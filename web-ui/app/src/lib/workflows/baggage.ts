/** Arazzo W3C baggage Microcks sends with every call of a workflow run (lets Live traces group the calls). */
export function baggage(runId: string, workflowId: string, stepId: string, attempt: number): string {
  return (
    `microcks-arazzo-run=${runId},microcks-arazzo-workflow=${workflowId},` +
    `microcks-arazzo-step=${stepId},microcks-arazzo-attempt=${attempt}`
  );
}
