import type { ArazzoDocument, Session, SourceProtocol, Workflow, WorkflowExample, WorkflowRun } from '#lib/workflows/types.js';
import type { Service } from '#lib/types.js';
import {
  DOCUMENTS,
  EXAMPLES,
  PASTRY_ARAZZO_YAML,
  PASTRY_OVERLAY_YAML,
  BEER_ARAZZO_YAML,
  WORKFLOWS,
  seedRuns,
  seedSessions
} from '#lib/placeholders/workflows.js';
import type { WorkflowsPayload } from '#lib/workflows/list.js';
import { mockUrlOf } from '#lib/workflows/mock-url.js';
import { dependsOnOf, messagingOf, runnabilityOf } from '#lib/workflows/step-mapping.js';
import { toActions } from '#lib/workflows/attempts.js';
import { toWorkflowExample } from '#lib/workflows/examples.js';
import { documentOrigin, jobOfDocument } from '#lib/workflows/source-job.js';
import type { ImportAnalysis } from '#lib/workflows/import-analysis.js';
import { parse as parseYaml } from 'yaml';
import { formatRelative } from '#lib/format.js';
import { MICROCKS_API_URL } from './auth.js';
import {
  BackendError,
  fetchArazzoDocuments,
  fetchBackendJobs,
  type BackendJob,
  fetchArazzoSource,
  fetchArazzoWorkflow,
  analyseImportedArazzoDocument,
  protocolOf,
  relinkArazzoSources,
  type BackendLinks,
  type BackendArazzoDocumentSummary,
  type BackendArazzoStep,
  type BackendArazzoWorkflowDetail
} from './backend';
import type { Criterion, StepParameter, WorkflowInput } from '#lib/workflows/types.js';
import { realRunsOf } from './runs-arazzo';
import { withSessionLabels } from '#lib/workflows/sessions.js';
import { sessionsOf } from './sessions-arazzo';
import { getService, listServices, refreshServices } from './store';

/*
 * Arazzo documents and workflows come from the Microcks backend (`/api/arazzo`, stories S1–S4 of feature/arazzo).
 * When the backend is unreachable, the placeholder below is shown instead, flagged as demo data.
 * Runs, sessions and examples (S5 and later) are not served by the backend yet: real workflows get none, and runs
 * launched from the UI are simulated in memory.
 *
 * PLACEHOLDER: Microcks has no Arazzo API yet (the parser does not recognise Arazzo artifacts), so documents,
 * workflows, runs, sessions and examples come from the placeholder module and live in memory. What is real:
 * the services a source description links to (resolved against the store) and the edits made from the UI.
 */

const clone = <T>(v: T): T => structuredClone(v);

/** Placeholder logical service ids → a predicate picking a service of the real store. */
const LOGICAL: Record<string, (s: Service) => boolean> = {
  pastry2: (s) => /api pastry\b.*2\.0/i.test(s.name) || /pastry/i.test(s.name) && s.version.startsWith('2'),
  pastry1: (s) => /pastries/i.test(s.name) || /pastry/i.test(s.name),
  orders: (s) => s.protocol === 'EVENT'
};

const resolveLogical = (id: string): string | undefined => {
  const services = listServices();
  if (services.some((s) => s.id === id)) return id;
  return services.find(LOGICAL[id] ?? (() => false))?.id;
};

const state = {
  documents: clone(DOCUMENTS) as ArazzoDocument[],
  workflows: clone(WORKFLOWS) as Workflow[],
  runs: seedRuns() as WorkflowRun[],
  sessions: seedSessions() as Session[],
  examples: clone(EXAMPLES) as WorkflowExample[],
  resolved: false
};

/** Replaces the logical service ids of the placeholder by real store services, once the store is seeded. */
function ensureResolved() {
  if (state.resolved || listServices().length === 0) return;
  state.resolved = true;
  for (const doc of state.documents) {
    for (const src of doc.sources) {
      if (src.link.status !== 'linked') continue;
      const real = resolveLogical(src.link.serviceId);
      if (real) src.link.serviceId = real;
    }
  }
}

