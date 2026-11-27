import type { RunTarget, StepResult, StepStatus, Workflow, WorkflowRun } from '#lib/workflows/types.js';
import type { FieldError, StartRunInput, WorkflowRunPayload } from '#lib/runs-workflow.js';
import { typedInputs, timeoutOrDefault, validateRunRequest } from '#lib/runs-workflow.js';
import {
  BackendError,
  fetchArazzoRun,
  fetchArazzoRuns,
  fetchBackendTestMessages,
  launchArazzoExampleRuns,
  launchArazzoRun,
  type BackendTestMessage,
  type BackendWorkflowResult,
  type BackendWorkflowRunSummary,
  type BackendWorkflowStepResult,
  type BackendArazzoRunLaunch
} from './backend';
import { coherenceOf, schemaOf } from './runs-arazzo-schema';
import { attemptsOf, groupByStep } from '#lib/workflows/attempts.js';
import { baggage, callsMocks } from '#lib/workflows/baggage.js';
import { originOf } from '#lib/workflows/mock-url.js';
import { exampleCheckOf, toWorkflowExample } from '#lib/workflows/examples.js';

/*
 * Real runs of the workflows of imported Arazzo documents (feature/arazzo S5–S11): launched on the Microcks backend,
 * executed by the Arazzo minion, read back from `/api/arazzo/runs` and mapped to the run model of the Deep Ink mockup.
 * Response schema validation (S9) and missing mock examples (S10) are read from the run. Recorded examples (S16/S20) and the retries
 * and early ends of the step actions (S17/S21) are read from the run.
 */

const STATUS: Record<BackendWorkflowResult['status'], WorkflowRun['status']> = {
  IN_PROGRESS: 'RUNNING',
  SUCCESS: 'SUCCESS',
  FAILURE: 'FAILURE'
};

const exampleCheckField = (check: ReturnType<typeof exampleCheckOf>) => (check ? { exampleCheck: check } : {});

const iso = (v: number | string | undefined) => new Date(v ?? Date.now()).toISOString();

/** A base URL relative to Microcks is the path of the mocks of a source (the run sent it to its mocks). */
const isMockPath = (baseUrl: string | null | undefined) => !baseUrl || baseUrl.startsWith('/');

function targetOf(run: Pick<BackendWorkflowResult, 'target' | 'sources'>): RunTarget {
  if (run.target === 'MOCKS') return { kind: 'mocks' };
  const baseUrls: Record<string, string> = {};
  const perSource: Record<string, 'mock' | 'implementation'> = {};
  for (const s of run.sources ?? []) {
    perSource[s.sourceName] = isMockPath(s.baseUrl) ? 'mock' : 'implementation';
    if (s.baseUrl && !isMockPath(s.baseUrl)) baseUrls[s.sourceName] = s.baseUrl;
  }
  if (run.target === 'MIXED') return { kind: 'mixed', perSource, baseUrls };
  return { kind: 'implementations', baseUrls };
}

function flowOf(
  status: StepStatus,
  step: Workflow['steps'][number],
  isLast: boolean,
  endedEarly: boolean,
  attempts: number,
  message: string | undefined
): StepResult['flow'] {
  if (status === 'not-run') return { kind: 'next', title: '', text: '' };
  if (status === 'passed') {
    const retried = attempts > 1 ? ` after ${attempts} attempts` : '';
    if (endedEarly) {
      const end = step.onSuccess.find((a) => a.type === 'end');
      return {
        kind: 'end',
        title: `onSuccess ${end?.name ?? 'end'}: the run ends here`,
        text: `${step.stepId} passed${retried} and its onSuccess end action ended the run: the later steps are not run.`
      };
    }
    return isLast
      ? { kind: 'next', title: 'Last step', text: `The step passed${retried} and it is the last one: the run ends with SUCCESS.` }
      : { kind: 'next', title: 'Next step', text: `The step passed${retried}, so the run continues with the next step.` };
  }
  const retry = step.onFailure.find((a) => a.type === 'retry');
  if (retry && attempts > 1) {
    return {
      kind: 'stop',
      title: `${retry.name} exhausted: the run ends here`,
      text: `${message ? `${message}. ` : ''}${step.stepId} failed on each of its ${attempts} attempts (retry ×${retry.type === 'retry' ? retry.retryLimit : 0}), so the run ends with FAILURE. The later steps are not run.`
    };
  }
  return {
    kind: 'stop',
    title: 'No onFailure action: the run ends here',
    text: `${message ? `${message}. ` : ''}${step.stepId} declares no onFailure action, so the run ends with FAILURE. The later steps are not run.`
  };
}

