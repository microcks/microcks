import { resolveExpression, type EvaluationContext } from './expression.js';
import type { StepResult, StepStatus, WorkflowRun } from './types.js';
import type { Action, Criterion, Step, WorkflowExample } from './types.js';

/** "$statusCode == 200" or "status is …"; several criteria are joined with " and ". */
export function criteriaText(criteria: readonly Criterion[] | undefined): string {
  if (!criteria || criteria.length === 0) return '';
  return criteria.map((c) => c.condition).join(' and ');
}

/** Short label of an action, as the Loom writes it: "retry ×2 · 1 s", "end if $response.body#/status == 'available'". */
export function actionLabel(action: Action): string {
  if (action.type === 'retry') return `retry ×${action.retryLimit} · ${action.retryAfter} s`;
  const when = criteriaText(action.criteria);
  return when ? `end if ${when}` : 'end';
}

/** An action in words (retry and end only, there is no goto). */
export function describeAction(action: Action): string {
  if (action.type === 'retry') {
    const times = action.retryLimit === 1 ? 'once' : `up to ${action.retryLimit} times`;
    return `Retry ${times}, waiting ${action.retryAfter} s between attempts`;
  }
  const when = criteriaText(action.criteria);
  return when ? `End the workflow when ${when}` : 'End the workflow';
}

/** Where an action sits: `onSuccess` or `onFailure`. */
export function actionsOf(step: Pick<Step, 'onSuccess' | 'onFailure'>): { when: 'onSuccess' | 'onFailure'; action: Action }[] {
  return [
    ...step.onSuccess.map((action) => ({ when: 'onSuccess' as const, action })),
    ...step.onFailure.map((action) => ({ when: 'onFailure' as const, action })),
  ];
}

const WORD: Record<StepStatus, string> = { passed: 'passed', failed: 'failed', 'not-run': 'not run' };

/** Text alternative of the mini step strip: "4 steps: passed, passed, failed, not run". */
export function stepStripAlt(statuses: readonly StepStatus[]): string {
  if (statuses.length === 0) return 'No step';
  return `${statuses.length} ${statuses.length === 1 ? 'step' : 'steps'}: ${statuses.map((s) => WORD[s]).join(', ')}`;
}

/** The step where a run stopped (end action or failure), if it did. */
export function stoppedStep(run: Pick<WorkflowRun, 'steps'>): StepResult | undefined {
  return run.steps.find((s) => s.status !== 'not-run' && s.flow.kind !== 'next');
}

/** Passed / failed as shown in the lists; a RUNNING run counts as neither. */
export function runOutcome(run: Pick<WorkflowRun, 'status'>): 'passed' | 'failed' | 'running' {
  return run.status === 'SUCCESS' ? 'passed' : run.status === 'FAILURE' ? 'failed' : 'running';
}

/** Headline of a run for the summary callout: "Run #14 failed" + "Stops at patchPastry · schema validation, 3 attempts". */
export function runHeadline(run: WorkflowRun): { title: string; detail: string } {
  if (run.status === 'RUNNING') return { title: `Run #${run.number} is running`, detail: '' };
  const stopped = stoppedStep(run);
  if (run.status === 'SUCCESS') {
    return {
      title: `Run #${run.number} passed`,
      detail: stopped ? `Ended early at ${stopped.stepId}` : 'Every step passed',
    };
  }
  if (!stopped) return { title: `Run #${run.number} failed`, detail: '' };
  const parts = [`Stops at ${stopped.stepId}`];
  if (stopped.schema.state === 'fail') parts.push('schema validation');
  const attempts = stopped.attempts.length;
  return { title: `Run #${run.number} failed`, detail: attempts > 1 ? `${parts.join(' · ')}, ${attempts} attempts` : parts.join(' · ') };
}

