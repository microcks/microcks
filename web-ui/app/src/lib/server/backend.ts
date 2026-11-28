import { MICROCKS_API_URL } from './auth.js';
import type { Operation, Protocol, Service } from '#lib/types.js';

/** Raw shape of the Microcks backend `Service`, limited to what the UI consumes. */
export interface BackendService {
  id: string;
  name: string;
  version: string;
  type: string;
  sourceArtifact?: string;
  metadata?: {
    createdOn?: string | number;
    lastUpdate?: string | number;
    labels?: Record<string, string>;
    annotations?: Record<string, string>;
  };
  operations?: BackendOperation[];
}

export interface BackendOperation {
  name: string;
  method?: string;
  dispatcher?: string;
  dispatcherRules?: string;
  defaultDelay?: number;
  defaultDelayStrategy?: string;
}

/** An exchange of the backend `messagesMap`: request/response pair or event, depending on the service type. */
export interface BackendExchange {
  type?: string;
  request?: { name?: string; content?: string; queryParameters?: { name: string; value: string }[] };
  response?: { name?: string; status?: string; mediaType?: string; content?: string; dispatchCriteria?: string };
  eventMessage?: { name?: string; content?: string; mediaType?: string; dispatchCriteria?: string };
}

export interface BackendServiceView {
  service: BackendService;
  messagesMap: Record<string, BackendExchange[]>;
}

export class BackendError extends Error {
  constructor(
    message: string,
    readonly status: number
  ) {
    super(message);
  }
}

const PROTOCOLS: Record<string, [Protocol, string]> = {
  REST: ['REST', 'REST'],
  GENERIC_REST: ['DIRECT', 'Direct REST'],
  SOAP_HTTP: ['SOAP', 'SOAP'],
  EVENT: ['EVENT', 'Event'],
  GENERIC_EVENT: ['DIRECT', 'Direct event'],
  GRPC: ['gRPC', 'gRPC'],
  GRAPHQL: ['GRAPHQL', 'GraphQL']
};

/** The backend does not expose the imported artifacts: the main contract kind is inferred from the service type. */
const CONTRACT_KINDS: Record<string, string> = {
  REST: 'openapi',
  SOAP_HTTP: 'wsdl',
  EVENT: 'asyncapi',
  GRPC: 'protobuf',
  GRAPHQL: 'graphql'
};

async function call(path: string, token: string | undefined, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (token) headers.set('authorization', `Bearer ${token}`);
  let res: Response;
  try {
    res = await fetch(`${MICROCKS_API_URL}${path}`, { ...init, headers, signal: AbortSignal.timeout(30_000) });
  } catch {
    throw new BackendError('The Microcks backend is unreachable.', 503);
  }
  if (!res.ok) {
    const text = (await res.text()).trim();
    throw new BackendError(text && text.length < 500 ? text : `Backend answered ${res.status}`, res.status);
  }
  return res;
}

const iso = (v: string | number | undefined): string => {
  const d = v === undefined ? new Date() : new Date(v);
  return Number.isNaN(d.getTime()) ? new Date().toISOString() : d.toISOString();
};

/** Maps a backend Service to the UI model. `mockUrl` is computed by the caller as it depends on UI conventions. */
/** UI protocol of a backend service type. */
export const protocolOf = (type: string): Protocol => (PROTOCOLS[type] ?? (['REST', type] as [Protocol, string]))[0];

export function toUiService(s: BackendService, mockUrl: (name: string, version: string, p: Protocol) => string): Service {
  const [protocol, format] = PROTOCOLS[s.type] ?? (['REST', s.type] as [Protocol, string]);
  const operations: Operation[] = (s.operations ?? []).map((o) => {
    const method = o.method ?? '';
    const resource = o.name.startsWith(`${method} `) ? o.name.slice(method.length + 1) : undefined;
    return { name: o.name, method, resource };
  });
  return {
    id: s.id,
    name: s.name,
    version: s.version,
    protocol,
    format,
    operations,
    artifacts: [{ kind: CONTRACT_KINDS[s.type] ?? 'primary', format }],
    labels: s.metadata?.labels ?? {},
    sourceIds: [],
    createdAt: iso(s.metadata?.createdOn),
    updatedAt: iso(s.metadata?.lastUpdate),
    mockUrl: mockUrl(s.name, s.version, protocol)
  };
}

