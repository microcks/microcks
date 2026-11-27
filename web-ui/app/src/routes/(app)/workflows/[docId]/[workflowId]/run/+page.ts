import { isRunScenario, type RunSetupPayload } from '#lib/runs-workflow.js';

export const ssr = false;

export const load = async ({ fetch, params, url }) => {
  const scenario = url.searchParams.get('scenario');
  const qs = isRunScenario(scenario) ? `?scenario=${scenario}` : '';
  const res = await fetch(`/api/runs/workflow-setup/${encodeURIComponent(params.docId)}/${encodeURIComponent(params.workflowId)}${qs}`);
  if (!res.ok) {
    return {
      setup: undefined as RunSetupPayload | undefined,
      error: res.status === 404 ? `No workflow ${params.workflowId} in document ${params.docId}.` : `The workflow could not be loaded (${res.status}).`,
      example: undefined as string | undefined,
      key: ''
    };
  }
  const setup: RunSetupPayload = await res.json();
  const example = url.searchParams.get('example') ?? undefined;
  return { setup, error: undefined as string | undefined, example, key: `${params.docId}/${params.workflowId}/${scenario ?? ''}/${example ?? ''}` };
};
