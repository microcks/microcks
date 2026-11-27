import type { RunTarget, StepResult, StepStatus, Workflow, WorkflowRun } from '#lib/workflows/types.js';
import type { FieldError, StartRunInput, WorkflowRunPayload } from '#lib/runs-workflow.js';
import { typedInputs, timeoutOrDefault, validateRunRequest } from '#lib/runs-workflow.js';
import {
  BackendError,
  fetchArazzoRun,
  fetchArazzoRuns,
  fetchBackendTestMessages,
  launchArazzoRun,
  type BackendTestMessage,
  type BackendWorkflowResult,
  type BackendWorkflowRunSummary,
  type BackendWorkflowStepResult
} from './backend';

/*
 * Real runs of the workflows of imported Arazzo documents (feature/arazzo S5–S11): launched on the Microcks backend,
 * executed by the Arazzo minion, read back from `/api/arazzo/runs` and mapped to the run model of the Deep Ink mockup.
 * Not covered yet by the backend: response schema validation (S9), recorded examples (S16/S20), retries (S17).
 */

const STATUS: Record<BackendWorkflowResult['status'], WorkflowRun['status']> = {
  IN_PROGRESS: 'RUNNING',
  SUCCESS: 'SUCCESS',
  FAILURE: 'FAILURE'
};

const iso = (v: number | string | undefined) => new Date(v ?? Date.now()).toISOString();

function targetOf(run: Pick<BackendWorkflowResult, 'target' | 'sources'>): RunTarget {
  if (run.target === 'MOCKS') return { kind: 'mocks' };
  const baseUrls: Record<string, string> = {};
  for (const s of run.sources ?? []) if (s.baseUrl) baseUrls[s.sourceName] = s.baseUrl;
  return { kind: 'implementations', baseUrls };
}

const SCHEMA_NOT_VALIDATED: StepResult['schema'] = {
  state: 'off',
  ref: '',
  message: 'Not validated: Microcks judges the step on its success criteria only (schema validation comes with a later story).'
};

function flowOf(status: StepStatus, stepId: string, isLast: boolean, message: string | undefined): StepResult['flow'] {
  if (status === 'not-run') return { kind: 'next', title: '', text: '' };
  if (status === 'passed') {
    // An "end" flow means an onSuccess end action (early end): the last step just lets the run reach its end.
    return isLast
      ? { kind: 'next', title: 'Last step', text: 'The step passed and it is the last one: the run ends with SUCCESS.' }
      : { kind: 'next', title: 'Next step', text: 'The step passed, so the run continues with the next step.' };
  }
  return {
    kind: 'stop',
    title: 'No onFailure action: the run ends here',
    text: `${message ? `${message}. ` : ''}${stepId} declares no onFailure action, so the run ends with FAILURE. The later steps are not run.`
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
  result: BackendWorkflowStepResult | undefined,
  isLast: boolean,
  startedAt: string,
  message: BackendTestMessage | undefined
): StepResult {
  const status: StepStatus = !result ? 'not-run' : result.status === 'PASSED' ? 'passed' : result.status === 'FAILED' ? 'failed' : 'not-run';
  const params: Record<string, string> = {};
  for (const p of result?.parameters ?? []) params[p.name] = stringOf(p.value);
  return {
    stepId: step.stepId,
    status,
    attempts: result?.statusCode ? [{ n: 1, httpStatus: result.statusCode, ms: result.elapsedTime, when: startedAt }] : [],
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
    schema: SCHEMA_NOT_VALIDATED,
    outputs: result?.outputs ?? {},
    flow: flowOf(status, step.stepId, isLast, result?.message ?? undefined),
    baggage: ''
  };
}

/** A full run, as the run page shows it: every step of the workflow, with the exchanges Microcks recorded. */
export async function uiRunOf(run: BackendWorkflowResult, workflow: Workflow, token?: string, withExchanges = true): Promise<WorkflowRun> {
  const startedAt = iso(run.startedDate);
  const byStep = new Map((run.steps ?? []).map((s) => [s.stepId, s]));
  const messages = await Promise.all(
    workflow.steps.map(async (st) => {
      const tc = byStep.get(st.stepId)?.testCaseId;
      if (!tc || !withExchanges) return undefined;
      return fetchBackendTestMessages(run.id, tc, token).then((m) => m[0]).catch(() => undefined);
    })
  );
  const steps = workflow.steps.map((st, i) => stepResultOf(st, byStep.get(st.stepId), i === workflow.steps.length - 1, startedAt, messages[i]));
  const running = run.status === 'IN_PROGRESS';
  const current = running ? workflow.steps.find((st) => !byStep.has(st.stepId)) : undefined;
  return {
    id: run.id,
    number: run.number ?? 0,
    documentId: run.documentId,
    workflowId: run.workflowId,
    origin: { kind: 'api' },
    target: targetOf(run),
    options: { inputs: run.inputs ?? {}, secret: run.secretRef?.name, timeoutMs: run.timeout ?? 10_000, validateSchemas: false },
    status: STATUS[run.status],
    startedAt,
    steps,
    outputs: run.outputs ?? {},
    coherence: [],
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
      schema: SCHEMA_NOT_VALIDATED,
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
    origin: { kind: 'api' },
    target: summary.target === 'MOCKS' ? { kind: 'mocks' } : { kind: 'implementations', baseUrls: {} },
    options: { inputs: {}, timeoutMs: 10_000, validateSchemas: false },
    status: STATUS[summary.status],
    startedAt,
    steps,
    outputs: {},
    coherence: []
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

/** Launches a run of a real workflow on the backend, after the same checks as the run setup form. */
export async function startRealRun(input: StartRunInput, workflow: Workflow, token?: string): Promise<StartRealResult> {
  if (input.target === 'mixed') {
    return { status: 422, error: 'Mixing mocks and implementations in one run is a proposal: Microcks runs a workflow on its mocks or on implementations.' };
  }
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
        ...(input.target === 'implementations' ? { implementations: input.baseUrls ?? {}, secretName: input.secret || undefined } : {})
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
  return { demo: false, run: await uiRunOf(run, detail.workflow, token), document: detail.document, workflow: detail.workflow, examples: [] };
}
