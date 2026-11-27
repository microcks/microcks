import { readFileSync, readdirSync, statSync, existsSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { isWorkflowArtifact, parseArtifact } from './parser';
import {
  BackendError,
  createBackendJob,
  deleteBackendJob,
  deleteBackendService,
  downloadArtifact,
  fetchBackendJobs,
  fetchBackendServices,
  fetchBackendJob,
  findBackendSecret,
  secretHeaders,
  saveBackendJob,
  setBackendJobActive,
  startBackendJob,
  type BackendJob,
  toUiService,
  updateServiceLabels,
  uploadArtifact
} from './backend';
import { targetFor, validateTestForm, type CreateTestRequest } from '#lib/testing.js';
import type {
  DiffLine,
  Operation,
  ParsedArtifact,
  Preview,
  Protocol,
  ScanSchedule,
  Service,
  Source,
  SourceOrigin
} from '#lib/types.js';

const SAMPLES_DIR = process.env.MICROCKS_SAMPLES_DIR ?? resolve(process.cwd(), '../../samples');
const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

const services = new Map<string, Service>();
const inferredVersion = new Set<string>();
const sources: Source[] = [];
const contents = new Map<string, string>();
const deleted = new Map<string, { source: Source; services: Service[] }>();
let seq = 0;

function mockUrl(name: string, version: string, protocol: Protocol): string {
  const n = encodeURIComponent(name).replace(/%20/g, '+');
  switch (protocol) {
    case 'REST':
      return `/rest/${n}/${version}`;
    case 'SOAP':
      return `/soap/${n}/${version}`;
    case 'GRAPHQL':
      return `/graphql/${n}/${version}`;
    case 'gRPC':
      return `grpc://localhost:9090 (${name})`;
    case 'EVENT':
      return `${name.replace(/\s+/g, '')}-${version}-<channel>`;
    default:
      return `/dynarest/${n}/${version}`;
  }
}

function mergeOps(into: Operation[], from: Operation[]): Operation[] {
  const seen = new Set(into.map((o) => o.name));
  return [...into, ...from.filter((o) => o.method !== 'RULES' && !seen.has(o.name))];
}

function fallbackName(a: ParsedArtifact): string {
  return basename(a.fileName).replace(/\.[^.]+$/, '');
}

/** Creates or merges the service described by an artifact. Secondary artifacts enrich a primary one. */
function applyArtifact(a: ParsedArtifact, sourceId: string, at: string): Service | undefined {
  if (a.kind === 'unknown') return undefined;
  const name = a.name ?? fallbackName(a);
  let version = a.version;
  let svc = [...services.values()].find(
    (s) => s.name === name && (version === undefined ? true : s.version === version || inferredVersion.has(s.id))
  );
  if (svc && version && svc.version !== version && inferredVersion.has(svc.id)) {
    services.delete(svc.id);
    inferredVersion.delete(svc.id);
    const oldId = svc.id;
    svc = { ...svc, id: `${slug(name)}-${slug(version)}`, version, mockUrl: mockUrl(name, version, svc.protocol) };
    services.set(svc.id, svc);
    for (const src of sources) src.serviceIds = src.serviceIds.map((x) => (x === oldId ? svc!.id : x));
  }
  if (!svc) {
    if (a.role === 'SECONDARY' && a.kind === 'metadata') return undefined;
    version ??= '1.0';
    svc = {
      id: `${slug(name)}-${slug(version)}`,
      name,
      version,
      protocol: a.protocol ?? 'REST',
      format: a.format,
      operations: [],
      artifacts: [],
      labels: {},
      sourceIds: [],
      createdAt: at,
      updatedAt: at,
      mockUrl: mockUrl(name, version, a.protocol ?? 'REST')
    };
    if (a.version === undefined) inferredVersion.add(svc.id);
    services.set(svc.id, svc);
  } else if (a.role === 'PRIMARY') {
    svc.format = a.format;
    if (a.protocol) svc.protocol = a.protocol;
  }
  if (a.kind !== 'metadata' && !svc.artifacts.some((x) => x.kind === a.kind)) svc.artifacts.push({ kind: a.kind, format: a.format });
  svc.operations = mergeOps(svc.operations, a.operations);
  svc.labels = { ...svc.labels, ...a.labels };
  if (!svc.sourceIds.includes(sourceId)) svc.sourceIds.push(sourceId);
  svc.updatedAt = at;
  return svc;
}

function lineDiff(before: string, after: string): DiffLine[] {
  const a = before.split('\n');
  const b = after.split('\n');
  const m = a.length;
  const n = b.length;
  if (m * n > 4_000_000) return [{ kind: 'ctx', text: 'Contract too large to diff inline.' }];
  const dp = Array.from({ length: m + 1 }, () => new Uint32Array(n + 1));
  for (let i = m - 1; i >= 0; i--)
    for (let j = n - 1; j >= 0; j--) dp[i][j] = a[i] === b[j] ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
  const out: DiffLine[] = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (a[i] === b[j]) out.push({ kind: 'ctx', text: a[i++] }), j++;
    else if (dp[i + 1][j] >= dp[i][j + 1]) out.push({ kind: 'del', text: a[i++] });
    else out.push({ kind: 'add', text: b[j++] });
  }
  while (i < m) out.push({ kind: 'del', text: a[i++] });
  while (j < n) out.push({ kind: 'add', text: b[j++] });
  // Keep only changes with 2 lines of context.
  const keep = new Set<number>();
  out.forEach((l, k) => {
    if (l.kind !== 'ctx') for (let d = -2; d <= 2; d++) keep.add(k + d);
  });
  return out.filter((_, k) => keep.has(k));
}

