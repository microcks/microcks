import type { ArazzoDocument, Session, Workflow } from '#lib/workflows/types.js';
import { BackendError, closeArazzoSession, fetchBackendServiceView, openArazzoSession, verifyArazzoSession, type BackendArazzoSession, type BackendServiceView } from './backend';
import { failureChoicesOf, restMockPath, sessionCallsOf, verificationProblemsOf } from '#lib/workflows/sessions.js';

/*
 * Verification sessions of imported documents (S15, S16, S17). Microcks opens, closes and verifies a session but has no
 * way to list or read one, so the sessions this UI opened are kept here, in memory, with what Microcks said about them:
 * the call count and the calls are known once the session is verified. The verifications themselves are runs of Microcks
 * (origin SESSION) and last with its history.
 */

const sessions = new Map<string, Session>();

/** Sessions kept per workflow, the latest first: an open session that Microcks expired is forgotten with the oldest. */
const KEPT = 20;

const iso = (v: number | string | undefined | null) => new Date(v ?? Date.now()).toISOString();

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

function uiSessionOf(s: BackendArazzoSession): Session {
  const failures = s.failures ?? {};
  const [first] = Object.entries(failures);
  return {
    id: s.id,
    app: '',
    documentId: s.documentId,
    workflowId: s.workflowId,
    state: s.closedDate ? 'closed' : 'open',
    openedAt: iso(s.openedDate),
    ...(s.closedDate ? { closedAt: iso(s.closedDate) } : {}),
    inputs: s.inputs ?? {},
    ...(first ? { failureInjection: { stepId: first[0], example: first[1] } } : {}),
    calls: [],
    real: { failures, droppedCallCount: s.droppedCallCount ?? 0 }
  };
}

/** The sessions of a workflow this UI opened, latest first. */
export const sessionsOf = (documentId: string, workflowId: string): Session[] =>
  [...sessions.values()].filter((s) => s.documentId === documentId && s.workflowId === workflowId).reverse().slice(0, KEPT);

export type OpenResult = { session: Session } | { status: 404 | 422; error: string };

/** Opens a session on a workflow; Microcks refuses an input or a failure it does not know (the reason is its answer). */
export async function openRealSession(
  input: { documentId: string; workflowId: string; inputs?: Record<string, unknown>; failures?: Record<string, string> },
  token?: string
): Promise<OpenResult> {
  try {
    const opened = await openArazzoSession(input, token);
    const session = uiSessionOf({ ...opened, documentId: input.documentId });
    sessions.set(session.id, session);
    return { session };
  } catch (e) {
    if (e instanceof BackendError && (e.status === 400 || e.status === 404)) return { status: e.status === 404 ? 404 : 422, error: e.message };
    throw e;
  }
}

/** Closes a session: it records no more calls. False when Microcks does not know it. */
export async function closeRealSession(sessionId: string, token?: string): Promise<Session | undefined> {
  if (!(await closeArazzoSession(sessionId, token))) return undefined;
  const known = sessions.get(sessionId);
  if (known) {
    known.state = 'closed';
    known.closedAt = new Date().toISOString();
  }
  return known;
}

/** Verifies the calls a session received: a run of origin SESSION. Undefined when Microcks does not know the session. */
export async function verifyRealSession(sessionId: string, token?: string): Promise<Session | undefined> {
  const run = await verifyArazzoSession(sessionId, token);
  if (!run) return undefined;
  const calls = sessionCallsOf(run.session?.calls ?? [], run.steps ?? []);
  // A session opened before this server started is not known here: it is listed from now on.
  const known =
    sessions.get(sessionId) ??
    uiSessionOf({ id: sessionId, documentId: run.documentId, workflowId: run.workflowId, openedDate: run.startedDate, callCount: calls.length, droppedCallCount: 0, inputs: run.inputs });
  sessions.set(sessionId, known);
  known.calls = calls;
  known.verification = {
    result: run.status === 'SUCCESS' ? 'SUCCESS' : 'FAILURE',
    runNumber: run.number ?? 0,
    problems: verificationProblemsOf(run.steps ?? [], calls, run.message)
  };
  return known;
}
