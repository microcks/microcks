import type { ArazzoDocument, Session, Workflow } from '#lib/workflows/types.js';
import {
  BackendError,
  closeArazzoSession,
  fetchArazzoRun,
  fetchArazzoSession,
  fetchBackendServiceView,
  listArazzoSessions,
  openArazzoSession,
  verifyArazzoSession,
  type BackendServiceView,
  type BackendWorkflowResult
} from './backend';
import { failureChoicesOf, restMockPath, sessionOf } from '#lib/workflows/sessions.js';

/*
 * Verification sessions of imported documents (S15, S16, S17). Microcks keeps them: it lists the sessions of a workflow,
 * reads one with the calls it recorded so far, and keeps the run of its latest verification, which holds the calls
 * matched to the steps. Nothing is kept here.
 */

export interface SessionStepSetup {
  /** Where the mocks of the step's operation answer (REST only), to call with the `x-microcks-session` header. */
  mockPath?: string;
  /** The examples of its operation the mocks can answer it with when it is set to fail. */
  examples: { value: string; code: number }[];
}

export type SessionSetup = Record<string, SessionStepSetup>;

/** What the session builder offers for each step of a workflow: its mock path and the examples it can fail with. */
export async function sessionSetupOf(document: ArazzoDocument, workflow: Workflow, token?: string): Promise<SessionSetup> {
  const views = new Map<string, Promise<BackendServiceView | undefined>>();
  const viewOf = (serviceId: string) => {
    if (!views.has(serviceId)) views.set(serviceId, fetchBackendServiceView(serviceId, token).catch(() => undefined));
    return views.get(serviceId)!;
  };
  const entries = await Promise.all(
    workflow.steps.map(async (step): Promise<[string, SessionStepSetup]> => {
      const link = document.sources.find((s) => s.name === step.sourceName)?.link;
      const view = link?.status === 'linked' ? await viewOf(link.serviceId) : undefined;
      if (!view) return [step.stepId, { examples: [] }];
      const rest = view.service.type === 'REST' && step.method !== '';
      return [
        step.stepId,
        {
          ...(rest ? { mockPath: restMockPath(view.service.name, view.service.version, step.path) } : {}),
          examples: failureChoicesOf(view.messagesMap[`${step.method} ${step.path}`] ?? [])
        }
      ];
    })
  );
  return Object.fromEntries(entries);
}

/** The sessions of a workflow Microcks keeps, latest first, each with its live call count and its verification. */
export async function sessionsOf(documentId: string, workflowId: string, token?: string): Promise<Session[]> {
  const backend = await listArazzoSessions(documentId, workflowId, token).catch(() => []);
  return Promise.all(
    backend.map(async (s) => sessionOf(s, s.verificationRunId ? await fetchArazzoRun(s.verificationRunId, token).catch(() => undefined) : undefined))
  );
}

export type OpenResult = { session: Session } | { status: 404 | 422; error: string };

/** Opens a session on a workflow; Microcks refuses an input or a failure it does not know (the reason is its answer). */
export async function openRealSession(
  input: { documentId: string; workflowId: string; inputs?: Record<string, unknown>; failures?: Record<string, string> },
  token?: string
): Promise<OpenResult> {
  try {
    return { session: sessionOf(await openArazzoSession(input, token)) };
  } catch (e) {
    if (e instanceof BackendError && (e.status === 400 || e.status === 404)) return { status: e.status === 404 ? 404 : 422, error: e.message };
    throw e;
  }
}

/** Closes a session: it records no more calls. Undefined when Microcks does not know it. */
export async function closeRealSession(sessionId: string, token?: string): Promise<Session | undefined> {
  if (!(await closeArazzoSession(sessionId, token))) return undefined;
  return readSession(sessionId, token);
}

/** Verifies the calls a session received: a run of origin SESSION. Undefined when Microcks does not know the session. */
export async function verifyRealSession(sessionId: string, token?: string): Promise<Session | undefined> {
  const run = await verifyArazzoSession(sessionId, token);
  if (!run) return undefined;
  return readSession(sessionId, token, run);
}

/** A session as Microcks keeps it now, with its verification run when it has one (the one just made, if given). */
async function readSession(sessionId: string, token?: string, verification?: BackendWorkflowResult): Promise<Session | undefined> {
  const backend = await fetchArazzoSession(sessionId, token);
  if (!backend) return undefined;
  return sessionOf(backend, verification ?? (backend.verificationRunId ? await fetchArazzoRun(backend.verificationRunId, token).catch(() => undefined) : undefined));
}
