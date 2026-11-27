import { baggage } from '#lib/workflows/baggage.js';
import { criteriaText } from '#lib/workflows/describe.js';
import { allCriteriaHold, evaluateCriterion, resolveExpression, type EvaluationContext } from '#lib/workflows/expression.js';
import type {
  Action,
  MockCoherenceIssue,
  RunOptions,
  RunOrigin,
  RunTarget,
  Step,
  StepAttempt,
  StepResult,
  StepStatus,
  Workflow,
  WorkflowRun
} from '#lib/workflows/types.js';

/*
 * PLACEHOLDER engine: a port of the Deep Ink domain simulation (`execution.ts`, `simulation.ts`). Microcks has no
 * Arazzo runner, so a workflow is played here, in the BFF, against the simulated world of
 * lib/placeholders/runs-world.ts. The semantics are the documented ones (success criteria, optional schema
 * validation, onFailure retry, onSuccess end), but nothing here calls a mock or an implementation.
 */

export interface StepResponse {
  statusCode: number;
  body?: unknown;
  headers?: Record<string, string>;
  ms: number;
  schema?: 'pass' | 'fail';
}

export interface SimulatedResponse extends StepResponse {
  badKey?: string;
  badNote?: string;
  schemaRef?: string;
  schemaMessage?: string;
  missingExample?: { description: string; hint: string };
}

export type SimulatedResponder = (step: Step, attempt: number, ctx: EvaluationContext) => SimulatedResponse;

type Dependent = Pick<Step, 'stepId' | 'dependsOn'>;

/** Parallel execution layers (same rule as the Loom layout); without any `dependsOn`, every step is its own layer. */
export function executionLayers(steps: readonly Dependent[]): string[][] {
  if (steps.length === 0) return [];
  if (!steps.some((s) => s.dependsOn && s.dependsOn.length > 0)) return steps.map((s) => [s.stepId]);
  const byId = new Map(steps.map((s) => [s.stepId, s]));
  const depth = new Map<string, number>();
  const visiting = new Set<string>();
  const depthOf = (id: string): number => {
    const known = depth.get(id);
    if (known !== undefined) return known;
    if (visiting.has(id)) throw new Error(`Cycle in step dependencies at "${id}"`);
    visiting.add(id);
    const step = byId.get(id);
    if (!step) throw new Error(`Unknown step "${id}" in dependsOn`);
    const deps = step.dependsOn ?? [];
    const d = deps.length === 0 ? 0 : 1 + Math.max(...deps.map(depthOf));
    visiting.delete(id);
    depth.set(id, d);
    return d;
  };
  const layers: string[][] = [];
  for (const s of steps) (layers[depthOf(s.stepId)] ??= []).push(s.stepId);
  return layers;
}

type StepFlow =
  | { kind: 'next' }
  | { kind: 'end'; outcome: 'success' | 'failure'; reason: 'end-action' | 'no-failure-action' | 'retry-limit-reached'; actionName?: string }
  | { kind: 'retry'; actionName: string; afterSeconds: number; nextAttempt: number };

function applies(action: Action, ctx: EvaluationContext | undefined): boolean {
  if (!action.criteria || action.criteria.length === 0) return true;
  return ctx !== undefined && allCriteriaHold(action.criteria, ctx);
}

/** What happens after an attempt: retry, end or next. `retryLimit` counts RETRIES. */
function nextAfterStep(step: Pick<Step, 'onSuccess' | 'onFailure'>, result: { passed: boolean; attempt: number; context?: EvaluationContext }): StepFlow {
  if (result.passed) {
    const end = step.onSuccess.find((a) => a.type === 'end' && applies(a, result.context));
    return end ? { kind: 'end', outcome: 'success', reason: 'end-action', actionName: end.name } : { kind: 'next' };
  }
  const action = step.onFailure.find((a) => applies(a, result.context));
  if (!action) return { kind: 'end', outcome: 'failure', reason: 'no-failure-action' };
  if (action.type === 'end') return { kind: 'end', outcome: 'failure', reason: 'end-action', actionName: action.name };
  if (result.attempt <= action.retryLimit) return { kind: 'retry', actionName: action.name, afterSeconds: action.retryAfter, nextAttempt: result.attempt + 1 };
  return { kind: 'end', outcome: 'failure', reason: 'retry-limit-reached', actionName: action.name };
}

