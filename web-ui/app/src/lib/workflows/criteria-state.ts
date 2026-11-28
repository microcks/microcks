import type { StepResult } from './types.js';

/**
 * What became of the success criteria of a step. A step that failed without any criterion evaluated (no response: the
 * call could not be made, timed out...) has none to report: "not evaluated", not "held".
 */
export function criteriaState(step: Pick<StepResult, 'status' | 'criteria'>): 'not-run' | 'not-evaluated' | 'held' | 'failed' {
  if (step.status === 'not-run') return 'not-run';
  if (step.criteria.length === 0 && step.status === 'failed') return 'not-evaluated';
  return step.criteria.every((c) => c.held) ? 'held' : 'failed';
}
