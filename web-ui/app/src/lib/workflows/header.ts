import { stoppedStep, verdictExplanation } from './describe.js';
import { schemaName } from './inspector.js';
import type { ArazzoDocument, Workflow, WorkflowRun } from './types.js';

export const WEEK_MS = 7 * 24 * 3600_000;

/** "Synced 12 min ago" from the document origin "Synced 12 min ago from GitHub". */
export function syncedText(document: Pick<ArazzoDocument, 'origin'>): string | undefined {
  return document.origin.startsWith('Synced') ? document.origin.replace(/ from .*$/, '') : undefined;
}

/** "Implementations · schema validation", "Microcks mocks", "Mock URL", "Session". */
export function runHow(run: WorkflowRun): string {
  if (run.origin.kind === 'session') return 'Session';
  if (run.origin.kind === 'mock-url') return 'Mock URL';
  if (run.target.kind === 'implementations') return `Implementations${run.options.validateSchemas ? ' · schema validation' : ''}`;
  return run.target.kind === 'mixed' ? 'Mixed' : 'Microcks mocks';
}

export type FailureText =
  | { kind: 'plain'; text: string }
  | { kind: 'schema'; conditions: string; field?: string; value?: string; who: string; schema?: string; expected?: string; message: string; end: string };

/** Why the last run failed, in one or two sentences (the note under the header). */
export function failureText(run: WorkflowRun, workflow: Workflow): FailureText {
  const stopped = stoppedStep(run);
  if (!stopped) return { kind: 'plain', text: verdictExplanation(run) };
  const notRun = run.steps.filter((s) => s.status === 'not-run').map((s) => s.stepId);
  const tail = [
    stopped.attempts.length > 1 ? 'The retry did not help' : undefined,
    notRun.length ? `${notRun.join(' and ')} ${notRun.length === 1 ? 'was' : 'were'} not run` : undefined,
    Object.keys(run.outputs).length === 0 ? 'the workflow produced no outputs' : undefined
  ].filter(Boolean) as string[];
  const end = tail.length ? ` ${tail.join(', ').replace(/, ([^,]*)$/, ' and $1')}.` : '';
  const bad = stopped.response?.lines.find((l) => l.bad);
  const field = bad ? /"([^"]+)":\s*(.+?),?$/.exec(bad.text.trim()) : null;
  if (stopped.schema.state === 'fail' && stopped.criteria.every((c) => c.held)) {
    const base = run.target.kind === 'implementations' ? Object.values(run.target.baseUrls)[0] : undefined;
    const who = base?.includes('staging') ? 'staging' : run.target.kind === 'implementations' ? 'the implementation' : 'the mock';
    const step = workflow.steps.find((s) => s.stepId === stopped.stepId);
    return {
      kind: 'schema',
      conditions: stopped.criteria.map((c) => c.condition).join(', '),
      field: field?.[1],
      value: field?.[2],
      who,
      schema: schemaName(stopped.schema.ref) ?? step?.operationId,
      expected: bad?.note?.replace(/^schema:\s*/, ''),
      message: stopped.schema.message,
      end
    };
  }
  return { kind: 'plain', text: `${verdictExplanation(run).replace(/ The workflow stopped there and produced no outputs\.$/, '')}${end}` };
}

/** Saves a text as a downloaded file. */
export function download(file: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/yaml' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = file;
  a.click();
  URL.revokeObjectURL(url);
}