/** Samples shipped in v2/samples are served locally; any other URL is fetched for real. */
export async function fetchArtifact(url: string, secret?: string, token?: string): Promise<{ fileName: string; content: string }> {
  const fileName = decodeURIComponent(new URL(url).pathname.split('/').pop() ?? 'artifact');
  const local = resolve(SAMPLES_DIR, fileName);
  if (/\/samples\/[^/]+$/.test(new URL(url).pathname) && existsSync(local)) return { fileName, content: readFileSync(local, 'utf8') };
  const headers = secret ? await secretHeaders(secret, token) : undefined;
  const res = await fetch(url, { headers, signal: AbortSignal.timeout(8000) });
  if (!res.ok) throw new Error(`HTTP ${res.status} while fetching ${url}`);
  const content = await res.text();
  if (content.length > 5_000_000) throw new Error('Artifact larger than 5 MB.');
  return { fileName, content };
}

export function analyze(origin: SourceOrigin, fileName: string, content: string, url = ''): Preview {
  const artifact = parseArtifact(fileName, content);
  const name = artifact.name ?? fallbackName(artifact);
  const version = artifact.version ?? '1.0';
  const exists = [...services.values()].some((s) => s.name === name && s.version === version);
  return {
    artifact,
    service: { name, version, protocol: artifact.protocol ?? 'REST', exists },
    content,
    origin,
    url
  };
}

export interface ImportRequest {
  origin: SourceOrigin;
  fileName: string;
  /** Display name of the source, defaults to the file name. */
  name?: string;
  content: string;
  url?: string;
  role?: 'PRIMARY' | 'SECONDARY';
  secret?: string;
  tlsValidation?: boolean;
}

function register(req: ImportRequest, at: string, active = true): { source: Source; service?: Service } {
  const artifact = parseArtifact(req.fileName, req.content);
  const id = `src-${++seq}`;
  const svc = applyArtifact(artifact, id, at);
  const source: Source = {
    id,
    name: req.name?.trim() || req.fileName,
    origin: req.origin,
    url: req.url ?? '',
    role: req.role ?? artifact.role,
    artifact: artifact.format,
    secret: req.secret,
    tlsValidation: req.tlsValidation ?? true,
    labels: svc?.labels ?? artifact.labels,
    active,
    createdAt: at,
    lastImportAt: at,
    warning: artifact.warnings[0],
    error: svc ? undefined : artifact.warnings[0] ?? 'Artifact not recognised.',
    serviceIds: svc ? [svc.id] : [],
    timeline: [
      {
        at,
        kind: svc ? 'import' : 'failure',
        summary: svc ? `Initial import: ${artifact.operations.length} operations` : 'Import failed: artifact not recognised'
      }
    ]
  };
  contents.set(id, req.content);
  sources.unshift(source);
  return { source, service: svc };
}

