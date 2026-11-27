import { criteriaState } from './criteria-state.js';
import { actionLabel, criteriaText } from './describe.js';
import { resolveExpression } from './expression.js';
import type { ArazzoDocument, Step, StepResult, Workflow, WorkflowRun, SourceProtocol as Protocol } from './types.js';
import { formatDuration } from './time.js';
import { statusLabel } from './http-status.js';

/**
 * Props of the `WorkflowLoom` element wrapper. The types are written here, identical to the wrapper's
 * (`ui/components/ui/workflow-loom.tsx`), so the page does not depend on the file being copied.
 */
export type LoomStepStatus = 'passed' | 'failed' | 'not-run';
export type LoomCheckState = 'passed' | 'failed' | 'off' | 'not-evaluated';
export interface LoomSource {
  name: string;
  type: 'openapi' | 'asyncapi';
  protocol: Protocol;
  linked: boolean;
  serviceLabel?: string;
}
export type LoomAction =
  | { type: 'end'; name: string; on: 'success' | 'failure'; criteria: string; taken?: boolean; label?: string; runLabel?: string }
  | { type: 'retry'; name: string; on: 'failure'; retryAfter: number; retryLimit: number; applied?: boolean; runLabel?: string };
export interface LoomStep {
  stepId: string;
  source: string;
  operation: string;
  method?: string;
  path?: string;
  result?: string;
  dependsOn?: string[];
  status?: LoomStepStatus;
  attempts?: number;
  /** The pair (drawn when `validatesSchemas`), or a list of rows drawn as given (a session's call / data passing / contract). */
  checks?: { successCriteria: LoomCheckState; schemaValidation?: LoomCheckState } | LoomCheckRow[];
  actions?: LoomAction[];
}
export interface LoomCheckRow {
  label: string;
  state: 'pass' | 'fail' | 'skip' | 'idle';
}
export interface LoomDrop {
  to: string;
  label: string;
  idle?: boolean;
  bad?: string;
}
export interface LoomThread {
  id?: string;
  from: string;
  expression: string;
  value?: string;
  missing?: boolean;
  drops: LoomDrop[];
}
export interface LoomInput {
  name: string;
  expression?: string;
  value?: string;
}
export interface LoomOutput {
  name: string;
  value?: string;
}
export interface LoomProps {
  sources: LoomSource[];
  steps: LoomStep[];
  threads: LoomThread[];
  inputs?: LoomInput[];
  outputs?: LoomOutput[];
  outputNote?: string;
  validatesSchemas?: boolean;
  selectedStep?: string;
  lens?: string;
  tableToggle?: boolean;
  label: string;
}

/** Runtime expressions of a step that read something produced elsewhere. */
export function expressionsOf(step: Step): string[] {
  const found = new Set<string>();
  const scan = (text: string) => {
    for (const m of text.matchAll(/\$(?:inputs\.[A-Za-z0-9_-]+|steps\.[A-Za-z0-9_-]+\.outputs\.[A-Za-z0-9_-]+)/g)) found.add(m[0]);
  };
  for (const p of step.parameters) scan(p.value);
  if (step.requestBody) scan(step.requestBody);
  return [...found];
}

/** JSON pointer of the requestBody member that receives `expression` ("/price"), when the body is JSON. */
export function bodyPointer(step: Pick<Step, 'requestBody'>, expression: string): string | undefined {
  if (!step.requestBody?.includes(expression)) return undefined;
  try {
    const body = JSON.parse(step.requestBody) as Record<string, unknown>;
    const key = Object.keys(body).find((k) => String(body[k]).includes(expression));
    return key ? `/${key}` : undefined;
  } catch {
    return undefined;
  }
}

/** A value as the loom and the inspector write it: '"M"', '3.9'. */
export const formatValue = (value: unknown): string => (typeof value === 'string' ? `"${value}"` : JSON.stringify(value));

function contextOf(run: WorkflowRun | undefined) {
  return {
    inputs: run?.options.inputs,
    steps: Object.fromEntries((run?.steps ?? []).map((s) => [s.stepId, { outputs: s.outputs }])),
  };
}

/** The value of an expression in a run, or undefined. */
export function valueIn(expression: string, run: WorkflowRun | undefined): unknown {
  return run ? resolveExpression(expression, contextOf(run)) : undefined;
}

