import { error, json } from '@sveltejs/kit';
import { startWorkflowRun } from '#lib/server/runs-workflow.js';

// Simulated start. Proposed Microcks counterpart: POST /api/arazzo/documents/{id}/workflows/{wf}/runs (not in Microcks today).
export const POST = async ({ request }) => {
  const body = await request.json().catch(() => null);
  if (!body || typeof body !== 'object' || typeof body.documentId !== 'string' || typeof body.workflowId !== 'string') {
    error(422, 'The request needs a documentId and a workflowId.');
  }
  if (!['mocks', 'implementations', 'mixed'].includes(body.target)) error(422, 'target must be mocks, implementations or mixed.');
  const result = startWorkflowRun(body);
  if ('runId' in result) return json(result, { status: 201 });
  return json({ message: result.error, errors: result.errors }, { status: result.status });
};