export async function fetchBackendServices(token?: string): Promise<BackendService[]> {
  const res = await call('/api/services?page=0&size=1000', token, { headers: { accept: 'application/json' } });
  return (await res.json()) as BackendService[];
}

/** Uploads an artifact; the answer is `name:version` of the first service it produced. */
export async function uploadArtifact(
  fileName: string,
  content: string,
  mainArtifact: boolean,
  token?: string
): Promise<string> {
  const form = new FormData();
  form.append('file', new Blob([content]), fileName);
  const res = await call(`/api/artifact/upload?mainArtifact=${mainArtifact}`, token, { method: 'POST', body: form });
  return (await res.text()).trim();
}

/** Asks the backend to download and import an artifact from a URL. */
export async function downloadArtifact(
  url: string,
  mainArtifact: boolean,
  secretName?: string,
  token?: string
): Promise<string> {
  const q = new URLSearchParams({ url, mainArtifact: String(mainArtifact) });
  if (secretName) q.set('secretName', secretName);
  const res = await call(`/api/artifact/download?${q}`, token, { method: 'POST' });
  const text = (await res.text()).trim();
  try {
    return (JSON.parse(text) as { name: string }).name;
  } catch {
    return text;
  }
}

export async function deleteBackendService(id: string, token?: string): Promise<void> {
  await call(`/api/services/${encodeURIComponent(id)}`, token, { method: 'DELETE' });
}

/** Fetches a Service with its operations and the examples (exchanges) stored for each of them. */
export async function fetchBackendServiceView(id: string, token?: string): Promise<BackendServiceView | undefined> {
  try {
    const res = await call(`/api/services/${encodeURIComponent(id)}?messages=true`, token, {
      headers: { accept: 'application/json' }
    });
    return (await res.json()) as BackendServiceView;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

/** Backend `DailyStatistic`: invocation counters of a service (or of all services) for a day (yyyyMMdd, server time). */
export interface BackendDailyStatistic {
  day?: string;
  serviceName?: string;
  serviceVersion?: string;
  dailyCount?: number;
  hourlyCount?: Record<string, number>;
  minuteCount?: Record<string, number>;
}

async function getJson<T>(path: string, token?: string): Promise<T | undefined> {
  const res = await call(path, token, { headers: { accept: 'application/json' } });
  const text = (await res.text()).trim();
  return text ? (JSON.parse(text) as T) : undefined;
}

export const fetchInvocationsGlobal = (day: string, token?: string) =>
  getJson<BackendDailyStatistic>(`/api/metrics/invocations/global?day=${day}`, token);

export const fetchInvocationsTop = async (day: string, limit: number, token?: string) =>
  (await getJson<BackendDailyStatistic[]>(`/api/metrics/invocations/top?day=${day}&limit=${limit}`, token)) ?? [];

/** Day (yyyyMMdd) -> total count, over the latest `limit` days. */
export const fetchInvocationsLatest = async (limit: number, token?: string) =>
  (await getJson<Record<string, number>>(`/api/metrics/invocations/global/latest?limit=${limit}`, token)) ?? {};

export const fetchInvocationsForService = (name: string, version: string, day: string, token?: string) =>
  getJson<BackendDailyStatistic>(
    `/api/metrics/invocations/${encodeURIComponent(name)}/${encodeURIComponent(version)}?day=${day}`,
    token
  );

/** Replaces the labels of a service. The backend PUT replaces annotations too, so the existing ones are sent back. */
export async function updateServiceLabels(id: string, labels: Record<string, string>, token?: string): Promise<void> {
  const view = await fetchBackendServiceView(id, token);
  if (!view) throw new BackendError(`Service ${id} not found`, 404);
  await call(`/api/services/${encodeURIComponent(id)}/metadata`, token, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ labels, annotations: view.service.metadata?.annotations ?? {} })
  });
}

// ── Tests (TestController) ──

export interface BackendTestStep {
  success: boolean;
  elapsedTime: number;
  requestName?: string;
  eventMessageName?: string;
  message?: string;
}

export interface BackendTestCase {
  success: boolean;
  elapsedTime: number;
  operationName: string;
  testStepResults?: BackendTestStep[];
}