/** "patchPastry called with name=Millefeuille, expected $steps.listPastries.outputs.firstName = Eclair Cafe" (session runs). */
export function wrongValue(result: StepResult | undefined): { param: string; got: string; expression: string } | undefined {
  const m = result ? /called with ([A-Za-z0-9_-]+)=([^,]+), expected (\$\S+)/.exec(result.flow.text) : null;
  return m ? { param: m[1] as string, got: m[2] as string, expression: m[3] as string } : undefined;
}

/** Producer of an expression: '$inputs' or the stepId. */
const producerOf = (expression: string) => (expression.startsWith('$inputs.') ? '$inputs' : (/^\$steps\.([^.]+)\./.exec(expression)?.[1] ?? '$inputs'));

/** Data threads (weft): one per expression, with every place that consumes it. */
export function threadsOf(workflow: Pick<Workflow, 'steps' | 'outputs'>, run?: WorkflowRun): LoomThread[] {
  const threads: LoomThread[] = [];
  const get = (expression: string): LoomThread => {
    let t = threads.find((x) => x.expression === expression);
    if (!t) {
      t = { id: expression, from: producerOf(expression), expression, drops: [] };
      threads.push(t);
    }
    return t;
  };
  const noOutputs = run !== undefined && Object.keys(run.outputs).length === 0;
  for (const step of workflow.steps) {
    const result = run?.steps.find((r) => r.stepId === step.stepId);
    const idle = result?.status === 'not-run' ? true : undefined;
    const wrong = wrongValue(result);
    for (const p of step.parameters) {
      for (const expr of expressionsOf({ ...step, parameters: [p], requestBody: undefined })) {
        const bad = wrong && wrong.param === p.name && wrong.expression === expr ? wrong.got : undefined;
        get(expr).drops.push({ to: step.stepId, label: `${p.name} · ${p.in}`, ...(idle ? { idle } : {}), ...(bad ? { bad } : {}) });
      }
    }
    if (step.requestBody) {
      for (const expr of expressionsOf({ ...step, parameters: [] })) {
        get(expr).drops.push({ to: step.stepId, label: `${bodyPointer(step, expr) ?? 'requestBody'} · body`, ...(idle ? { idle } : {}) });
      }
    }
  }
  for (const out of workflow.outputs) {
    if (out.expression.startsWith('$')) get(out.expression).drops.push({ to: '$outputs', label: out.name, ...(noOutputs ? { idle: true } : {}) });
  }
  if (run) {
    for (const t of threads) {
      const v = valueIn(t.expression, run);
      if (v === undefined) t.missing = true;
      else t.value = formatValue(v);
    }
  }
  return threads;
}

function checksOf(result: StepResult | undefined, validate: boolean, run?: WorkflowRun): LoomStep['checks'] | undefined {
  if (!result) return undefined;
  if (run?.origin.kind === 'session') {
    // A verification judges each call: the call itself, the data it carries, and its contract.
    if (result.status === 'not-run') return [{ label: 'Call', state: 'idle' }, { label: 'Data passing', state: 'idle' }, { label: 'Contract', state: 'idle' }];
    const call = result.attempts[0]?.when.replace(/^call/, 'Call') ?? 'Call';
    return [
      { label: call, state: 'pass' },
      { label: 'Data passing', state: wrongValue(result) ? 'fail' : 'pass' },
      { label: 'Contract', state: 'pass' },
    ];
  }
  if (result.status === 'not-run') return { successCriteria: 'not-evaluated', schemaValidation: validate ? 'not-evaluated' : undefined };
  const state = criteriaState(result);
  const criteria: LoomCheckState = state === 'held' ? 'passed' : state === 'failed' ? 'failed' : 'not-evaluated';
  const schema: LoomCheckState = result.schema.state === 'pass' ? 'passed' : result.schema.state === 'fail' ? 'failed' : result.schema.state === 'off' ? 'off' : 'not-evaluated';
  return { successCriteria: criteria, schemaValidation: validate ? schema : undefined };
}