/** Ids of the Sources whose source of truth is a backend ImportJob (the id is the job id). */
const jobSourceIds = new Set<string>();

const asIso = (v: number | string | undefined) => (v === undefined ? undefined : new Date(v).toISOString());

/** Maps an ImportJob to a Source, keeping the in-memory timeline and diff the backend does not record (see IDEAS M2-029). */
function sourceOfJob(job: BackendJob, previous: Source | undefined, formats: Map<string, string>): Source {
  const serviceIds = (job.serviceRefs ?? []).map((r) => r.serviceId);
  const lastImportAt = asIso(job.lastImportDate);
  const timeline = previous?.timeline.length
    ? previous.timeline
    : lastImportAt
      ? [{ at: lastImportAt, kind: job.lastImportError ? ('failure' as const) : ('import' as const), summary: job.lastImportError ? `Import failed: ${job.lastImportError}` : `Imported ${serviceIds.length} service${serviceIds.length === 1 ? '' : 's'}` }]
      : [];
  return {
    id: job.id,
    name: job.name,
    origin: (job.metadata?.annotations?.origin as SourceOrigin | undefined) ?? 'url',
    url: job.repositoryUrl,
    role: job.mainArtifact ? 'PRIMARY' : 'SECONDARY',
    artifact: previous?.artifact ?? formats.get(serviceIds[0] ?? '') ?? '',
    secret: job.secretRef?.name,
    tlsValidation: !job.repositoryDisableSSLValidation,
    labels: job.metadata?.labels ?? {},
    active: job.active,
    createdAt: asIso(job.createdDate) ?? lastImportAt ?? new Date().toISOString(),
    lastImportAt,
    error: job.lastImportError || undefined,
    warning: previous?.warning,
    serviceIds,
    timeline,
    diff: previous?.diff
  };
}

function syncJobSources(jobs: BackendJob[]) {
  const formats = new Map([...services.values()].map((s) => [s.id, s.format]));
  const known = new Map(sources.map((s) => [s.id, s]));
  const fresh = jobs.map((j) => sourceOfJob(j, known.get(j.id), formats));
  const freshIds = new Set(fresh.map((s) => s.id));
  for (let i = sources.length - 1; i >= 0; i--) if (jobSourceIds.has(sources[i].id) && !freshIds.has(sources[i].id)) sources.splice(i, 1);
  for (const src of fresh) {
    const at = sources.findIndex((s) => s.id === src.id);
    if (at >= 0) sources[at] = src;
    else sources.push(src);
  }
  sources.sort((a, b) => (b.lastImportAt ?? b.createdAt).localeCompare(a.lastImportAt ?? a.createdAt));
  jobSourceIds.clear();
  for (const id of freshIds) jobSourceIds.add(id);
}

/** Ids of the Services whose source of truth is the Microcks backend (the others are local demo or snapshot data). */
const backendIds = new Set<string>();
const REFRESH_TTL_MS = 2000;
let lastRefresh = 0;

/**
 * Re-reads the Services from the backend into the local cache read synchronously by the catalog, testing and
 * observation views. Failures keep the previous cache. The cache is shared by all users: Services hidden from a user
 * by tenancy are not filtered per user yet.
 */
export const isBackendService = (id: string) => backendIds.has(id);

export async function refreshServices(token?: string, force = false): Promise<void> {
  if (!force && Date.now() - lastRefresh < REFRESH_TTL_MS) return;
  lastRefresh = Date.now();
  let remote;
  let jobs: BackendJob[];
  try {
    [remote, jobs] = await Promise.all([fetchBackendServices(token), fetchBackendJobs(token)]);
  } catch {
    return;
  }
  const links = new Map<string, string[]>();
  syncJobSources(jobs);
  for (const src of sources) for (const id of src.serviceIds) links.set(id, [...(links.get(id) ?? []), src.id]);
  const fresh = new Set<string>();
  for (const r of remote) {
    const svc = toUiService(r, mockUrl);
    svc.sourceIds = links.get(svc.id) ?? [];
    services.set(svc.id, svc);
    fresh.add(svc.id);
  }
  for (const id of backendIds) if (!fresh.has(id)) services.delete(id);
  backendIds.clear();
  for (const id of fresh) backendIds.add(id);
}