export interface BackendTestResult {
  id: string;
  testNumber: number;
  testDate: number | string;
  testedEndpoint: string;
  serviceId: string;
  timeout: number;
  elapsedTime: number;
  success: boolean;
  inProgress: boolean;
  runnerType: string;
  runnerMessage?: string;
  testCaseResults?: BackendTestCase[];
}

export interface BackendTestRequest {
  serviceId: string;
  testEndpoint: string;
  runnerType: string;
  timeout: number;
  secretName?: string;
  filteredOperations?: string[];
  operationsHeaders?: Record<string, { name: string; values: string }[]>;
}

export async function launchBackendTest(req: BackendTestRequest, token?: string): Promise<BackendTestResult> {
  const res = await call('/api/tests', token, {
    method: 'POST',
    headers: { 'content-type': 'application/json', accept: 'application/json' },
    body: JSON.stringify(req)
  });
  return (await res.json()) as BackendTestResult;
}

export async function fetchBackendTest(id: string, token?: string): Promise<BackendTestResult | undefined> {
  try {
    const res = await call(`/api/tests/${encodeURIComponent(id)}`, token, { headers: { accept: 'application/json' } });
    return (await res.json()) as BackendTestResult;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

export async function fetchBackendTests(serviceId: string, page: number, size: number, token?: string): Promise<BackendTestResult[]> {
  const res = await call(`/api/tests/service/${encodeURIComponent(serviceId)}?page=${page}&size=${size}`, token, {
    headers: { accept: 'application/json' }
  });
  return (await res.json()) as BackendTestResult[];
}

export async function countBackendTests(serviceId: string, token?: string): Promise<number> {
  const res = await call(`/api/tests/service/${encodeURIComponent(serviceId)}/count`, token, { headers: { accept: 'application/json' } });
  return ((await res.json()) as { counter: number }).counter;
}

export interface BackendHeader {
  name: string;
  values?: string[];
}

export interface BackendTestMessage {
  request?: { name?: string; content?: string; headers?: BackendHeader[] };
  response?: { content?: string; status?: string; mediaType?: string; headers?: BackendHeader[] };
}

/** Exchanges recorded for a test case. `testCaseId` is the id used by the backend, with '/' written as '!'. */
export async function fetchBackendTestMessages(id: string, testCaseId: string, token?: string): Promise<BackendTestMessage[]> {
  const res = await call(`/api/tests/${encodeURIComponent(id)}/messages/${encodeURIComponent(testCaseId)}`, token, {
    headers: { accept: 'application/json' }
  });
  return (await res.json()) as BackendTestMessage[];
}

/** An ImportJob: the backend record of a repository (URL or Git file) that Microcks imports and keeps in sync. */
export interface BackendJob {
  id: string;
  name: string;
  repositoryUrl: string;
  mainArtifact: boolean;
  repositoryDisableSSLValidation?: boolean;
  frequency?: string;
  createdDate?: number | string;
  lastImportDate?: number | string;
  lastImportError?: string;
  active: boolean;
  metadata?: { labels?: Record<string, string>; annotations?: Record<string, string> };
  secretRef?: { secretId: string; name: string };
  serviceRefs?: { serviceId: string; name: string; version: string }[];
  /** The Arazzo document the job imported, with the source descriptions it could not link. */
  arazzoDocumentRef?: { documentId?: string | null; title: string; version: string; unlinkedSources?: string[] } | null;
}

const JSON_HEADERS = { 'content-type': 'application/json', accept: 'application/json' };

export async function fetchBackendJobs(token?: string): Promise<BackendJob[]> {
  const res = await call('/api/jobs?page=0&size=1000', token, { headers: { accept: 'application/json' } });
  return (await res.json()) as BackendJob[];
}

export async function fetchBackendJob(id: string, token?: string): Promise<BackendJob | undefined> {
  const res = await call(`/api/jobs/${encodeURIComponent(id)}`, token, { headers: { accept: 'application/json' } });
  const text = await res.text();
  return text ? (JSON.parse(text) as BackendJob) : undefined;
}

export async function createBackendJob(job: Partial<BackendJob>, token?: string): Promise<BackendJob> {
  const res = await call('/api/jobs', token, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(job) });
  return (await res.json()) as BackendJob;
}

/** Saves a whole job; the backend ignores annotations on creation, so they are written by a second call. */
export async function saveBackendJob(job: BackendJob, token?: string): Promise<BackendJob> {
  const res = await call(`/api/jobs/${encodeURIComponent(job.id)}`, token, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(job) });
  return (await res.json()) as BackendJob;
}