interface PlayedAttempt {
  n: number;
  statusCode: number;
  ms: number;
  criteriaHeld: boolean;
  schema: 'pass' | 'fail' | 'off';
  passed: boolean;
}

interface PlayedStep {
  stepId: string;
  status: StepStatus;
  attempts: PlayedAttempt[];
  flow: StepFlow;
  outputs: Record<string, unknown>;
}

interface PlayedRun {
  status: 'SUCCESS' | 'FAILURE';
  steps: PlayedStep[];
  outputs: Record<string, unknown>;
}

function playWorkflow(
  workflow: Pick<Workflow, 'steps' | 'outputs'>,
  respond: (step: Step, attempt: number, ctx: EvaluationContext) => StepResponse,
  options: { inputs: Record<string, unknown>; validateSchemas: boolean }
): PlayedRun {
  const order = executionLayers(workflow.steps).flat();
  const byId = new Map(workflow.steps.map((s) => [s.stepId, s]));
  const ctxSteps: Record<string, { outputs: Record<string, unknown> }> = {};
  const played: PlayedStep[] = [];
  let status: PlayedRun['status'] = 'SUCCESS';
  let stopped = false;

  for (const id of order) {
    const step = byId.get(id) as Step;
    if (stopped) {
      played.push({ stepId: id, status: 'not-run', attempts: [], flow: { kind: 'next' }, outputs: {} });
      continue;
    }
    const attempts: PlayedAttempt[] = [];
    let attempt = 1;
    let flow: StepFlow = { kind: 'next' };
    let outputs: Record<string, unknown> = {};
    let passed = false;
    for (;;) {
      const base: EvaluationContext = { inputs: options.inputs, steps: ctxSteps };
      const response = respond(step, attempt, base);
      const ctx: EvaluationContext = { ...base, statusCode: response.statusCode, body: response.body, headers: response.headers };
      const criteriaHeld = allCriteriaHold(step.successCriteria, ctx);
      const schema = options.validateSchemas && response.schema ? response.schema : 'off';
      passed = criteriaHeld && schema !== 'fail';
      attempts.push({ n: attempt, statusCode: response.statusCode, ms: response.ms, criteriaHeld, schema, passed });
      flow = nextAfterStep(step, { passed, attempt, context: ctx });
      if (passed) {
        outputs = Object.fromEntries(step.outputs.map((o) => [o.name, resolveExpression(o.expression, ctx)]));
        ctxSteps[step.stepId] = { outputs };
      }
      if (flow.kind !== 'retry') break;
      attempt = flow.nextAttempt;
    }
    played.push({ stepId: id, status: passed ? 'passed' : 'failed', attempts, flow, outputs });
    if (flow.kind === 'end') {
      stopped = true;
      status = flow.outcome === 'success' ? 'SUCCESS' : 'FAILURE';
    }
  }

  const outputs: Record<string, unknown> = {};
  if (status === 'SUCCESS') {
    for (const o of workflow.outputs) {
      const value = resolveExpression(o.expression, { inputs: options.inputs, steps: ctxSteps });
      if (value !== undefined) outputs[o.name] = value;
    }
  }
  return { status, steps: played, outputs };
}

const REASON: Record<number, string> = {
  200: 'OK', 201: 'Created', 204: 'No Content', 400: 'Bad Request', 401: 'Unauthorized', 404: 'Not Found',
  409: 'Conflict', 500: 'Internal Server Error', 502: 'Bad Gateway', 504: 'Gateway Timeout'
};