export function listAll(): WorkflowsPayload {
  ensureResolved();
  return { documents: state.documents, workflows: state.workflows, runs: state.runs };
}

export interface WorkflowDetailPayload {
  document: ArazzoDocument;
  workflow: Workflow;
  runs: WorkflowRun[];
  sessions: Session[];
  examples: WorkflowExample[];
  /** True for placeholder data (the Microcks backend is unreachable or does not know the document). */
  demo?: boolean;
}

export function getDetail(docId: string, workflowId: string): WorkflowDetailPayload | undefined {
  const realWorkflow = real.workflows.get(key(docId, workflowId));
  if (realWorkflow) {
    const runs = state.runs.filter((r) => r.documentId === docId && r.workflowId === workflowId).sort((a, b) => b.number - a.number);
    return { document: real.documents.get(docId)!, workflow: realWorkflow, runs, sessions: [], examples: real.examples.get(key(docId, workflowId)) ?? [] };
  }
  ensureResolved();
  const document = state.documents.find((d) => d.id === docId);
  const workflow = state.workflows.find((w) => w.documentId === docId && w.workflowId === workflowId);
  if (!document || !workflow) return undefined;
  const mine = <T extends { documentId: string; workflowId: string }>(x: T) =>
    x.documentId === docId && x.workflowId === workflowId;
  return {
    document,
    workflow,
    runs: state.runs.filter(mine).sort((a, b) => b.number - a.number),
    sessions: state.sessions.filter(mine),
    examples: state.examples.filter((e) => e.workflowId === workflowId)
  };
}

export const getDocument = (docId: string) => {
  if (real.documents.has(docId)) return real.documents.get(docId);
  ensureResolved();
  return state.documents.find((d) => d.id === docId);
};

export function getSource(docId: string): string | undefined {
  if (!getDocument(docId)) return undefined;
  return docId === 'pastry' ? PASTRY_ARAZZO_YAML : docId === 'beer' ? BEER_ARAZZO_YAML : '';
}

export function getOverlay(docId: string): string | null | undefined {
  const doc = getDocument(docId);
  if (!doc) return undefined;
  return doc.overlay && docId === 'pastry' ? PASTRY_OVERLAY_YAML : null;
}

export function linkSource(docId: string, name: string, serviceId: string): ArazzoDocument | { error: string; status: number } {
  const doc = getDocument(docId);
  if (!doc) return { error: `Document "${docId}" does not exist.`, status: 404 };
  const src = doc.sources.find((s) => s.name === name);
  if (!src) return { error: `Source "${name}" does not exist in ${doc.file}.`, status: 404 };
  const service = getService(serviceId);
  if (!service) return { error: `Service "${serviceId}" does not exist.`, status: 422 };
  const asyncapi = src.type === 'asyncapi';
  if (asyncapi !== (service.protocol === 'EVENT')) {
    return { error: `${src.name} is ${asyncapi ? 'an AsyncAPI' : 'an OpenAPI'} source: pick a matching API.`, status: 422 };
  }
  const used = new Set(
    state.workflows.filter((w) => w.documentId === docId).flatMap((w) => w.steps.filter((s) => s.sourceName === name).map((s) => s.operationId))
  ).size;
  src.link = { status: 'linked', serviceId, protocol: service.protocol as SourceProtocol, operationsFound: `${used} of ${used} found` };
  for (const w of state.workflows) {
    if (w.documentId !== docId || !w.steps.some((s) => s.sourceName === name)) continue;
    const blocked = doc.sources.filter(
      (s) => s.link.status === 'not-linked' && w.steps.some((st) => st.sourceName === s.name)
    );
    if (w.runnability.runnable === false && w.runnability.reason === 'source-not-linked' && blocked.length === 0) {
      w.runnability = { runnable: true };
    }
  }
  return doc;
}

