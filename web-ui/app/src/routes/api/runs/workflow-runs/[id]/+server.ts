import { error, json } from '@sveltejs/kit';
import { getWorkflowRunPayload } from '#lib/server/runs-workflow.js';

// A run from Microcks (GET /api/workflows/runs/{id}), or a simulated run of a placeholder document.
export const GET = async ({ params, locals }) => {
  const payload = await getWorkflowRunPayload(params.id, locals.token);
  if (!payload) error(404, `No workflow run "${params.id}".`);
  return json(payload);
};