const stringOf = (v: unknown) => (typeof v === 'string' ? v : JSON.stringify(v));

function bodyLines(content: string | undefined): { text: string }[] {
  if (!content) return [];
  let text = content;
  try {
    text = JSON.stringify(JSON.parse(content), null, 2);
  } catch {
    /* not JSON: shown as is */
  }
  return text.split('\n').map((line) => ({ text: line }));
}

function stepResultOf(
  step: Workflow['steps'][number],
  entries: BackendWorkflowStepResult[],
  isLast: boolean,
  endedEarly: boolean,
  startedAt: string,
  message: BackendTestMessage | undefined,
  validateSchema: boolean,
  call: { runId: string; workflowId: string; target: RunTarget }
): StepResult {
  const result = entries[entries.length - 1];
  const status: StepStatus = !result ? 'not-run' : result.status === 'PASSED' ? 'passed' : result.status === 'FAILED' ? 'failed' : 'not-run';
  const params: Record<string, string> = {};
  for (const p of result?.parameters ?? []) params[p.name] = stringOf(p.value);
  return {
    stepId: step.stepId,
    status,
    attempts: attemptsOf(entries, startedAt, step.onFailure.find((a) => a.type === 'retry')?.retryAfter ?? 0),
    request: result
      ? { method: result.operation?.method ?? step.method, url: result.requestUrl ?? step.path, params, body: message?.request?.content || undefined }
      : undefined,
    response: result
      ? {
          meta: [result.statusCode ?? message?.response?.status, message?.response?.mediaType].filter(Boolean).join(' · '),
          lines: bodyLines(message?.response?.content)
        }
      : undefined,
    criteria: (result?.criteria ?? []).map((c) => ({
      condition: c.condition,
      kind: 'simple' as const,
      evaluation: (c.values ?? []).map((v) => `${v.expression} = ${stringOf(v.value)}`).join(', '),
      held: c.held
    })),
    schema: schemaOf(validateSchema, status, result?.message, step.operationId, result?.statusCode),
    outputs: result?.outputs ?? {},
    flow: flowOf(status, step, isLast, endedEarly, entries.length, result?.message ?? undefined),
    // The Arazzo minion sends this baggage with the calls to mocks (S29), not with those to implementations.
    baggage: result && callsMocks(call.target, step.sourceName) ? baggage(call.runId, call.workflowId, step.stepId, entries.length) : ''
  };
}

/** A full run, as the run page shows it: every step of the workflow, with the exchanges Microcks recorded. */
export async function uiRunOf(run: BackendWorkflowResult, workflow: Workflow, token?: string, withExchanges = true): Promise<WorkflowRun> {
  const startedAt = iso(run.startedDate);
  const target = targetOf(run);
  const byStep = groupByStep(run.steps ?? []);
  const messages = await Promise.all(
    workflow.steps.map(async (st) => {
      const tc = byStep.get(st.stepId)?.at(-1)?.testCaseId;
      if (!tc || !withExchanges) return undefined;
      return fetchBackendTestMessages(run.id, tc, token).then((m) => m[0]).catch(() => undefined);
    })
  );
  const steps = workflow.steps.map((st, i) => {
    const entries = byStep.get(st.stepId) ?? [];
    const next = workflow.steps[i + 1];
    // A passed step followed by steps that never ran, in a run that succeeded, is an onSuccess end.
    const endedEarly = run.status === 'SUCCESS' && entries.at(-1)?.status === 'PASSED' && next !== undefined && !byStep.has(next.stepId);
    return stepResultOf(st, entries, i === workflow.steps.length - 1, endedEarly, startedAt, messages[i], run.validateSchema === true, { runId: run.id, workflowId: run.workflowId, target });
  });
  const running = run.status === 'IN_PROGRESS';
  const current = running ? workflow.steps.find((st) => !byStep.has(st.stepId)) : undefined;
  return {
    id: run.id,
    number: run.number ?? 0,
    documentId: run.documentId,
    workflowId: run.workflowId,
    origin: originOf(run.origin),
    target,
    options: { example: run.example?.name, inputs: run.inputs ?? {}, secret: run.secretRef?.name, timeoutMs: run.timeout ?? 10_000, validateSchemas: run.validateSchema === true },
    status: STATUS[run.status],
    startedAt,
    steps,
    outputs: run.outputs ?? {},
    coherence: coherenceOf(run.missingExample),
    ...exampleCheckField(exampleCheckOf(run.example)),
    ...(current ? { current: { stepId: current.stepId, attempt: 1 } } : {})
  };
}

