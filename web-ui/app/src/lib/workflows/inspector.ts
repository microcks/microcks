import { criteriaText, stoppedStep } from './describe.js';
import type { Action, Step, StepResult, Workflow, WorkflowRun } from './types.js';

export const prettyJson = (json: string) => {
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
};

/** "PatchPastry · 200 · application/json · Pastry" → "Pastry". */
export const schemaName = (ref: string) => {
  const parts = ref.split(' · ');
  return parts.length > 3 ? parts[parts.length - 1] : undefined;
};

/** Title, detail and note of the failure box of a step. */
export function failureBox(step: Step, result: StepResult, run: WorkflowRun): { title: string; detail: string; note: string } | undefined {
  if (result.status !== 'failed') return undefined;
  const last = result.attempts[result.attempts.length - 1];
  if (result.schema.state === 'fail') {
    const bad = result.response?.lines.find((l) => l.bad);
    const field = bad ? /"([^"]+)":\s*(.+?),?$/.exec(bad.text.trim()) : null;
    const expected = bad?.note?.replace(/^schema:\s*/, '');
    const name = schemaName(result.schema.ref);
    return {
      title: `Response ${last?.httpStatus ?? ''} of ${step.operationId} does not conform to its schema`,
      detail:
        field && expected && name
          ? `/${field[1]} does not match the ${name} schema: ${field[2]?.startsWith('"') ? `string ${field[2]}` : field[2]} found, ${expected} expected`
          : result.schema.message,
      note: 'A response that does not conform to its schema fails the step, even when its criteria hold.'
    };
  }
  if (run.origin.kind === 'session') {
    return {
      title: result.flow.title,
      detail: result.flow.text,
      note: 'A verification checks that each call carries the values the workflow passes from earlier steps and from its inputs.'
    };
  }
  const unheld = result.criteria.find((c) => !c.held);
  return unheld
    ? { title: 'A success criterion did not hold', detail: `${unheld.condition}: ${unheld.evaluation}`, note: 'A step passes only when all its success criteria hold.' }
    : { title: result.flow.title, detail: result.flow.text, note: '' };
}

/** Why a step was not run, from the step where the run stopped. */
export function notRunText(run: WorkflowRun): string {
  const stopped = stoppedStep(run);
  if (!stopped) return 'Not run: the run ended before this step.';
  if (stopped.status === 'passed') return `Not run: the end action of ${stopped.stepId} ended the run.`;
  const retries = stopped.attempts.length - 1;
  return retries > 0
    ? `Not run: ${stopped.stepId} still failed after its ${retries} ${retries === 1 ? 'retry' : 'retries'}, so the run ended there.`
    : `Not run: ${stopped.stepId} failed, so the run ended there.`;
}

export interface ActionRow {
  when: 'On success' | 'On failure';
  type: string;
  text: string;
  crit?: string;
  note?: string;
  noteTone?: 'danger' | 'secondary';
}

const describe = (a: Action): string =>
  a.type === 'retry' ? `retry every ${a.retryAfter} s, up to ${a.retryLimit} ${a.retryLimit === 1 ? 'time' : 'times'}` : 'end the workflow';

export function actionRows(workflow: Workflow, step: Step, result: StepResult | undefined, run: WorkflowRun | undefined): ActionRow[] {
  const ran = result !== undefined && result.status !== 'not-run' && run !== undefined;
  const label = run ? `#${run.number}` : '';
  const next = workflow.steps[workflow.steps.indexOf(step) + 1];
  const rows: ActionRow[] = [];
  if (step.onSuccess.length === 0) {
    rows.push({ when: 'On success', type: 'default', text: next ? 'go on to the next step' : 'the workflow ends and returns its outputs' });
  }
  for (const a of step.onSuccess) {
    let note: string | undefined;
    if (ran && a.type === 'end') {
      if (result.flow.kind === 'end') note = `Applied in ${label}: the run ended here, with success.`;
      else {
        const field = /\$response\.body#\/([A-Za-z0-9_-]+)/.exec(criteriaText(a.criteria))?.[1];
        const v = field ? result.outputs[field] : undefined;
        note = `Not applied in ${label}: ${field && v !== undefined ? `${field} was '${String(v)}', so ` : ''}the run went on to ${next?.stepId ?? 'its end'}.`;
      }
    }
    rows.push({ when: 'On success', type: a.type, text: describe(a), crit: criteriaText(a.criteria) || undefined, note, noteTone: 'secondary' });
  }
  if (step.onFailure.length === 0) rows.push({ when: 'On failure', type: 'default', text: 'end the run; the later steps are not run' });
  for (const a of step.onFailure) {
    let note: string | undefined;
    let noteTone: ActionRow['noteTone'] = 'secondary';
    if (ran && a.type === 'retry') {
      if (run.origin.kind === 'session') note = 'A verification checks the calls received; it does not retry.';
      else if (result.attempts.length > 1) {
        const later = result.attempts.slice(1).map((x) => x.n);
        note = `Applied in ${label}: ${later.length === 1 ? `attempt ${later[0]}` : `attempts ${later.slice(0, -1).join(', ')} and ${later[later.length - 1]}`} ${result.status === 'failed' ? 'failed the same way, then the run ended' : 'passed'}.`;
        noteTone = result.status === 'failed' ? 'danger' : 'secondary';
      } else note = `Not needed in ${label}: the step passed at its first attempt.`;
    }
    rows.push({ when: 'On failure', type: a.type, text: describe(a), crit: criteriaText(a.criteria) || undefined, note, noteTone });
  }
  return rows;
}
