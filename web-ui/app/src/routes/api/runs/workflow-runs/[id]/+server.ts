import { error, json } from '@sveltejs/kit';
import { getWorkflowRunPayload } from '#lib/server/runs-workflow.js';

// Proposed Microcks counterpart: GET /api/arazzo/runs/{id} (not in Microcks today).
export const GET = ({ params }) => {
  const payload = getWorkflowRunPayload(params.id);
  if (!payload) error(404, `No workflow run "${params.id}".`);
  return json(payload);
};
