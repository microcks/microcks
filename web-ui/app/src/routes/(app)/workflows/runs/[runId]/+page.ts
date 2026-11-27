export const ssr = false;

export const load = async ({ fetch, params, url }) => {
  const res = await fetch(`/api/runs/workflow-runs/${encodeURIComponent(params.runId)}`);
  const live = url.searchParams.has('live');
  if (!res.ok) {
    return {
      payload: undefined,
      live,
      error: res.status === 404 ? `No workflow run ${params.runId}.` : `The run could not be loaded (${res.status}).`
    };
  }
  const payload: import('#lib/runs-workflow.js').WorkflowRunPayload = await res.json();
  return { payload, live, error: undefined as string | undefined };
};
