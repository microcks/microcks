import type { Runnability, StepMessaging } from './types.ts';

const sentence = (r: string) => r.charAt(0).toUpperCase() + r.slice(1);

/** Why a workflow can or cannot run, from the status Microcks gives: async steps (S28) are told apart from unlinked sources. */
export function runnabilityOf(status: { runnable: boolean; reasons: readonly string[] } | undefined): Runnability {
  if (status === undefined || status.runnable) return { runnable: true };
  const reasons = status.reasons.map(sentence);
  return {
    runnable: false,
    reason: reasons.some((r) => r.startsWith('Async steps cannot be run')) ? 'asyncapi-steps' : 'source-not-linked',
    message: reasons.join('. ')
  };
}

/** What a step that sends or waits for a message adds to an HTTP step (undefined for an HTTP step). */
export function messagingOf(
  messaging: { action: string; correlationId?: string | null; timeout?: number | null } | null | undefined,
  channel: string | null | undefined
): StepMessaging | undefined {
  if (!messaging) return undefined;
  return {
    action: messaging.action === 'send' ? 'send' : 'receive',
    ...(channel ? { channel } : {}),
    ...(messaging.correlationId ? { correlationId: messaging.correlationId } : {}),
    ...(messaging.timeout != null ? { timeoutMs: messaging.timeout } : {})
  };
}

/**
 * The `dependsOn` the loom draws, from what Microcks gives: undefined for every step when none declares dependsOn
 * (the loom is then sequential); otherwise the closest prerequisites of each step (those another prerequisite does not
 * already imply), so that a step joining parallel branches names the branches, not the step they all follow.
 */
export function dependsOnOf(steps: readonly { stepId: string; dependsOn: readonly string[]; prerequisites: readonly string[] }[]): (string[] | undefined)[] {
  if (!steps.some((s) => s.dependsOn.length > 0)) return steps.map(() => undefined);
  const direct = new Map(steps.map((s) => [s.stepId, s.prerequisites]));
  const implied = (id: string, seen = new Set<string>()): Set<string> => {
    for (const p of direct.get(id) ?? []) if (!seen.has(p)) implied(p, seen.add(p));
    return seen;
  };
  return steps.map((s) => s.prerequisites.filter((p) => !s.prerequisites.some((q) => q !== p && implied(q).has(p))));
}
