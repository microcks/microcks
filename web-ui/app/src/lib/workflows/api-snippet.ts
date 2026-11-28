/** The URL that launches a run through the API (`POST /api/workflows/documents/{id}/workflows/{wf}/runs`). */
export function runsUrl(host: string, document: { apiId: string; title: string; version: string }, workflowId: string, demo: boolean): string {
  // Microcks takes the document by its id or by `<title>:<version>`; a real document is shown by the latter, which a script can know.
  const ref = demo ? document.apiId : `${encodeURIComponent(document.title)}:${encodeURIComponent(document.version)}`;
  return `${host}/api/workflows/documents/${ref}/workflows/${encodeURIComponent(workflowId)}/runs`;
}