/** In-memory edit of a workflow from the Loom editor: summary, description and the step list. */
export function updateWorkflow(
  docId: string,
  workflowId: string,
  patch: Partial<Pick<Workflow, 'summary' | 'description' | 'steps' | 'outputs'>>
): Workflow | undefined {
  const wf = state.workflows.find((w) => w.documentId === docId && w.workflowId === workflowId);
  if (!wf) return undefined;
  if (typeof patch.summary === 'string') wf.summary = patch.summary;
  if (typeof patch.description === 'string') wf.description = patch.description;
  if (Array.isArray(patch.steps)) wf.steps = patch.steps;
  if (Array.isArray(patch.outputs)) wf.outputs = patch.outputs;
  return wf;
}

/* Run accessors added by the `runs` domain (workflow run pages): read, create and number runs in the same store. */
export const getRun = (id: string): WorkflowRun | undefined => state.runs.find((r) => r.id === id);

export const addRun = (run: WorkflowRun): void => {
  state.runs.push(run);
};

export const nextRunNumber = (docId: string, workflowId: string): number =>
  state.runs.filter((r) => r.documentId === docId && r.workflowId === workflowId).reduce((max, r) => Math.max(max, r.number), 0) + 1;

/* ---------------------------------------------------------------------------------------------------------------
 * Backend mode: documents and workflows read from `/api/arazzo`, mapped to the UI types of the Deep Ink mockup.
 * ------------------------------------------------------------------------------------------------------------- */

/** Real documents and workflows, cached for the synchronous accessors used by the (simulated) run pages. */
const real = { documents: new Map<string, ArazzoDocument>(), workflows: new Map<string, Workflow>(), examples: new Map<string, WorkflowExample[]>() };
const key = (docId: string, workflowId: string) => `${docId}/${workflowId}`;

type Obj = Record<string, any>;

/** The source descriptions as written in the document: type and URL are not in the backend summary. */
function sourceDescriptionsOf(content: string | undefined): Map<string, { type: 'openapi' | 'asyncapi'; url: string }> {
  const out = new Map<string, { type: 'openapi' | 'asyncapi'; url: string }>();
  let doc: Obj | undefined;
  try {
    doc = content ? (parseYaml(content) as Obj) : undefined;
  } catch {
    doc = undefined;
  }
  for (const sd of Array.isArray(doc?.sourceDescriptions) ? (doc!.sourceDescriptions as Obj[]) : []) {
    out.set(String(sd.name), { type: sd.type === 'asyncapi' ? 'asyncapi' : 'openapi', url: String(sd.url ?? '') });
  }
  return out;
}

const fileOf = (url: string) => url.split('/').pop() || url;

/** Microcks keeps the links of an overlay, not its file: the overlay is named after the document it applies to. */
const overlayFileOf = (summary: BackendArazzoDocumentSummary) =>
  `${summary.title.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}.arazzo-sources.yaml`;

/** The ArazzoSources overlay giving these links, as Microcks imports it. */
function overlayYaml(title: string, version: string, links: Map<string, readonly [string, string]>): string {
  const q = (v: string) => JSON.stringify(v);
  return [
    'apiVersion: mocks.microcks.io/v1alpha1',
    'kind: ArazzoSources',
    'metadata:',
    `  name: ${q(title)}`,
    `  version: ${q(version)}`,
    'sources:',
    ...[...links].flatMap(([source, [api, v]]) => [`  ${q(source)}:`, `    name: ${q(api)}`, `    version: ${q(v)}`])
  ].join('\n') + '\n';
}

const linksOf = (summary: BackendArazzoDocumentSummary) =>
  new Map(
    summary.sources.filter((s) => s.link.apiName).map((s) => [s.link.sourceName, [s.link.apiName!, s.link.apiVersion ?? ''] as const])
  );