/** Saves the labels of a service on the backend and refreshes the cache. */
export async function setServiceLabels(id: string, labels: Record<string, string>, token?: string): Promise<Service | undefined> {
  if (!backendIds.has(id)) return undefined;
  await updateServiceLabels(id, labels, token);
  await refreshServices(token, true);
  return services.get(id);
}

/** What an Arazzo document or ArazzoSources overlay import produced: `name` is the title (or overlay name). */
export interface WorkflowImport {
  kind: 'arazzo' | 'arazzo-sources';
  name: string;
  version: string;
}

/**
 * Imports an Arazzo document or ArazzoSources overlay once, uploaded or downloaded from its URL (importers of Arazzo
 * documents come with a later story). The backend answers `<title>:<version>`.
 */
async function importWorkflowArtifact(req: ImportRequest, kind: WorkflowImport['kind'], token?: string): Promise<{ workflows: WorkflowImport }> {
  const mainArtifact = kind === 'arazzo';
  let outcome: string;
  try {
    outcome = req.origin !== 'upload' && req.url
      ? await downloadArtifact(req.url, mainArtifact, req.secret, token)
      : await uploadArtifact(req.fileName, req.content, mainArtifact, token);
  } catch (e) {
    if (e instanceof BackendError) throw new BackendError(e.message, e.status === 503 ? 503 : 422);
    throw e;
  }
  const at = outcome.lastIndexOf(':');
  if (!outcome || at < 0) throw new BackendError('The backend did not import this Arazzo artifact.', 422);
  return { workflows: { kind, name: outcome.slice(0, at), version: outcome.slice(at + 1) } };
}

/** Imports an artifact through the backend, then records the Source that carries it. */
export async function importViaBackend(
  req: ImportRequest,
  token?: string
): Promise<{ source?: Source; service?: Service; workflows?: WorkflowImport }> {
  const artifact = parseArtifact(req.fileName, req.content);
  if (isWorkflowArtifact(artifact)) return importWorkflowArtifact(req, artifact.kind as WorkflowImport['kind'], token);
  const role = req.role ?? artifact.role;
  // A URL or Git import is an importer: the backend keeps it as an ImportJob. A one-off upload only creates Services.
  if (req.origin !== 'upload' && req.url) return createJobSource(req, role, token);
  let outcome = '';
  let failure: string | undefined;
  try {
    outcome = await uploadArtifact(req.fileName, req.content, role === 'PRIMARY', token);
  } catch (e) {
    if (e instanceof BackendError && e.status === 503) throw e;
    failure = e instanceof Error ? e.message : String(e);
  }
  await refreshServices(token, true);
  const [name, version] = outcome.split(':');
  const svc = outcome ? [...services.values()].find((x) => x.name === name && x.version === version) : undefined;
  if (!svc) throw new BackendError(failure ?? 'The backend did not create any Service from this artifact.', 422);
  return { service: svc };
}

/** Records a URL/Git import as a backend ImportJob, runs it once and answers the resulting Source. */
async function createJobSource(req: ImportRequest, role: 'PRIMARY' | 'SECONDARY', token?: string): Promise<{ source?: Source; service?: Service }> {
  const secretRef = req.secret ? await findBackendSecret(req.secret, token) : undefined;
  if (req.secret && !secretRef) throw new BackendError(`Secret "${req.secret}" does not exist.`, 422);
  const created = await createBackendJob(
    {
      name: req.name?.trim() || req.fileName,
      repositoryUrl: req.url,
      mainArtifact: role === 'PRIMARY',
      repositoryDisableSSLValidation: req.tlsValidation === false,
      active: true,
      ...(secretRef ? { secretRef } : {})
    },
    token
  );
  await saveBackendJob({ ...created, metadata: { ...created.metadata, annotations: { origin: req.origin } } }, token);
  await startBackendJob(created.id, token);
  contents.set(created.id, req.content);
  await refreshServices(token, true);
  const source = sources.find((x) => x.id === created.id);
  const service = source ? services.get(source.serviceIds[0] ?? '') : undefined;
  return { source, service };
}

export function importSource(req: ImportRequest) {
  return register(req, new Date().toISOString());
}