/** "$response.body#/status == 'available'" → "status == 'available'". */
export const shortCriteria = (text: string) => text.replace(/\$response\.body#\//g, '');

function actionsOf(step: Step, result: StepResult | undefined, run: WorkflowRun | undefined): LoomAction[] {
  const ran = result !== undefined && result.status !== 'not-run';
  const runLabel = run ? `#${run.number}` : undefined;
  const out: LoomAction[] = [];
  for (const a of step.onSuccess) {
    if (a.type === 'end') {
      const criteria = criteriaText(a.criteria);
      out.push({ type: 'end', name: a.name, on: 'success', criteria, label: shortCriteria(criteria), taken: ran ? result.flow.kind === 'end' : undefined, runLabel: ran ? runLabel : undefined });
    }
  }
  for (const a of step.onFailure) {
    if (a.type === 'retry') out.push({ type: 'retry', name: a.name, on: 'failure', retryAfter: a.retryAfter, retryLimit: a.retryLimit, applied: ran ? result.attempts.length > 1 : undefined, runLabel: ran ? runLabel : undefined });
    else {
      const criteria = criteriaText(a.criteria);
      out.push({ type: 'end', name: a.name, on: 'failure', criteria, label: shortCriteria(criteria), taken: ran ? result.flow.kind === 'stop' : undefined, runLabel: ran ? runLabel : undefined });
    }
  }
  return out;
}

/** One-line result of a step in a run: "200 · 41 ms", "200 · 3 attempts · 185 ms", "call #4 · 200", "Not run". */
export function stepResultLine(result: StepResult | undefined): string | undefined {
  if (!result) return undefined;
  if (result.status === 'not-run') return 'Not run';
  const last = result.attempts[result.attempts.length - 1];
  if (!last) return undefined;
  if (last.when.startsWith('call #')) return `${last.when} · ${statusLabel(last.httpStatus)}`;
  const ms = result.attempts.reduce((sum, a) => sum + a.ms, 0);
  return result.attempts.length > 1 ? `${statusLabel(last.httpStatus)} · ${result.attempts.length} attempts · ${formatDuration(ms)}` : `${statusLabel(last.httpStatus)} · ${formatDuration(ms)}`;
}

/** Everything the Loom needs for a workflow and, optionally, the results of one run. */
export function buildLoomProps(
  workflow: Workflow,
  document: ArazzoDocument,
  run: WorkflowRun | undefined,
  selectedStep: string | undefined,
  serviceLabel: (serviceId: string) => string | undefined = () => undefined,
): LoomProps {
  const validate = run?.options.validateSchemas === true;
  const used = [...new Set(workflow.steps.map((s) => s.sourceName))];
  const sources: LoomSource[] = used.map((name) => {
    const src = document.sources.find((s) => s.name === name);
    const linked = src?.link.status === 'linked';
    const protocol: Protocol = src?.link.status === 'linked' ? src.link.protocol : src?.type === 'asyncapi' ? 'EVENT' : 'REST';
    const label = src?.link.status === 'linked' ? serviceLabel(src.link.serviceId) : undefined;
    const type = src?.type ?? 'openapi';
    return { name, type, protocol, linked, ...(label ? { serviceLabel: label } : {}) };
  });
  const steps: LoomStep[] = workflow.steps.map((step) => {
    const result = run?.steps.find((r) => r.stepId === step.stepId);
    const line = stepResultLine(result);
    return {
      stepId: step.stepId,
      source: step.sourceName,
      operation: `${step.method} ${step.path}`,
      method: step.method,
      path: step.path,
      ...(line ? { result: line } : {}),
      dependsOn: step.dependsOn,
      status: result?.status,
      attempts: result && result.attempts.length > 1 ? result.attempts.length : undefined,
      checks: checksOf(result, validate, run),
      actions: actionsOf(step, result, run),
    };
  });
  const hasOutputs = run !== undefined && Object.keys(run.outputs).length > 0;
  return {
    sources,
    steps,
    threads: threadsOf(workflow, run),
    inputs: workflow.inputs.map((i) => {
      const v = valueIn(`$inputs.${i.name}`, run);
      return { name: i.name, ...(v !== undefined ? { value: formatValue(v) } : {}) };
    }),
    outputs: workflow.outputs.map((o) => ({ name: o.name, value: hasOutputs && run.outputs[o.name] !== undefined ? formatValue(run.outputs[o.name]) : '—' })),
    ...(run && !hasOutputs && workflow.outputs.length ? { outputNote: `No outputs: #${run.number} ${run.status === 'FAILURE' ? 'failed' : 'produced none'}.` } : {}),
    validatesSchemas: validate,
    selectedStep,
    tableToggle: false,
    label: `Loom of ${workflow.workflowId}${run ? `, results of run #${run.number}` : ', definition only'}`,
  };
}

/** The effective dependencies of each step, as the Loom lays them out (sequential unless some step declares dependsOn). */
export function effectiveDependencies(steps: readonly Pick<Step, 'stepId' | 'dependsOn'>[]): string[][] {
  const declared = steps.some((s) => s.dependsOn && s.dependsOn.length > 0);
  return steps.map((s, i) => (declared ? (s.dependsOn ?? []) : i > 0 ? [(steps[i - 1] as Pick<Step, 'stepId'>).stepId] : []));
}

export interface LensUse {
  /** "2 · getPastry" or "workflow output" */
  who: string;
  /** "parameter name, in: path", "requestBody replacement /price", "outputs.pastryName" */
  what: string;
  value: string;
  /** The value is wrong (session) or absent: drawn in danger / muted. */
  tone?: 'bad' | 'idle';
}

export interface Lens {
  expression: string;
  /** '"Eclair Cafe"', or "— (not produced: patchPastry failed)". */
  value: string;
  missing: boolean;
  warn?: string;
  /** "workflow input size", "listPastries · $response.body#/0/name" */
  producedBy: string;
  uses: LensUse[];
}

/** Where a runtime expression is produced and consumed, with its value in the selected run. */
export function lensOf(expression: string, workflow: Pick<Workflow, 'steps' | 'outputs'>, run: WorkflowRun | undefined): Lens {
  const raw = valueIn(expression, run);
  const producer = producerOf(expression);
  const m = /^\$steps\.([^.]+)\.outputs\.(.+)$/.exec(expression);
  const producerStep = m ? workflow.steps.find((s) => s.stepId === m[1]) : undefined;
  const producedBy =
    producer === '$inputs'
      ? `workflow input ${expression.slice('$inputs.'.length)}`
      : `${producer} · ${producerStep?.outputs.find((o) => o.name === m?.[2])?.expression ?? `outputs.${m?.[2]}`}`;
  const producerResult = run?.steps.find((r) => r.stepId === producer);
  const why = producerResult?.status === 'failed' ? `not produced: ${producer} failed${run?.origin.kind === 'session' ? ' the verification' : ''}` : producerResult?.status === 'not-run' ? `not produced: ${producer} not run` : 'not resolved';
  const missing = run !== undefined && raw === undefined;
  const shown = raw === undefined ? '' : formatValue(raw);
  const noOutputs = run !== undefined && Object.keys(run.outputs).length === 0;
  const thread = threadsOf(workflow, run).find((t) => t.expression === expression);
  let warn: string | undefined;
  const uses: LensUse[] = (thread?.drops ?? []).map((d) => {
    const index = workflow.steps.findIndex((s) => s.stepId === d.to);
    const step = workflow.steps[index];
    const who = d.to === '$outputs' ? 'workflow output' : `${index + 1} · ${d.to}`;
    const [name, where] = d.label.split(' · ');
    const what = d.to === '$outputs' ? `outputs.${d.label}` : where === 'body' ? `requestBody replacement ${name}` : `parameter ${name}, in: ${where}`;
    if (d.bad) {
      const app = run?.origin.kind === 'session' ? run.origin.app : 'the caller';
      warn = `${app} sent ${name}=${d.bad} to ${step?.stepId} instead of this value.`;
      return { who, what, value: `${d.bad} (expected ${raw === undefined ? '—' : String(raw)})`, tone: 'bad' as const };
    }
    if (d.idle) return { who, what, value: d.to === '$outputs' && noOutputs ? `not produced in #${run?.number}` : `not run in #${run?.number}`, tone: 'idle' as const };
    return { who, what, value: missing ? '—' : run ? shown : 'no run selected' };
  });
  return { expression, value: !run ? 'no run selected' : missing ? `— (${why})` : shown, missing, ...(warn ? { warn } : {}), producedBy, uses };
}

/** Run picker parts: "#14" and its target "Implementations", "Session", "Mocks · example", "Mock URL". */
export function runPick(run: Pick<WorkflowRun, 'number' | 'origin' | 'target' | 'options'>): { label: string; target: string } {
  const target =
    run.origin.kind === 'mock-url'
      ? 'Mock URL'
      : run.origin.kind === 'session'
        ? 'Session'
        : run.target.kind === 'implementations'
          ? 'Implementations'
          : run.target.kind === 'mixed'
            ? 'Mixed'
            : run.options.example
              ? 'Mocks · example'
              : 'Mocks';
  return { label: `#${run.number}`, target };
}

/** Label of a run in pickers: "#14 · Implementations". */
export function runPickLabel(run: Pick<WorkflowRun, 'number' | 'origin' | 'target' | 'options'>): string {
  const { label, target } = runPick(run);
  return `${label} · ${target}`;
}

export { actionLabel };