/** A run of the history list: its steps up to the one that failed, from the run summary. */
export function uiRunOfSummary(summary: BackendWorkflowRunSummary, documentId: string, workflow: Workflow | undefined): WorkflowRun {
  const startedAt = iso(summary.startedDate);
  const failedAt = workflow?.steps.findIndex((s) => s.stepId === summary.failedStepId) ?? -1;
  const steps: StepResult[] = (workflow?.steps ?? []).map((st, i) => {
    const status: StepStatus =
      summary.status === 'SUCCESS' ? 'passed' : failedAt < 0 ? 'not-run' : i < failedAt ? 'passed' : i === failedAt ? 'failed' : 'not-run';
    return {
      stepId: st.stepId,
      status,
      attempts: status === 'not-run' ? [] : [{ n: 1, httpStatus: 0, ms: 0, when: startedAt }],
      criteria: [],
      schema: schemaOf(false, status, undefined, st.operationId, undefined),
      outputs: {},
      flow: { kind: 'next', title: '', text: '' },
      baggage: ''
    };
  });
  return {
    id: summary.id,
    number: summary.number,
    documentId,
    workflowId: summary.workflowId,
    origin: originOf(summary.origin),
    target:
      summary.target === 'MOCKS'
        ? { kind: 'mocks' }
        : summary.target === 'MIXED'
          ? { kind: 'mixed', perSource: {} }
          : { kind: 'implementations', baseUrls: {} },
    options: { example: summary.example ?? undefined, inputs: {}, timeoutMs: 10_000, validateSchemas: false },
    status: STATUS[summary.status],
    startedAt,
    steps,
    outputs: {},
    coherence: [],
    ...exampleCheckField(exampleCheckOf(summary.example ? { name: summary.example, verdict: summary.exampleVerdict } : undefined))
  };
}

/** How many runs of a document the lists read in full (inputs, steps, why it stopped); older ones from their summary. */
const FULL_RUNS = 20;

/** The runs history of real documents, newest first, mapped for the lists (Workflows page, Runs tab, service page). */
export async function realRunsOf(docIds: string[], workflows: Workflow[], token?: string): Promise<WorkflowRun[]> {
  const lists = await Promise.all(docIds.map((id) => fetchArazzoRuns(id, 50, token).then((runs) => ({ id, runs })).catch(() => ({ id, runs: [] }))));
  const runs = await Promise.all(
    lists.flatMap(({ id, runs }) =>
      runs.map(async (summary, i) => {
        const workflow = workflows.find((w) => w.documentId === id && w.workflowId === summary.workflowId);
        const full = workflow && i < FULL_RUNS ? await fetchArazzoRun(summary.id, token).catch(() => undefined) : undefined;
        return full && workflow ? uiRunOf(full, workflow, token, false) : uiRunOfSummary(summary, id, workflow);
      })
    )
  );
  return runs;
}

export type StartRealResult = { runId: string } | { status: 404 | 422; error: string; errors?: FieldError[] };

/**
 * Where the run goes, as Microcks takes it: the mocks (nothing), the implementations of every source, or a mixed target
 * whose `implementations` hold the sources sent to their implementation (S31). The secret is for the implementations.
 */
