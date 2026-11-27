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
import { getService, listServices } from './store';

/*
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
}

export function getDetail(docId: string, workflowId: string): WorkflowDetailPayload | undefined {
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