/** Runs the import now and answers the job with its refreshed service references and last error. */
export async function startBackendJob(id: string, token?: string): Promise<BackendJob> {
  const res = await call(`/api/jobs/${encodeURIComponent(id)}/start`, token, { method: 'PUT', headers: { accept: 'application/json' } });
  return (await res.json()) as BackendJob;
}

export async function setBackendJobActive(id: string, active: boolean, token?: string): Promise<void> {
  await call(`/api/jobs/${encodeURIComponent(id)}/${active ? 'activate' : 'stop'}`, token, { method: 'PUT' });
}

export async function deleteBackendJob(id: string, token?: string): Promise<void> {
  await call(`/api/jobs/${encodeURIComponent(id)}`, token, { method: 'DELETE' });
}

/** Resolves a secret by name to the reference an ImportJob carries. */
export async function findBackendSecret(name: string, token?: string): Promise<{ secretId: string; name: string } | undefined> {
  const res = await call(`/api/secrets/search?name=${encodeURIComponent(name)}`, token, { headers: { accept: 'application/json' } });
  const found = ((await res.json()) as { id: string; name: string }[]).find((s) => s.name === name);
  return found ? { secretId: found.id, name: found.name } : undefined;
}

/** A Secret as the backend stores it. Non-admin callers get the credential fields blanked. */
export interface BackendSecret {
  id?: string;
  name: string;
  description?: string;
  username?: string;
  password?: string;
  token?: string;
  tokenHeader?: string;
  caCertPem?: string;
}

export async function fetchBackendSecrets(token?: string): Promise<BackendSecret[]> {
  const res = await call('/api/secrets?page=0&size=1000', token, { headers: { accept: 'application/json' } });
  return (await res.json()) as BackendSecret[];
}

export async function fetchBackendSecret(id: string, token?: string): Promise<BackendSecret | undefined> {
  const res = await call(`/api/secrets/${encodeURIComponent(id)}`, token, { headers: { accept: 'application/json' } });
  const text = await res.text();
  return text ? (JSON.parse(text) as BackendSecret) : undefined;
}

export async function createBackendSecret(secret: BackendSecret, token?: string): Promise<BackendSecret> {
  const res = await call('/api/secrets', token, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(secret) });
  return (await res.json()) as BackendSecret;
}

/** Replaces the whole secret: send every field to keep. */
export async function saveBackendSecret(secret: BackendSecret & { id: string }, token?: string): Promise<BackendSecret> {
  const res = await call(`/api/secrets/${encodeURIComponent(secret.id)}`, token, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify(secret) });
  return (await res.json()) as BackendSecret;
}

export async function deleteBackendSecret(id: string, token?: string): Promise<void> {
  await call(`/api/secrets/${encodeURIComponent(id)}`, token, { method: 'DELETE' });
}

export interface BackendGroup {
  name: string;
  description?: string;
}

export interface BackendMembership {
  id: string;
  groupName: string;
  role: string;
  /** Null while the membership is pending: the user never signed in. */
  subject?: string | null;
  email?: string | null;
}

export interface BackendUser {
  id: string;
  username: string;
  name?: string | null;
  email?: string | null;
  emailVerified?: boolean;
  roles?: string[] | null;
  createdAt?: string | number | null;
  lastLoginAt?: string | number | null;
  disabled?: boolean;
}

/** Users created at first sign-in. Empty when authentication is disabled. */
export async function fetchBackendUsers(token?: string): Promise<BackendUser[]> {
  return (await getJson<BackendUser[]>('/api/users', token)) ?? [];
}

export async function setBackendUserDisabled(id: string, disabled: boolean, token?: string): Promise<void> {
  await call(`/api/users/${encodeURIComponent(id)}`, token, {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify({ disabled })
  });
}

export async function deleteBackendUser(id: string, token?: string): Promise<void> {
  await call(`/api/users/${encodeURIComponent(id)}`, token, { method: 'DELETE' });
}

export async function fetchBackendGroups(token?: string): Promise<BackendGroup[]> {
  return (await getJson<BackendGroup[]>('/api/groups', token)) ?? [];
}