/** Overlay of a real document, rebuilt from the links Microcks holds; null when it has none. */
export async function getDocumentOverlay(docId: string, token?: string): Promise<string | null | undefined> {
  if (isPlaceholderDocument(docId)) return getOverlay(docId);
  const summary = (await fetchArazzoDocuments(token)).find((d) => d.id === docId);
  if (!summary) return undefined;
  const links = linksOf(summary);
  return links.size ? overlayYaml(summary.title, summary.version, links) : null;
}

function toDocument(
  summary: BackendArazzoDocumentSummary,
  details: BackendArazzoWorkflowDetail[],
  content: string | undefined,
  job: BackendJob | undefined
): ArazzoDocument {
  const described = sourceDescriptionsOf(content);
  const counts = new Map(details[0]?.sources.map((s) => [s.link.sourceName, s]) ?? []);
  const ago = (ms: number) => formatRelative(new Date(ms).toISOString());
  return {
    id: summary.id,
    apiId: summary.id,
    file: summary.fileName ?? `${summary.title}.arazzo.yaml`,
    title: summary.title,
    arazzoVersion: summary.arazzoVersion,
    version: summary.version,
    origin: documentOrigin(job, summary.importDate === undefined ? undefined : Number(new Date(summary.importDate)), ago),
    ...(job ? { job: { id: job.id, name: job.name } } : {}),
    sources: summary.sources.map((s) => {
      const sd = described.get(s.link.sourceName);
      const type = sd?.type ?? (s.apiType === 'EVENT' ? 'asyncapi' : 'openapi');
      const used = counts.get(s.link.sourceName);
      return {
        name: s.link.sourceName,
        type,
        url: sd?.url ?? '',
        link: s.link.linked && s.apiId
          ? {
              status: 'linked' as const,
              serviceId: s.apiId,
              protocol: protocolOf(s.apiType ?? 'REST') as SourceProtocol,
              operationsFound: used ? `${used.usedOperations} of ${used.operations} found` : 'linked'
            }
          : {
              status: 'not-linked' as const,
              expectedFile: s.link.apiName ? `${s.link.apiName} ${s.link.apiVersion ?? ''}`.trim() : fileOf(sd?.url ?? s.link.sourceName)
            }
      };
    }),
    overlay: summary.sources.some((s) => s.link.apiName) ? overlayFileOf(summary) : undefined,
    workflowIds: summary.workflows.map((w) => w.status.workflowId),
    importIssues: []
  };
}

const INPUT_TYPES: WorkflowInput['type'][] = ['string', 'number', 'integer', 'boolean'];

const IN: Record<string, StepParameter['in']> = { path: 'path', query: 'query', header: 'header' };

function toCriterion(c: BackendArazzoStep['successCriteria'][number]): Criterion {
  return c.type === 'regex' && c.context
    ? { type: 'regex', context: c.context, condition: c.condition }
    : { type: 'simple', condition: c.condition };
}

const named = (m: Record<string, string> | undefined) => Object.entries(m ?? {}).map(([name, expression]) => ({ name, expression }));