function launchTargetOf(input: StartRunInput, sources: string[]): Pick<BackendArazzoRunLaunch, 'implementations' | 'secretName' | 'target'> {
  if (input.target === 'mocks') return {};
  const secretName = input.secret || undefined;
  if (input.target === 'implementations') return { implementations: input.baseUrls ?? {}, secretName };
  const perSource = Object.fromEntries(sources.map((name) => [name, input.perSource?.[name] === 'implementation' ? 'implementation' : 'mock'] as const));
  const implementations = Object.fromEntries(
    sources.filter((name) => perSource[name] === 'implementation').map((name) => [name, (input.baseUrls ?? {})[name] ?? ''])
  );
  const usesImplementation = Object.keys(implementations).length > 0;
  return { implementations, target: { kind: 'mixed', perSource }, ...(usesImplementation ? { secretName } : {}) };
}

/** Launches a run of a real workflow on the backend, after the same checks as the run setup form. */
export async function startRealRun(input: StartRunInput, workflow: Workflow, token?: string): Promise<StartRealResult> {
  const sources = [...new Set(workflow.steps.map((s) => s.sourceName))];
  const errors = validateRunRequest({
    workflowId: workflow.workflowId,
    notRunnableMessage: workflow.runnability.runnable ? undefined : workflow.runnability.message,
    target: input.target,
    sources,
    baseUrls: input.baseUrls ?? {},
    perSource: input.perSource,
    inputs: workflow.inputs.map((i) => ({ name: i.name, type: i.type, value: String(input.inputs?.[i.name] ?? '') })),
    timeoutMs: String(input.timeoutMs ?? '')
  });
  if (errors.length > 0) return { status: 422, error: errors[0]!.message, errors };
  const inputs = typedInputs(workflow.inputs, input.inputs ?? {});
  try {
    const run = await launchArazzoRun(
      workflow.documentId,
      workflow.workflowId,
      {
        inputs,
        timeout: timeoutOrDefault(String(input.timeoutMs)),
        validateSchema: input.validateSchemas === true,
        ...(input.example ? { example: input.example } : {}),
        ...launchTargetOf(input, sources)
      },
      token
    );
    return { runId: run.id };
  } catch (e) {
    if (e instanceof BackendError && (e.status === 400 || e.status === 404)) return { status: e.status === 404 ? 404 : 422, error: e.message };
    throw e;
  }
}

/** The run page payload of a real run; undefined when the backend does not know the run. */
export async function getRealRunPayload(
  runId: string,
  detailOf: (docId: string, workflowId: string) => Promise<{ document: WorkflowRunPayload['document']; workflow: Workflow } | undefined>,
  token?: string
): Promise<WorkflowRunPayload | undefined> {
  let run: BackendWorkflowResult | undefined;
  try {
    run = await fetchArazzoRun(runId, token);
  } catch (e) {
    if (e instanceof BackendError && e.status === 503) return undefined;
    throw e;
  }
  if (!run) return undefined;
  const detail = await detailOf(run.documentId, run.workflowId);
  if (!detail) return undefined;
  const played = run.example ? [toWorkflowExample({ ...run.example, workflowId: run.workflowId })] : [];
  return { demo: false, run: await uiRunOf(run, detail.workflow, token), document: detail.document, workflow: detail.workflow, examples: played };
}

export type StartExamplesResult =
  | { launches: { example: string; runId?: string; number?: number; refusal?: string }[] }
  | { status: 404 | 422; error: string };

/** Plays every recorded example of a real workflow on the mocks, one run each, and says what each launch became. */
export async function startRealExampleRuns(
  docId: string,
  workflowId: string,
  options: { validateSchemas?: boolean; timeoutMs?: number },
  token?: string
): Promise<StartExamplesResult> {
  try {
    const launches = await launchArazzoExampleRuns(docId, workflowId, { timeout: options.timeoutMs, validateSchema: options.validateSchemas === true }, token);
    if (!launches) return { status: 404, error: `Workflow "${workflowId}" does not exist in document "${docId}".` };
    return { launches: launches.map((l) => ({ example: l.example, runId: l.runId ?? undefined, number: l.number ?? undefined, refusal: l.refusal ?? undefined })) };
  } catch (e) {
    if (e instanceof BackendError && (e.status === 400 || e.status === 404)) return { status: e.status === 404 ? 404 : 422, error: e.message };
    throw e;
  }
}