export function createDirectApi(input: {
  name: string;
  version: string;
  kind: 'rest' | 'event';
  resource: string;
  payload: string;
}): { source: Source; service: Service } {
  const at = new Date().toISOString();
  const id = `src-${++seq}`;
  const svc: Service = {
    id: `${slug(input.name)}-${slug(input.version)}`,
    name: input.name,
    version: input.version,
    protocol: 'DIRECT',
    format: input.kind === 'rest' ? 'Direct REST' : 'Direct event',
    operations: [{ name: `${input.kind === 'rest' ? 'GET' : 'RECEIVE'} ${input.resource}`, method: input.kind === 'rest' ? 'GET' : 'RECEIVE', resource: input.resource }],
    artifacts: [{ kind: 'direct', format: input.kind === 'rest' ? 'Direct REST' : 'Direct event' }],
    labels: {},
    sourceIds: [id],
    createdAt: at,
    updatedAt: at,
    mockUrl: `/dynarest/${encodeURIComponent(input.name).replace(/%20/g, '+')}/${input.version}/${input.resource.replace(/^\//, '')}`
  };
  services.set(svc.id, svc);
  const source: Source = {
    id,
    name: input.name,
    origin: 'upload',
    url: '',
    role: 'PRIMARY',
    artifact: svc.format,
    tlsValidation: true,
    labels: {},
    active: true,
    createdAt: at,
    lastImportAt: at,
    serviceIds: [svc.id],
    timeline: [{ at, kind: 'import', summary: 'Direct API created' }]
  };
  contents.set(id, input.payload);
  sources.unshift(source);
  return { source, service: svc };
}

export async function importNow(id: string, token?: string): Promise<Source> {
  const source = sources.find((s) => s.id === id);
  if (!source) throw new Error('Unknown source');
  const at = new Date().toISOString();
  if (source.origin === 'upload' || !source.url) throw new Error('Uploaded sources are re-imported by uploading the file again.');
  try {
    // The backend does the real import; this local copy only feeds the diff, so a failure here is not fatal for jobs.
    const fetched = await fetchArtifact(source.url, source.secret, token).catch((e) => {
      if (!jobSourceIds.has(id)) throw e;
      return undefined;
    });
    const content = fetched?.content ?? contents.get(id) ?? '';
    const before = contents.get(id) ?? '';
    const lines = fetched ? lineDiff(before, content) : [];
    const changed = lines.some((l) => l.kind !== 'ctx');
    if (jobSourceIds.has(id)) {
      const job = await startBackendJob(id, token);
      if (job.lastImportError) throw new Error(job.lastImportError);
    } else {
      await downloadArtifact(source.url, source.role === 'PRIMARY', source.secret, token);
    }
    await refreshServices(token, true);
    if (changed) {
      source.diff = { revision: at.slice(0, 16).replace('T', ' '), lines };
      contents.set(id, content);
    }
    source.timeline.unshift({
      at,
      kind: 'reimport',
      summary: changed ? 'Re-imported: contract changed' : 'Re-imported: no change',
      revision: changed ? source.diff!.revision : undefined
    });
    source.lastImportAt = at;
    source.error = undefined;
  } catch (e) {
    source.error = e instanceof Error ? e.message : String(e);
    source.timeline.unshift({ at, kind: 'failure', summary: `Import failed: ${source.error}` });
  }
  await refreshServices(token, true);
  return sources.find((x) => x.id === id) ?? source;
}

export async function setActive(id: string, active: boolean, token?: string): Promise<Source | undefined> {
  const s = sources.find((x) => x.id === id);
  if (!s) return undefined;
  if (jobSourceIds.has(id)) await setBackendJobActive(id, active, token);
  s.active = active;
  return s;
}

export interface SourcePatch {
  name?: string;
  url?: string;
  /** Name of the Secret; an empty string removes it. */
  secret?: string;
  tlsValidation?: boolean;
  role?: 'PRIMARY' | 'SECONDARY';
  labels?: Record<string, string>;
}