function toWorkflow(d: BackendArazzoWorkflowDetail, status: BackendArazzoDocumentSummary['workflows'][number] | undefined): Workflow {
  const dependsOn = dependsOnOf(d.steps.map((st) => ({ stepId: st.stepId, dependsOn: st.dependsOn ?? [], prerequisites: st.prerequisites ?? [] })));
  return {
    workflowId: d.workflowId,
    documentId: d.documentId,
    summary: d.summary || status?.description || d.workflowId,
    description: d.description ?? undefined,
    inputs: d.inputs.map((i) => ({ name: i.name, type: INPUT_TYPES.includes(i.type as never) ? (i.type as WorkflowInput['type']) : 'string' })),
    steps: d.steps.map((st, i) => {
      const messaging = messagingOf(st.messaging, st.operation.channel);
      return {
        stepId: st.stepId,
        sourceName: st.operation.sourceName,
        operationId: st.operation.operationId,
        method: st.operation.method ?? '',
        // As in the mockup, an AsyncAPI step shows its channel where an HTTP step shows its path.
        path: (messaging && st.operation.channel) || st.operation.path || '',
        description: st.description ?? '',
        parameters: st.parameters.map((p) => ({ name: p.name, in: IN[p.in] ?? 'query', value: p.value })),
        requestBody: st.requestBody?.payload ?? undefined,
        successCriteria: st.successCriteria.map(toCriterion),
        onSuccess: toActions(st.onSuccess),
        onFailure: toActions(st.onFailure),
        outputs: named(st.outputs),
        ...(dependsOn[i] ? { dependsOn: dependsOn[i] } : {}),
        ...(st.prerequisites?.length ? { prerequisites: st.prerequisites } : {}),
        ...(messaging ? { messaging } : {})
      };
    }),
    outputs: named(d.outputs),
    mockUrl: mockUrlOf(MICROCKS_API_URL, d.title, d.version, d.workflowId),
    runnability: runnabilityOf(status?.status)
  };
}

/** Reads every document with its workflows from the backend; undefined when the backend is unreachable. */
async function fetchReal(token?: string): Promise<{ documents: ArazzoDocument[]; workflows: Workflow[] } | undefined> {
  let summaries: BackendArazzoDocumentSummary[];
  try {
    summaries = await fetchArazzoDocuments(token);
  } catch (e) {
    if (e instanceof BackendError && (e.status === 503 || e.status === 404)) return undefined;
    throw e;
  }
  const documents: ArazzoDocument[] = [];
  const workflows: Workflow[] = [];
  const examplesByWorkflow: [string, WorkflowExample[]][] = [];
  const jobs = await fetchBackendJobs(token).catch(() => [] as BackendJob[]);
  await Promise.all(
    summaries.map(async (summary) => {
      const [content, ...details] = await Promise.all([
        fetchArazzoSource(summary.id, token),
        ...summary.workflows.map((w) => fetchArazzoWorkflow(summary.id, w.status.workflowId, token))
      ]);
      const found = details.filter((d): d is BackendArazzoWorkflowDetail => d !== undefined);
      documents.push(toDocument(summary, found, content as string | undefined, jobOfDocument(jobs, summary.id)));
      for (const d of found) examplesByWorkflow.push([key(summary.id, d.workflowId), (d.examples ?? []).map(toWorkflowExample)]);
      for (const d of found) workflows.push(toWorkflow(d, summary.workflows.find((w) => w.status.workflowId === d.workflowId)));
    })
  );
  const order = new Map(summaries.map((s, i) => [s.id, i]));
  documents.sort((a, b) => order.get(a.id)! - order.get(b.id)!);
  workflows.sort((a, b) => order.get(a.documentId)! - order.get(b.documentId)!);
  real.documents = new Map(documents.map((d) => [d.id, d]));
  real.workflows = new Map(workflows.map((w) => [key(w.documentId, w.workflowId), w]));
  real.examples = new Map(examplesByWorkflow);
  return { documents, workflows };
}

/** Whether a document belongs to the placeholder data (otherwise it is a real one, read from the backend). */
export const isPlaceholderDocument = (docId: string) => state.documents.some((d) => d.id === docId);

/** Workflows screen: real documents and workflows, or the placeholder when the backend is unreachable. */
export async function listWorkflowsPayload(token?: string): Promise<WorkflowsPayload & { demo: boolean }> {
  await refreshServices(token);
  const fetched = await fetchReal(token);
  if (!fetched) return { ...listAll(), demo: true };
  const runs = await realRunsOf(fetched.documents.map((d) => d.id), fetched.workflows, token);
  return { ...fetched, runs, demo: false };
}