/** Why the run ended, in a sentence ("Step 3, patchPastry, failed after 3 attempts: …"). */
export function verdictExplanation(run: WorkflowRun): string {
  if (run.status === 'RUNNING') return '';
  const stopped = stoppedStep(run);
  if (run.status === 'SUCCESS') {
    if (stopped) return stopped.flow.text;
    const validated = run.options.validateSchemas && run.target.kind !== 'mocks';
    return `Every step passed its success criteria${validated ? ' and schema validation' : ''}.${
      run.target.kind !== 'mocks' && !run.options.validateSchemas
        ? ' Schema validation was off: the responses were not checked against their OpenAPI schema.'
        : ''
    }`;
  }
  if (!stopped) return 'The run failed.';
  const index = run.steps.findIndex((s) => s.stepId === stopped.stepId) + 1;
  const attempts = stopped.attempts.length;
  const head = `Step ${index}, ${stopped.stepId}, failed${attempts > 1 ? ` after ${attempts} attempts` : ''}`;
  const unheld = stopped.criteria.find((c) => !c.held);
  const reason =
    stopped.schema.state === 'fail'
      ? stopped.schema.message
      : unheld
        ? `Success criterion ${unheld.condition} did not hold: ${unheld.evaluation}`
        : stopped.flow.title;
  return `${head}: ${reason}. The workflow stopped there and produced no outputs.`;
}

/** Counts used by the summary: "2 passed · 1 failed · 1 not run" (a count of zero is left out, except passed). */
export function stepsTally(run: Pick<WorkflowRun, 'steps'>): string {
  const count = (status: StepStatus) => run.steps.filter((s) => s.status === status).length;
  const failed = count('failed');
  const notRun = count('not-run');
  return [`${count('passed')} passed`, ...(failed ? [`${failed} failed`] : []), ...(notRun ? [`${notRun} not run`] : [])].join(' · ');
}

/** What the runner spends around each call (resolving expressions, checking criteria), in ms. */
export const CALL_OVERHEAD_MS = 12;

/** Time one attempt takes on the run clock: the retryAfter wait before it, the call and the overhead. */
const attemptSpan = (a: { ms: number; waitMs?: number }) => (a.waitMs ?? 0) + a.ms + CALL_OVERHEAD_MS;

/** Total number of calls (all attempts of all steps) and the run duration (calls, retry waits and overhead). */
export function callTotals(run: Pick<WorkflowRun, 'steps'>): { calls: number; ms: number } {
  let calls = 0;
  let ms = 0;
  for (const s of run.steps) {
    calls += s.attempts.length;
    for (const a of s.attempts) ms += attemptSpan(a);
  }
  return { calls, ms };
}

/** When each step starts, in ms from the start of the run ("at +94 ms"). A step that did not run starts where the run stopped. */
export function stepOffsets(run: Pick<WorkflowRun, 'steps'>): number[] {
  let clock = 0;
  return run.steps.map((s) => {
    const start = clock;
    for (const a of s.attempts) clock += attemptSpan(a);
    return start;
  });
}

/** "Stops at patchPastry", "Stops at getPastry · ended early" or "Runs to its end". */
export function endLabel(run: Pick<WorkflowRun, 'steps' | 'status'>): string {
  const stopped = stoppedStep(run);
  if (!stopped) return 'Runs to its end';
  return `Stops at ${stopped.stepId}${run.status === 'SUCCESS' ? ' · ended early' : ''}`;
}

/* ------------------------------------------------------------------------------------- hand-overs */

/** A value one step hands over to the next: "firstName = "Eclair Cafe" → path name of patchPastry". */
export interface Handover {
  /** Name of the value: the last segment of its runtime expression (`firstName`, `size`). */
  name: string;
  value: unknown;
  /** Where it goes: "query size of listPastries", "body /price of patchPastry". */
  target: string;
  expression: string;
}

const lastSegment = (expression: string) => expression.split('.').pop() ?? expression;

/** The runtime values a step receives from the inputs and the earlier steps, resolved in `ctx`. */
export function handoversOf(step: Pick<Step, 'stepId' | 'parameters' | 'requestBody'>, ctx: EvaluationContext): Handover[] {
  const fromParams = step.parameters
    .filter((p) => p.value.startsWith('$'))
    .map((p) => ({ name: lastSegment(p.value), value: resolveExpression(p.value, ctx), target: `${p.in} ${p.name} of ${step.stepId}`, expression: p.value }));
  const fromBody = [...(step.requestBody ?? '').matchAll(/"([^"]+)"\s*:\s*"\{(\$[^}]+)\}"/g)].map(([, key = '', expression = '']) => ({
    name: lastSegment(expression),
    value: resolveExpression(expression, ctx),
    target: `body /${key} of ${step.stepId}`,
    expression,
  }));
  return [...fromParams, ...fromBody];
}