/** Edits the ImportJob behind a Source. Uploads have no job and cannot be edited. */
export async function updateSource(id: string, patch: SourcePatch, token?: string): Promise<Source | undefined> {
  if (!sources.some((s) => s.id === id)) return undefined;
  if (!jobSourceIds.has(id)) throw new BackendError('An uploaded file has no importer to edit: upload it again.', 409);
  const job = await fetchBackendJob(id, token);
  if (!job) throw new BackendError('The importer no longer exists.', 404);
  const next: BackendJob = { ...job };
  if (patch.name !== undefined) next.name = patch.name.trim() || job.name;
  if (patch.url !== undefined) next.repositoryUrl = patch.url.trim() || job.repositoryUrl;
  if (patch.tlsValidation !== undefined) next.repositoryDisableSSLValidation = !patch.tlsValidation;
  if (patch.role !== undefined) next.mainArtifact = patch.role === 'PRIMARY';
  if (patch.secret !== undefined) {
    if (patch.secret === '') delete next.secretRef;
    else {
      const ref = await findBackendSecret(patch.secret, token);
      if (!ref) throw new BackendError(`Secret "${patch.secret}" does not exist.`, 422);
      next.secretRef = ref;
    }
  }
  if (patch.labels !== undefined) next.metadata = { ...job.metadata, labels: patch.labels };
  await saveBackendJob(next, token);
  await refreshServices(token, true);
  return sources.find((s) => s.id === id);
}

/** Deletes the ImportJob behind a removed Source. */
export async function deleteSourceJob(id: string, token?: string): Promise<void> {
  if (!jobSourceIds.has(id)) return;
  await deleteBackendJob(id, token);
  jobSourceIds.delete(id);
}

/** Removes a Source and returns the Services that went away with it (to delete from the backend), or undefined. */
export function removeSource(id: string): Service[] | undefined {
  const idx = sources.findIndex((s) => s.id === id);
  if (idx < 0) return undefined;
  const [source] = sources.splice(idx, 1);
  const removed: Service[] = [];
  for (const svc of [...services.values()]) {
    if (!svc.sourceIds.includes(id)) continue;
    svc.sourceIds = svc.sourceIds.filter((x) => x !== id);
    if (svc.sourceIds.length === 0) {
      services.delete(svc.id);
      removed.push(svc);
    }
  }
  deleted.set(id, { source, services: removed });
  return removed;
}

/** Deletes from the backend the Services removed with a Source, and drops them from the cache. */
export async function deleteRemovedServices(removed: Service[], token?: string): Promise<void> {
  for (const svc of removed) {
    if (backendIds.has(svc.id)) {
      await deleteBackendService(svc.id, token);
      backendIds.delete(svc.id);
    }
  }
}

export async function undoRemove(id: string, token?: string): Promise<boolean> {
  const entry = deleted.get(id);
  if (!entry) return false;
  deleted.delete(id);
  const content = contents.get(id);
  if (jobSourceIds.has(id) && entry.source.url) {
    // The ImportJob was deleted with the Source: create it again (it gets a new id) and import once more.
    const restored = await createJobSource(
      { origin: entry.source.origin, fileName: entry.source.name, name: entry.source.name, content: content ?? '', url: entry.source.url, role: entry.source.role, secret: entry.source.secret, tlsValidation: entry.source.tlsValidation },
      entry.source.role,
      token
    );
    return !!restored.source;
  }
  sources.unshift(entry.source);
  if (content !== undefined && entry.services.some((x) => x.protocol !== 'DIRECT')) {
    // Backend Services are restored by importing the stored artifact again.
    await uploadArtifact(entry.source.name, content, entry.source.role === 'PRIMARY', token);
    await refreshServices(token, true);
    const relinked = [...services.values()].filter((x) => entry.services.some((o) => o.name === x.name && o.version === x.version));
    entry.source.serviceIds = relinked.map((x) => x.id);
    for (const svc of relinked) if (!svc.sourceIds.includes(id)) svc.sourceIds.push(id);
    return true;
  }
  for (const svc of entry.services) services.set(svc.id, svc);
  for (const sid of entry.source.serviceIds) {
    const svc = services.get(sid);
    if (svc && !svc.sourceIds.includes(id)) svc.sourceIds.push(id);
  }
  return true;
}

export const listServices = () => [...services.values()].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
export const getService = (id: string) => services.get(id);
export const listSources = () => sources;
export const getSource = (id: string) => sources.find((s) => s.id === id);
/** Raw artifact content of a source, for read-only extraction of examples. */
export const getSourceContent = (id: string) => contents.get(id);