const show = (value: unknown): string => (typeof value === 'string' ? value : JSON.stringify(value));

/** Replaces `"{$inputs.x}"` in a request body template with the JSON value of the expression. */
function renderBody(template: string, ctx: EvaluationContext): string {
  return template.replace(/"\{(\$[^}]+)\}"/g, (_m, expr: string) => {
    const value = resolveExpression(expr, ctx);
    return value === undefined ? 'null' : JSON.stringify(value);
  });
}

function requestOf(step: Step, ctx: EvaluationContext, baseUrl: string): NonNullable<StepResult['request']> {
  const params: Record<string, string> = {};
  let path = step.path;
  const query: string[] = [];
  for (const p of step.parameters) {
    const value = resolveExpression(p.value, ctx);
    const text = value === undefined ? '' : show(value);
    params[p.name] = text;
    if (p.in === 'path') path = path.replace(`{${p.name}}`, encodeURIComponent(text));
    else if (p.in === 'query') query.push(`${encodeURIComponent(p.name)}=${encodeURIComponent(text)}`);
  }
  return {
    method: step.method,
    url: `${baseUrl}${path}${query.length ? `?${query.join('&')}` : ''}`,
    params,
    body: step.requestBody ? renderBody(step.requestBody, ctx) : undefined
  };
}

function linesOf(response: SimulatedResponse): NonNullable<StepResult['response']>['lines'] {
  if (response.body === undefined || response.body === '') return [];
  return JSON.stringify(response.body, null, 2)
    .split('\n')
    .map((text) => {
      const bad = response.badKey !== undefined && text.trimStart().startsWith(`"${response.badKey}":`);
      return bad ? { text, bad: true, note: response.badNote ?? 'schema mismatch' } : { text };
    });
}

function flowOf(step: Step, played: PlayedStep, retryAfter: number | undefined): StepResult['flow'] {
  const attempts = played.attempts.length;
  const flow = played.flow;
  if (played.status === 'not-run') return { kind: 'next', title: '', text: '' };
  if (flow.kind === 'next') return { kind: 'next', title: 'Next step', text: 'The step passed, so the run continues with the next step.' };
  if (flow.kind === 'end' && flow.outcome === 'success') {
    const action = step.onSuccess.find((a) => a.name === flow.actionName);
    const when = criteriaText(action?.criteria);
    return {
      kind: 'end',
      title: 'onSuccess end applies: the run ends here',
      text: `${when ? `Its criterion ${when} holds` : 'The end action applies'}: nothing more to do. The run ends with SUCCESS; the later steps are not run.`
    };
  }
  if (flow.kind === 'end' && flow.reason === 'retry-limit-reached') {
    return {
      kind: 'stop',
      title: `Failed after ${attempts} attempts: the run ends here`,
      text: `Each attempt failed, so onFailure retry applied ${attempts - 1} ${attempts - 1 === 1 ? 'time' : 'times'} (retryAfter ${retryAfter ?? 0} s, retryLimit ${attempts - 1}). After attempt ${attempts} the retry limit is reached: the run ends with FAILURE and the later steps are not run.`
    };
  }
  return {
    kind: 'stop',
    title: 'No onFailure action: the run ends here',
    text: `${step.stepId} declares no onFailure action, so the run ends with FAILURE. The later steps are not run.`
  };
}

export interface SimulateRunInput {
  runId: string;
  number: number;
  workflow: Pick<Workflow, 'workflowId' | 'documentId' | 'steps' | 'outputs'>;
  origin: RunOrigin;
  target: RunTarget;
  options: RunOptions;
  startedAt: string;
  respond: SimulatedResponder;
  baseUrl: (sourceName: string) => string;
}

