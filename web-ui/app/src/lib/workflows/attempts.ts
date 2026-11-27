import type { Action, StepAttempt } from './types.js';

/*
 * Retries and end actions of the steps (`onSuccess` / `onFailure`). Microcks records each attempt of a retried step as
 * another entry of the run with the same step id; the screens show them as the attempts of one step.
 */

/** The entries of a run grouped by step id, in the order the steps first ran. */
export function groupByStep<T extends { stepId: string }>(entries: readonly T[]): Map<string, T[]> {
  const grouped = new Map<string, T[]>();
  for (const e of entries) grouped.set(e.stepId, [...(grouped.get(e.stepId) ?? []), e]);
  return grouped;
}

/** The attempts of one step; attempts after the first wait `retryAfterSeconds` (the retry action) before they start. */
export function attemptsOf(
  entries: readonly { statusCode?: number | null; elapsedTime: number }[],
  startedAt: string,
  retryAfterSeconds: number
): StepAttempt[] {
  return entries.map((e, i) => ({
    n: i + 1,
    httpStatus: e.statusCode ?? 0,
    ms: e.elapsedTime,
    when: startedAt,
    ...(i > 0 && retryAfterSeconds > 0 ? { waitMs: Math.round(retryAfterSeconds * 1000) } : {})
  }));
}

export interface BackendAction {
  name: string;
  type: string;
  retryAfter?: number | null;
  retryLimit?: number | null;
  criteria?: { condition: string; context?: string | null; type?: string | null }[] | null;
}

/** The actions of a step as the screens draw them: retry and end only. */
export function toActions(actions: readonly BackendAction[] | null | undefined): Action[] {
  return (actions ?? []).flatMap((a): Action[] => {
    const criteria = (a.criteria ?? []).map((c) =>
      c.type === 'regex' && c.context ? ({ type: 'regex', context: c.context, condition: c.condition } as const) : ({ type: 'simple', condition: c.condition } as const)
    );
    const withCriteria = criteria.length > 0 ? { criteria } : {};
    if (a.type === 'retry') return [{ type: 'retry', name: a.name, retryAfter: a.retryAfter ?? 0, retryLimit: a.retryLimit ?? 1, ...withCriteria }];
    if (a.type === 'end') return [{ type: 'end', name: a.name, ...withCriteria }];
    return [];
  });
}
