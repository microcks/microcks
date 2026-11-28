import type { RunTarget } from './types.ts';

/** Arazzo W3C baggage Microcks sends with every call of a workflow run (lets Live traces group the calls). */
export function baggage(runId: string, workflowId: string, stepId: string, attempt: number): string {
  return (
    `microcks-arazzo-run=${runId},microcks-arazzo-workflow=${workflowId},` +
    `microcks-arazzo-step=${stepId},microcks-arazzo-attempt=${attempt}`
  );
}

/** The attributes Microcks puts on the spans of a mock call carrying that baggage (S29), as Live traces can filter on. */
export function traceAttributes(runId: string, workflowId: string, stepId: string, attempt: number): { name: string; value: string }[] {
  return [
    { name: 'arazzo.run.id', value: runId },
    { name: 'arazzo.workflow.id', value: workflowId },
    { name: 'arazzo.step.id', value: stepId },
    { name: 'arazzo.attempt', value: String(attempt) }
  ];
}

/** Whether a step's calls go to mocks (and so carry the baggage): its source is on the mocks of the run target. */
export function callsMocks(target: RunTarget, sourceName: string): boolean {
  return target.kind === 'mocks' || (target.kind === 'mixed' && target.perSource[sourceName] !== 'implementation');
}