/** Plays a workflow and turns the outcome into a full run: requests, responses, criteria, schema, attempts, flow notes. */
export function simulateRun(input: SimulateRunInput): WorkflowRun {
  const { workflow, options, runId } = input;
  const recorded = new Map<string, { response: SimulatedResponse; ctx: EvaluationContext }>();
  const key = (stepId: string, attempt: number) => `${stepId}#${attempt}`;

  const played = playWorkflow(
    workflow,
    (step, attempt, ctx) => {
      const response = input.respond(step, attempt, ctx);
      recorded.set(key(step.stepId, attempt), { response, ctx });
      return response;
    },
    { inputs: options.inputs, validateSchemas: options.validateSchemas }
  );
  const byId = new Map(played.steps.map((p) => [p.stepId, p]));

  const coherence: MockCoherenceIssue[] = [];
  const steps: StepResult[] = workflow.steps.map((step) => {
    const p = byId.get(step.stepId) as PlayedStep;
    const retry = step.onFailure.find((a) => a.type === 'retry');
    const retryAfter = retry && retry.type === 'retry' ? retry.retryAfter : undefined;
    const attempts: StepAttempt[] = p.attempts.map((a) => ({
      n: a.n,
      httpStatus: a.statusCode,
      ms: a.ms,
      when: a.n === 1 ? 'first call' : `after ${retryAfter ?? 0} s`,
      ...(a.n > 1 ? { waitMs: (retryAfter ?? 0) * 1000 } : {})
    }));
    if (p.status === 'not-run' || attempts.length === 0) {
      return {
        stepId: step.stepId,
        status: 'not-run',
        attempts: [],
        criteria: [],
        schema: { state: 'off', ref: '', message: 'Not run.' },
        outputs: {},
        flow: { kind: 'next', title: '', text: '' },
        baggage: baggage(runId, workflow.workflowId, step.stepId, 1)
      };
    }
    const last = recorded.get(key(step.stepId, p.attempts.length)) as { response: SimulatedResponse; ctx: EvaluationContext };
    const { response } = last;
    const evalCtx: EvaluationContext = { ...last.ctx, statusCode: response.statusCode, body: response.body, headers: response.headers };
    const lastAttempt = p.attempts[p.attempts.length - 1] as PlayedAttempt;
    const schema: StepResult['schema'] = !options.validateSchemas
      ? { state: 'off', ref: '', message: 'Off for this run: the step is judged on its success criteria only.' }
      : response.schema === 'fail'
        ? {
            state: 'fail',
            ref: response.schemaRef ?? `${step.operationId} · ${response.statusCode} · application/json`,
            message: response.schemaMessage ?? 'The response does not conform to its schema.'
          }
        : response.schema === 'pass'
          ? { state: 'pass', ref: `${step.operationId} · ${response.statusCode} · application/json`, message: 'The response conforms to its schema.' }
          : { state: 'na', ref: '', message: 'No schema to validate this response against.' };
    if (response.missingExample && !lastAttempt.passed) {
      coherence.push({ stepId: step.stepId, operationId: step.operationId, missing: response.missingExample.description, copilotHint: response.missingExample.hint });
    }
    return {
      stepId: step.stepId,
      status: p.status,
      attempts,
      request: requestOf(step, last.ctx, input.baseUrl(step.sourceName)),
      response: {
        meta: `${response.statusCode} ${REASON[response.statusCode] ?? ''}${response.body === undefined ? '' : ' · application/json'}`.trim(),
        lines: linesOf(response)
      },
      criteria: step.successCriteria.map((c) => {
        const result = evaluateCriterion(c, evalCtx);
        return { condition: c.condition, kind: result.kind, evaluation: result.evaluation, held: result.held };
      }),
      schema,
      outputs: p.outputs,
      flow: flowOf(step, p, retryAfter),
      baggage: baggage(runId, workflow.workflowId, step.stepId, attempts.length)
    };
  });

  return {
    id: runId,
    number: input.number,
    documentId: workflow.documentId,
    workflowId: workflow.workflowId,
    origin: input.origin,
    target: input.target,
    options,
    status: played.status,
    startedAt: input.startedAt,
    steps,
    outputs: played.outputs,
    coherence
  };
}