/** Evaluation context of a finished run: its inputs and the outputs of the steps it reached. */
export function runContext(run: Pick<WorkflowRun, 'options' | 'steps'>): EvaluationContext {
  return { inputs: run.options.inputs, steps: Object.fromEntries(run.steps.map((s) => [s.stepId, { outputs: s.outputs }])) };
}

/* -------------------------------------------------------------------------------- example judging */

export interface ExampleExpectation {
  kind: 'Step' | 'End' | 'Output';
  expected: string;
  got: string;
  met: boolean;
}

const json = (value: unknown) => JSON.stringify(value);

/**
 * Judges a finished run on the example it played: each expected step status (and step outputs), where the
 * run ends, and the workflow outputs. `unmet` lists the expectations that do not hold, in words.
 */
export function judgeExample(
  run: Pick<WorkflowRun, 'steps' | 'status' | 'outputs'>,
  example: Pick<WorkflowExample, 'steps' | 'stopsAt' | 'outputs'>,
): { rows: ExampleExpectation[]; unmet: string[]; matches: boolean } {
  const rows: ExampleExpectation[] = [];
  const unmet: string[] = [];
  for (const e of example.steps) {
    const step = run.steps.find((s) => s.stepId === e.stepId);
    const ran = step !== undefined && step.status !== 'not-run';
    const code = ran ? step.attempts[step.attempts.length - 1]?.httpStatus : undefined;
    let met = ran && code === e.status;
    if (!ran) unmet.push(`Step ${e.stepId}: expected to run but was not run`);
    else if (code !== e.status) unmet.push(`Step ${e.stepId}: expecting ${e.status} but got ${code ? code : 'no response'}`);
    const outs = Object.entries(e.outputs ?? {});
    const seen: string[] = [];
    if (ran) {
      for (const [k, v] of outs) {
        const got = step.outputs[k];
        seen.push(`${k} = ${got === undefined ? 'absent' : json(got)}`);
        if (got === undefined || json(got) !== json(v)) {
          met = false;
          unmet.push(`Step ${e.stepId} output ${k}: expected ${json(v)} but was ${got === undefined ? 'absent' : json(got)}`);
        }
      }
    }
    const expectedOuts = outs.map(([k, v]) => `${k} = ${json(v)}`).join(' · ');
    rows.push({
      kind: 'Step',
      expected: `${e.stepId} ${e.status}${expectedOuts ? ` · ${expectedOuts}` : ''}`,
      got: ran ? `${code}${seen.length ? ` · ${seen.join(' · ')}` : ''}` : 'Not run',
      met,
    });
  }
  const stoppedAt = stoppedStep(run)?.stepId;
  const endMet = example.stopsAt ? stoppedAt === example.stopsAt : !stoppedAt;
  if (!endMet) {
    unmet.push(
      example.stopsAt
        ? `Expected to stop at ${example.stopsAt} but ${stoppedAt ? `stopped at ${stoppedAt}` : 'the workflow ran to its end'}`
        : `Expected to run to its end but stopped at ${stoppedAt}`,
    );
  }
  rows.push({ kind: 'End', expected: example.stopsAt ? `Stops at ${example.stopsAt}` : 'Runs to its end', got: endLabel(run), met: endMet });
  for (const [k, v] of Object.entries(example.outputs)) {
    const got = run.outputs[k];
    const met = got !== undefined && json(got) === json(v);
    if (!met) unmet.push(`Output ${k}: expected ${json(v)} but was ${got === undefined ? 'absent' : json(got)}`);
    rows.push({ kind: 'Output', expected: `${k} = ${json(v)}`, got: got === undefined ? 'absent' : `${k} = ${json(got)}`, met });
  }
  return { rows, unmet, matches: unmet.length === 0 };
}