export function schedule(): ScanSchedule {
  const now = new Date();
  const last = new Date(now);
  last.setMinutes(0, 0, 0);
  const next = new Date(last.getTime() + 3_600_000);
  return {
    cron: '0 0 * * * *',
    description: 'every hour, global to all scheduled sources',
    envVar: 'SERVICES_UPDATE_INTERVAL',
    lastScanAt: last.toISOString(),
    nextScanAt: next.toISOString(),
    intervalMinutes: 60
  };
}

const GH = 'https://raw.githubusercontent.com/microcks/microcks/master/samples/';

interface Seed {
  file: string;
  origin: SourceOrigin;
  role?: 'PRIMARY' | 'SECONDARY';
  active?: boolean;
}

// Demo configuration: which sample file is registered as which kind of source. Contents are real.
const SEEDS: Seed[] = [
  { file: 'BeerCatalogAPI-swagger.json', origin: 'upload' },
  { file: 'BeerCatalogAPI-collection.json', origin: 'upload' },
  { file: 'APIPastry-openapi.yaml', origin: 'url' },
  { file: 'api-pastries-0.0.1.har', origin: 'upload' },
  { file: 'PetstoreAPI-swagger.json', origin: 'git' },
  { file: 'PetstoreAPI-collection.json', origin: 'git' },
  { file: 'Petstore-webhooks-openapi.yaml', origin: 'url', active: false },
  { file: 'UserSignedUpAPI-asyncapi.yml', origin: 'url' },
  { file: 'UserRegistration-callbacks-openapi.yaml', origin: 'url' },
  { file: 'films.graphql', origin: 'git' },
  { file: 'films-postman.json', origin: 'git' },
  { file: 'films-metadata.yml', origin: 'git' },
  { file: 'hello-v1.proto', origin: 'git' },
  { file: 'HelloService.postman.json', origin: 'git' },
  { file: 'HelloService.metadata.yml', origin: 'git' },
  { file: 'HelloService.wsdl', origin: 'url' },
  { file: 'HelloService-soapui-project.xml', origin: 'url' },
  { file: 'HelloAPI-swagger.json', origin: 'upload' }
];

/** Registers the demo Services and Sources. Kept for demos and future tests, it only runs when explicitly enabled. */
export function seed() {
  if (!existsSync(SAMPLES_DIR)) return;
  for (const s of SEEDS) {
    const path = resolve(SAMPLES_DIR, s.file);
    if (!existsSync(path)) continue;
    const at = statSync(path).mtime.toISOString();
    const content = readFileSync(path, 'utf8');
    const { source } = register(
      { origin: s.origin, fileName: s.file, content, url: s.origin === 'upload' ? '' : GH + s.file },
      at,
      s.active ?? true
    );
    if (s.origin === 'git') source.url = GH + s.file;
  }
  // Presentation order: newest import first.
  sources.sort((a, b) => (b.lastImportAt ?? '').localeCompare(a.lastImportAt ?? ''));
}

export function sampleFiles(): string[] {
  return existsSync(SAMPLES_DIR) ? readdirSync(SAMPLES_DIR).sort() : [];
}

export function readSample(file: string): string | undefined {
  const p = resolve(SAMPLES_DIR, basename(file));
  return existsSync(p) ? readFileSync(p, 'utf8') : undefined;
}

/** Validates a test configuration against the service's target. Returns the first error, if any. */
export function validateTestRequest(req: CreateTestRequest): string | undefined {
  const svc = services.get(req.serviceId);
  if (!svc) return `Service "${req.serviceId}" does not exist.`;
  const target = targetFor(svc);
  const known = new Set(target.operations.map((o) => o.id));
  const errors = validateTestForm(target, {
    endpoint: req.endpoint,
    runner: req.runner,
    timeout: String(req.timeoutMs),
    selectedOperations: req.operations.filter((o) => known.has(o)),
    oauth: false,
    clientSecret: ''
  });
  return Object.values(errors)[0];
}

// The UI starts empty, like a first use. Set MICROCKS_UI_DEMO_SEED=true to load the demo catalog.
if (process.env.MICROCKS_UI_DEMO_SEED === 'true') seed();