export async function fetchBackendMembers(group: string, token?: string): Promise<BackendMembership[]> {
  return (await getJson<BackendMembership[]>(`/api/groups/${encodeURIComponent(group)}/members`, token)) ?? [];
}

export async function createBackendGroup(group: BackendGroup, token?: string): Promise<BackendGroup> {
  const res = await call('/api/groups', token, { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(group) });
  return (await res.json()) as BackendGroup;
}

export async function deleteBackendGroup(name: string, token?: string): Promise<void> {
  await call(`/api/groups/${encodeURIComponent(name)}`, token, { method: 'DELETE' });
}

export async function addBackendMember(
  group: string,
  member: { role: string; subject?: string; email?: string },
  token?: string
): Promise<BackendMembership> {
  const res = await call(`/api/groups/${encodeURIComponent(group)}/members`, token, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(member)
  });
  return (await res.json()) as BackendMembership;
}

export async function removeBackendMember(group: string, membershipId: string, token?: string): Promise<void> {
  await call(`/api/groups/${encodeURIComponent(group)}/members/${encodeURIComponent(membershipId)}`, token, { method: 'DELETE' });
}

/** Repository export (services, resources, requests, responses, event messages) as the raw backend JSON. */
export async function exportRepository(serviceIds: string[], token?: string): Promise<string> {
  const q = new URLSearchParams();
  for (const id of serviceIds) q.append('serviceIds', id);
  const res = await call(`/api/export?${q}`, token, { headers: { accept: 'application/json' } });
  return res.text();
}

/** Imports a repository export; existing entries with the same ids are replaced. */
export async function importRepository(json: string, token?: string): Promise<void> {
  const form = new FormData();
  form.append('file', new Blob([json], { type: 'application/json' }), 'microcks-repository.json');
  await call('/api/import', token, { method: 'POST', body: form });
}

/** Optional features reported by the backend: feature name → property → value (all strings). */
export async function fetchBackendFeatures(token?: string): Promise<Record<string, Record<string, string> | undefined>> {
  const res = await call('/api/features/config', token, { headers: { accept: 'application/json' } });
  return (await res.json()) as Record<string, Record<string, string>>;
}

export async function fetchBackendAuthConfig(): Promise<{ enabled: boolean; oidcEnabled: boolean }> {
  const res = await call('/api/auth/config', undefined, { headers: { accept: 'application/json' } });
  const b = await res.json();
  return { enabled: b.enabled === true, oidcEnabled: b.oidcEnabled === true };
}

/** Request headers the backend would send to reach a URL protected by the named Secret (basic or token). */
export async function secretHeaders(secretName: string, token?: string): Promise<Record<string, string>> {
  const secret = (await fetchBackendSecrets(token)).find((s) => s.name === secretName);
  const headers: Record<string, string> = {};
  if (!secret) return headers;
  if (secret.username && secret.password) headers.authorization = `Basic ${Buffer.from(`${secret.username}:${secret.password}`).toString('base64')}`;
  if (secret.token) {
    if (secret.tokenHeader?.trim()) headers[secret.tokenHeader.trim()] = secret.token;
    else headers.authorization = `Bearer ${secret.token}`;
  }
  return headers;
}

/* ---- Arazzo (`/api/arazzo`, ported from feature/arazzo S1–S4) ---- */

export interface BackendArazzoSourceLink {
  sourceName: string;
  apiName?: string | null;
  apiVersion?: string | null;
  linked: boolean;
}

export interface BackendArazzoDocumentSummary {
  id: string;
  title: string;
  version: string;
  arazzoVersion: string;
  fileName?: string | null;
  importDate?: number | string;
  sources: { link: BackendArazzoSourceLink; apiId?: string | null; apiType?: string | null }[];
  workflows: {
    status: { workflowId: string; runnable: boolean; reasons: string[] };
    description?: string | null;
    stepCount: number;
    lastResult: 'NEVER_RUN' | 'SUCCESS' | 'FAILURE' | string;
  }[];
}