/** Workflow page: the real workflow when the backend knows the document, else the placeholder one. */
export async function getWorkflowDetail(
  docId: string,
  workflowId: string,
  token?: string
): Promise<(WorkflowDetailPayload & { demo: boolean }) | undefined> {
  if (!isPlaceholderDocument(docId)) {
    const fetched = await fetchReal(token);
    if (fetched) {
      const document = real.documents.get(docId);
      const workflow = real.workflows.get(key(docId, workflowId));
      if (!document || !workflow) return undefined;
      const [listed, sessions] = await Promise.all([realRunsOf([docId], [workflow], token), sessionsOf(docId, workflowId, token)]);
      const runs = withSessionLabels(listed.filter((r) => r.workflowId === workflowId), sessions);
      return { document, workflow, runs, sessions, examples: real.examples.get(key(docId, workflowId)) ?? [], demo: false };
    }
  }
  const detail = getDetail(docId, workflowId);
  return detail && { ...detail, demo: true };
}

/** Source tab: the document as uploaded. */
export async function getDocumentSource(docId: string, token?: string): Promise<string | undefined> {
  if (isPlaceholderDocument(docId)) return getSource(docId);
  return fetchArazzoSource(docId, token);
}

/**
 * The links of a real document with one source description linked (or linked again) to an API of Microcks, as
 * Microcks takes them (story S19), after the same checks as the dialog.
 */
async function linksWith(
  docId: string,
  name: string,
  serviceId: string,
  token?: string
): Promise<{ links: BackendLinks } | { error: string; status: number }> {
  const summaries = await fetchArazzoDocuments(token);
  const summary = summaries.find((d) => d.id === docId);
  if (!summary) return { error: `Document "${docId}" does not exist.`, status: 404 };
  if (!summary.sources.some((s) => s.link.sourceName === name)) {
    return { error: `Source "${name}" does not exist in ${summary.fileName ?? summary.title}.`, status: 404 };
  }
  await refreshServices(token);
  const service = getService(serviceId);
  if (!service) return { error: `Service "${serviceId}" does not exist.`, status: 422 };
  const content = await fetchArazzoSource(docId, token);
  const type = sourceDescriptionsOf(content).get(name)?.type ?? 'openapi';
  if ((type === 'asyncapi') !== (service.protocol === 'EVENT')) {
    return { error: `${name} is ${type === 'asyncapi' ? 'an AsyncAPI' : 'an OpenAPI'} source: pick a matching API.`, status: 422 };
  }
  const links: BackendLinks = Object.fromEntries([...linksOf(summary)].map(([source, [apiName, apiVersion]]) => [source, { name: apiName, version: apiVersion }]));
  links[name] = { serviceId: service.id, name: service.name, version: service.version };
  return { links };
}

/** What Microcks makes of a document if this source were linked to this API; nothing is stored. */
export async function analyseLink(docId: string, name: string, serviceId: string, token?: string): Promise<ImportAnalysis | { error: string; status: number }> {
  const out = await linksWith(docId, name, serviceId, token);
  if ('error' in out) return out;
  return (await analyseImportedArazzoDocument(docId, out.links, token)) ?? { error: `Document "${docId}" does not exist.`, status: 404 };
}

/** Links a source description of a real document to a Microcks API: the links replace those of its overlay. */
export async function linkRealSource(
  docId: string,
  name: string,
  serviceId: string,
  token?: string
): Promise<ArazzoDocument | { error: string; status: number }> {
  const out = await linksWith(docId, name, serviceId, token);
  if ('error' in out) return out;
  if (!(await relinkArazzoSources(docId, out.links, token))) return { error: `Document "${docId}" does not exist.`, status: 404 };
  await fetchReal(token);
  return real.documents.get(docId) ?? { error: `Document "${docId}" does not exist.`, status: 404 };
}

/** A real document as the screens show it, read again from the backend (e.g. right after an import). */
export async function arazzoDocumentOf(docId: string, token?: string): Promise<ArazzoDocument | undefined> {
  await fetchReal(token);
  return real.documents.get(docId);
}
