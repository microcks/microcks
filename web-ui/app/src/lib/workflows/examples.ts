import type { RunStatus, WorkflowExample } from './types.js';

/*
 * Recorded examples of an ArazzoExamples (`ArazzoWorkflowDetail.examples`) and how a run of one is judged
 * (`WorkflowResult.example`). Pure and client-safe.
 */

export interface BackendExample {
  workflowId: string;
  name: string;
  inputs?: Record<string, unknown> | null;
  outputs?: Record<string, unknown> | null;
  steps?: { stepId: string; status: number; outputs?: Record<string, unknown> | null }[] | null;
  stopsAt?: string | null;
}

/** An example the screens draw: its name is its id (an ArazzoExamples has no description). */
export function toWorkflowExample(e: BackendExample): WorkflowExample {
  return {
    id: e.name,
    workflowId: e.workflowId,
    description: '',
    inputs: (e.inputs ?? {}) as WorkflowExample['inputs'],
    steps: (e.steps ?? []).map((s) => ({ stepId: s.stepId, status: s.status, outputs: (s.outputs ?? {}) as Record<string, string> })),
    ...(e.stopsAt ? { stopsAt: e.stopsAt } : {}),
    outputs: (e.outputs ?? {}) as WorkflowExample['outputs']
  };
}

/** What the backend decided about a run of an example. */
export interface ExampleCheck {
  matches: boolean;
  unmet: string[];
}

/** The verdict of a run of an example; undefined while the run is in progress (no verdict yet) or plays no example. */
export function exampleCheckOf(
  check: { name: string; verdict?: 'MATCHES' | 'DOES_NOT_MATCH' | null; unmetExpectations?: string[] | null } | null | undefined
): ExampleCheck | undefined {
  if (!check?.verdict) return undefined;
  return { matches: check.verdict === 'MATCHES', unmet: check.unmetExpectations ?? [] };
}

/** Whether a finished run matches the example it played: the backend verdict, else (demo runs) its outcome. */
export function judgedAs(run: { status: RunStatus; exampleCheck?: ExampleCheck }): boolean {
  return run.exampleCheck ? run.exampleCheck.matches : run.status === 'SUCCESS';
}