export interface BackendArazzoStep {
  stepId: string;
  description?: string | null;
  operation: { sourceName: string; operationId: string; method?: string | null; path?: string | null; channel?: string | null; reason?: string | null };
  /** S28: only for a step that sends or waits for a message. */
  messaging?: { action: string; correlationId?: string | null; timeout?: number | null } | null;
  /** S30 */
  dependsOn?: string[] | null;
  prerequisites?: string[] | null;
  parameters: { name: string; in: string; value: string }[];
  requestBody?: { contentType?: string | null; payload?: string | null; replacements?: Record<string, string> | null } | null;
  successCriteria: { condition: string; context?: string | null; type?: string | null }[];
  outputs: Record<string, string>;
  onSuccess?: BackendArazzoAction[] | null;
  onFailure?: BackendArazzoAction[] | null;
}

export interface BackendArazzoAction {
  name: string;
  type: string;
  retryAfter?: number | null;
  retryLimit?: number | null;
  criteria?: { condition: string; context?: string | null; type?: string | null }[] | null;
}

export interface BackendArazzoWorkflowDetail {
  documentId: string;
  title: string;
  version: string;
  arazzoVersion: string;
  fileName?: string | null;
  importDate?: number | string;
  workflowIds: string[];
  sources: { link: BackendArazzoSourceLink; serviceId?: string | null; reason?: string | null; operations: number; usedOperations: number }[];
  workflowId: string;
  summary?: string | null;
  description?: string | null;
  inputs: { name: string; usedBy: { stepId: string; place: string; target: string }[]; type?: string | null; required?: boolean }[];
  steps: BackendArazzoStep[];
  outputs: Record<string, string>;
  dataPassings: { fromStepId: string; outputName: string; to: { stepId: string; place: string; target: string } }[];
  /** The examples recorded for this workflow by the ArazzoSources overlay. */
  examples?: BackendExample[] | null;
}

export async function fetchArazzoDocuments(token?: string): Promise<BackendArazzoDocumentSummary[]> {
  const res = await call('/api/arazzo/documents', token, { headers: { accept: 'application/json' } });
  return (await res.json()) as BackendArazzoDocumentSummary[];
}

