import { stoppedStep } from './describe.js';
import type { Session, Step, StepResult, Workflow, WorkflowRun } from './types.js';
import { formatDuration } from './time.js';
import { valueIn, wrongValue } from './loom-model.js';

/** Where a run's results come from, in words: "Implementations", "Session", "Microcks mocks", "Mock URL". */
export function originWord(run: Pick<WorkflowRun, 'origin' | 'target'>): string {
  if (run.origin.kind === 'session') return 'Session';
  if (run.origin.kind === 'mock-url') return 'Mock URL';
  return run.target.kind === 'implementations' ? 'Implementations' : run.target.kind === 'mixed' ? 'Mixed targets' : 'Microcks mocks';
}

/** "run #14" or "verification #13" (a session run is a verification). */
export const runNoun = (run: Pick<WorkflowRun, 'origin' | 'number'>) => `${run.origin.kind === 'session' ? 'verification' : 'run'} #${run.number}`;

export interface RunSummary {
  tone: 'success' | 'danger' | 'info';
  title: string;
  text: string;
  end: string;
}

/** The run summary bar of the Loom: "Run #14 failed" · how and where · "Stops at patchPastry". */
export function runSummary(run: WorkflowRun, workflow: Workflow, when: string, sessions: readonly Session[] = []): RunSummary {
  const stopped = stoppedStep(run);
  const sources = [...new Set(workflow.steps.map((s) => s.sourceName))];
  const session = run.origin.kind === 'session' ? sessions.find((s) => s.id === (run.origin as { sessionId: string }).sessionId) : undefined;
  const tone = run.status === 'SUCCESS' ? 'success' : run.status === 'FAILURE' ? 'danger' : 'info';
  const word = run.status === 'SUCCESS' ? 'passed' : run.status === 'FAILURE' ? 'failed' : 'is running';
  const title = `${run.origin.kind === 'session' ? 'Verification' : 'Run'} #${run.number} ${word}`;
  let text: string;
  if (run.origin.kind === 'session') {
    text = `Session ${run.origin.sessionId} · calls of ${run.origin.app} to the Microcks mocks · ${when}`;
  } else if (run.target.kind === 'implementations') {
    const urls = sources.map((s) => `${s} → ${(run.target as { baseUrls: Record<string, string> }).baseUrls[s] ?? '?'}`).join(', ');
    text = `Implementations · ${urls} · schema validation ${run.options.validateSchemas ? 'on' : 'off'} · ${when}`;
  } else if (run.origin.kind === 'mock-url') {
    text = `Microcks mocks · played by a POST to the Mock URL · answered ${run.status === 'SUCCESS' ? '200 with the outputs' : '409'} · ${when}`;
  } else {
    const judged = run.options.example ? ` · example ${run.options.example}${run.status === 'SUCCESS' ? ' · Matches the example' : ''}` : '';
    text = `Microcks mocks${judged} · ${when}`;
  }
  const unexpected = session?.calls.filter((c) => c.stepId === 'unexpected').length ?? 0;
  const end =
    run.status === 'RUNNING'
      ? 'Running'
      : unexpected > 0
        ? `${unexpected} unexpected ${unexpected === 1 ? 'call' : 'calls'}`
        : run.status === 'FAILURE'
          ? stopped
            ? `Stops at ${stopped.stepId}`
            : 'Failed'
          : stopped
            ? `Ends early at ${stopped.stepId}`
            : 'Runs to its end';
  return { tone, title, text, end };
}

/** The URL a step called in a run, with its parameters resolved ("…/pastries/Eclair%20Cafe", "…/pastries?size=M"). */
export function calledUrl(step: Step, result: StepResult | undefined, run: WorkflowRun | undefined, override?: Record<string, string>): string | undefined {
  const url = result?.request?.url;
  if (!url) return undefined;
  const prefix = step.path.split('/{')[0] ?? step.path;
  const at = url.lastIndexOf(prefix);
  const base = at >= 0 ? url.slice(0, at) : url;
  const value = (expr: string, name: string) => override?.[name] ?? String(valueIn(expr, run) ?? (expr.startsWith('$') ? `{${name}}` : expr));
  let path = step.path;
  const query: string[] = [];
  for (const p of step.parameters) {
    const v = value(p.value, p.name);
    if (p.in === 'path') path = path.replace(`{${p.name}}`, encodeURIComponent(v));
    else if (p.in === 'query') query.push(`${p.name}=${encodeURIComponent(v)}`);
  }
  return `${base}${path}${query.length ? `?${query.join('&')}` : ''}`;
}

/** The HTTP line of the inspector: "PATCH …/pastries/Eclair%20Cafe → 200, on each of the 3 attempts". */
export function httpLine(step: Step, result: StepResult | undefined, run: WorkflowRun | undefined, override?: Record<string, string>): string {
  if (!result || result.status === 'not-run') return 'No call';
  const url = calledUrl(step, result, run, override) ?? step.path;
  const last = result.attempts[result.attempts.length - 1];
  if (!last) return `${step.method} ${url}`;
  if (last.when.startsWith('call #')) return `${last.when} · ${step.method} ${url} → ${last.httpStatus}`;
  if (result.attempts.length > 1) return `${step.method} ${url} → ${last.httpStatus}, on each of the ${result.attempts.length} attempts`;
  return `${step.method} ${url} → ${last.httpStatus} · ${formatDuration(last.ms)}`;
}

export type Tone = 'pass' | 'fail' | 'off';

/** Status of a step in a run: tone and word ("Failed after 3 attempts", "Passed", "Not run"). */
export function stepStatusOf(result: StepResult | undefined): { tone: Tone; word: string } {
  if (!result) return { tone: 'off', word: 'Not part of the run' };
  if (result.status === 'not-run') return { tone: 'off', word: 'Not run' };
  if (result.status === 'passed') return { tone: 'pass', word: 'Passed' };
  const n = result.attempts.length;
  return { tone: 'fail', word: `Failed${n > 1 ? ` after ${n} attempts` : ''}` };
}

/** The checks of a step in a run: criteria and schema validation, or call / data passing / contract for a session. */
export function stepChecksOf(result: StepResult | undefined, run: WorkflowRun): { label: string; tone: Tone; word: string }[] {
  if (!result) return [];
  const notRun = result.status === 'not-run';
  if (run.origin.kind === 'session' && !notRun) {
    const wrong = wrongValue(result);
    return [
      { label: result.attempts[0]?.when.replace(/^call/, 'Call') ?? 'Call', tone: 'pass', word: 'received' },
      wrong ? { label: 'Data passing', tone: 'fail', word: 'not from an earlier step' } : { label: 'Data passing', tone: 'pass', word: 'as the workflow says' },
      { label: 'Contract', tone: 'pass', word: 'conforms' },
    ];
  }
  const held = result.criteria.every((c) => c.held);
  const sc = result.schema.state;
  return [
    { label: 'Success criteria', tone: notRun ? 'off' : held ? 'pass' : 'fail', word: notRun ? 'not evaluated' : held ? 'held' : 'did not hold' },
    {
      label: 'Schema validation',
      tone: notRun || sc === 'off' || sc === 'na' ? 'off' : sc === 'pass' ? 'pass' : 'fail',
      word: notRun && run.options.validateSchemas ? 'not evaluated' : sc === 'pass' ? 'conforms' : sc === 'fail' ? 'failed' : 'off',
    },
  ];
}