export async function fetchArazzoWorkflow(docId: string, workflowId: string, token?: string): Promise<BackendArazzoWorkflowDetail | undefined> {
  try {
    const path = `/api/arazzo/documents/${encodeURIComponent(docId)}/workflows/${encodeURIComponent(workflowId)}`;
    const res = await call(path, token, { headers: { accept: 'application/json' } });
    return (await res.json()) as BackendArazzoWorkflowDetail;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

export async function fetchArazzoSource(docId: string, token?: string): Promise<string | undefined> {
  try {
    const res = await call(`/api/arazzo/documents/${encodeURIComponent(docId)}/source`, token);
    return await res.text();
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

/* ---- Arazzo import dialog and relink (`/api/arazzo/documents/analysis`, stories S14 and S19) ---- */

import type { ApiRef, ImportAnalysis } from '#lib/workflows/import-analysis.js';
import type { BackendExample } from '#lib/workflows/examples.js';

/** The links chosen per source description; absent or null = not linked, undefined `links` = the proposed ones. */
export type BackendLinks = Record<string, ApiRef | null>;

const jsonCall = (path: string, token: string | undefined, method: string, body: unknown) =>
  call(path, token, { method, headers: { 'content-type': 'application/json', accept: 'application/json' }, body: JSON.stringify(body) });

/** What Microcks says about a document before importing it; nothing is stored. */
export async function analyseArazzoDocument(content: string, links: BackendLinks | undefined, token?: string): Promise<ImportAnalysis> {
  return (await (await jsonCall('/api/arazzo/documents/analysis', token, 'POST', { content, links: links ?? null })).json()) as ImportAnalysis;
}

/** Imports a document with the links chosen for its sources, all or nothing; answers its summary. */
export async function importArazzoDocument(
  content: string,
  fileName: string,
  links: BackendLinks,
  token?: string
): Promise<BackendArazzoDocumentSummary> {
  return (await (await jsonCall('/api/arazzo/documents', token, 'POST', { content, fileName, links })).json()) as BackendArazzoDocumentSummary;
}

/** The same analysis for an imported document; `links` undefined = the links of its overlay. Undefined when unknown. */
export async function analyseImportedArazzoDocument(docId: string, links: BackendLinks | undefined, token?: string): Promise<ImportAnalysis | undefined> {
  try {
    const res = await jsonCall(`/api/arazzo/documents/${encodeURIComponent(docId)}/analysis`, token, 'POST', { links: links ?? null });
    return (await res.json()) as ImportAnalysis;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

/** Links the sources of an imported document again (replaces the sources of its overlay). Undefined when unknown. */
export async function relinkArazzoSources(docId: string, links: BackendLinks, token?: string): Promise<BackendArazzoDocumentSummary | undefined> {
  try {
    const res = await jsonCall(`/api/arazzo/documents/${encodeURIComponent(docId)}/sources`, token, 'PUT', { links });
    return (await res.json()) as BackendArazzoDocumentSummary;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

/* ---- Arazzo runs (`/api/arazzo`, ported from feature/arazzo S5–S11) ---- */

export interface BackendWorkflowRunSummary {
  id: string;
  number: number;
  workflowId: string;
  target: 'MOCKS' | 'IMPLEMENTATIONS' | 'MIXED';
  status: 'IN_PROGRESS' | 'SUCCESS' | 'FAILURE';
  failedStepId?: string | null;
  startedDate?: number | string;
  elapsedTime: number;
  /** Name of the recorded example the run played, and the verdict once it ended. */
  example?: string | null;
  exampleVerdict?: 'MATCHES' | 'DOES_NOT_MATCH' | null;
  /** `MOCK_URL` for a run called through the Mock URL of its workflow (S27), `SESSION` for a verification (S15). */
  origin?: 'MOCK_URL' | 'SESSION' | null;
}

import type { BackendMissingExample } from './runs-arazzo-schema';
import type { BackendSession, BackendSessionCall } from '#lib/workflows/sessions.js';

export interface BackendWorkflowStepResult {
  index: number;
  stepId: string;
  operation?: { sourceName: string; operationId: string; method?: string | null; path?: string | null } | null;
  status: string;
  statusCode?: number | null;
  elapsedTime: number;
  criteria?: { condition: string; values?: { expression: string; value: unknown }[]; held: boolean }[] | null;
  outputs?: Record<string, unknown> | null;
  testCaseId?: string | null;
  message?: string | null;
  requestUrl?: string | null;
  parameters?: { name: string; in: string; value: unknown; expression?: string | null }[] | null;
}

export interface BackendWorkflowResult {
  id: string;
  documentId: string;
  workflowId: string;
  target: 'MOCKS' | 'IMPLEMENTATIONS' | 'MIXED';
  sources?: { sourceName: string; serviceId?: string | null; baseUrl?: string | null }[];
  inputs?: Record<string, unknown> | null;
  outputs?: Record<string, unknown> | null;
  status: 'IN_PROGRESS' | 'SUCCESS' | 'FAILURE';
  startedDate?: number | string;
  elapsedTime: number;
  timeout?: number | null;
  secretRef?: { secretId?: string; name?: string } | null;
  steps?: BackendWorkflowStepResult[];
  message?: string | null;
  number?: number | null;
  /** `MOCK_URL` for a run called through the Mock URL of its workflow (S27), `SESSION` for a verification (S15). */
  origin?: 'MOCK_URL' | 'SESSION' | null;
  /** The session a verification checked, with the calls it received (S15). */
  session?: { sessionId: string; calls: BackendSessionCall[] } | null;
  /** S9: each response was validated against the OpenAPI contract. */
  validateSchema?: boolean;
  /** S10: the mock example the failed step needed and did not find (run on the mocks). */
  missingExample?: BackendMissingExample | null;
  /** The recorded example the run played, judged when the run is over. */
  example?: (BackendExample & { verdict?: 'MATCHES' | 'DOES_NOT_MATCH' | null; unmetExpectations?: string[] | null }) | null;
}

export interface BackendArazzoRunLaunch {
  inputs: Record<string, unknown>;
  timeout?: number;
  implementations?: Record<string, string>;
  secretName?: string;
  /** S9: validate each response against the OpenAPI contract (absent = no validation). */
  validateSchema?: boolean;
  /** S31: where each source goes in a mixed run, as the Deep Ink mockup launches it. */
  target?: { kind: 'mixed'; perSource: Record<string, 'mock' | 'implementation'> };
  /** Name of a recorded example to play: its inputs are used and the run is judged on it. */
  example?: string;
}

export async function launchArazzoRun(docId: string, workflowId: string, launch: BackendArazzoRunLaunch, token?: string): Promise<BackendWorkflowResult> {
  const path = `/api/arazzo/documents/${encodeURIComponent(docId)}/workflows/${encodeURIComponent(workflowId)}/runs`;
  const res = await call(path, token, { method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/json' }, body: JSON.stringify(launch) });
  return (await res.json()) as BackendWorkflowResult;
}

export async function fetchArazzoRun(runId: string, token?: string): Promise<BackendWorkflowResult | undefined> {
  try {
    const res = await call(`/api/arazzo/runs/${encodeURIComponent(runId)}`, token, { headers: { accept: 'application/json' } });
    return (await res.json()) as BackendWorkflowResult;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

export async function fetchArazzoRuns(docId: string, size: number, token?: string): Promise<BackendWorkflowRunSummary[]> {
  const res = await call(`/api/arazzo/documents/${encodeURIComponent(docId)}/runs?page=0&size=${size}`, token, { headers: { accept: 'application/json' } });
  return (await res.json()) as BackendWorkflowRunSummary[];
}

/** One run launched for an example of `POST …/examples/runs`: its run, or why it was refused. */
export interface BackendExampleLaunch {
  example: string;
  runId?: string | null;
  number?: number | null;
  refusal?: string | null;
}

/** Plays every recorded example of a workflow, one run each (what a CI does in one call). Undefined when unknown. */
export async function launchArazzoExampleRuns(
  docId: string,
  workflowId: string,
  launch: Pick<BackendArazzoRunLaunch, 'timeout' | 'validateSchema' | 'implementations' | 'secretName' | 'target'>,
  token?: string
): Promise<BackendExampleLaunch[] | undefined> {
  try {
    const path = `/api/arazzo/documents/${encodeURIComponent(docId)}/workflows/${encodeURIComponent(workflowId)}/examples/runs`;
    return (await (await call(path, token, { method: 'POST', headers: { 'content-type': 'application/json', accept: 'application/json' }, body: JSON.stringify(launch) })).json()) as BackendExampleLaunch[];
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

/* ---- Arazzo verification sessions (`/api/arazzo/sessions`, stories S15, S16, S17) ---- */

/** A session of Microcks, as `POST`, `GET /api/arazzo/sessions` and `GET /api/arazzo/sessions/{id}` give it. */
export type BackendArazzoSession = BackendSession;

/** The sessions of a workflow still kept by Microcks, the latest first. */
export async function listArazzoSessions(documentId: string, workflowId: string, token?: string): Promise<BackendArazzoSession[]> {
  const query = `documentId=${encodeURIComponent(documentId)}&workflowId=${encodeURIComponent(workflowId)}`;
  return (await (await call(`/api/arazzo/sessions?${query}`, token, { headers: { accept: 'application/json' } })).json()) as BackendArazzoSession[];
}

/** A session read now, with the calls counted so far; undefined when Microcks does not know it (or it expired). */
export async function fetchArazzoSession(sessionId: string, token?: string): Promise<BackendArazzoSession | undefined> {
  try {
    return (await (await call(`/api/arazzo/sessions/${encodeURIComponent(sessionId)}`, token, { headers: { accept: 'application/json' } })).json()) as BackendArazzoSession;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}

export async function openArazzoSession(
  request: { documentId: string; workflowId: string; inputs?: Record<string, unknown>; failures?: Record<string, string> },
  token?: string
): Promise<BackendArazzoSession> {
  return (await (await jsonCall('/api/arazzo/sessions', token, 'POST', request)).json()) as BackendArazzoSession;
}

/** Closes a session; false when Microcks does not know it. */
export async function closeArazzoSession(sessionId: string, token?: string): Promise<boolean> {
  try {
    await call(`/api/arazzo/sessions/${encodeURIComponent(sessionId)}`, token, { method: 'DELETE' });
    return true;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return false;
    throw e;
  }
}

/** The verification of a session: a run of origin `SESSION`; undefined when Microcks does not know the session. */
export async function verifyArazzoSession(sessionId: string, token?: string): Promise<BackendWorkflowResult | undefined> {
  try {
    return (await (await call(`/api/arazzo/sessions/${encodeURIComponent(sessionId)}/verification`, token, { method: 'POST', headers: { accept: 'application/json' } })).json()) as BackendWorkflowResult;
  } catch (e) {
    if (e instanceof BackendError && e.status === 404) return undefined;
    throw e;
  }
}
