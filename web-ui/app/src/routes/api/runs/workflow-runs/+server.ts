import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { startWorkflowRun } from '#lib/server/runs-workflow.js';

// Launches a run: on Microcks (POST /api/arazzo/documents/{id}/workflows/{wf}/runs) for an imported document, simulated
// for the placeholder documents.
export const POST = async ({ request, locals }) => {
  const body = await request.json().catch(() => null);
  if (!body || typeof body !== 'object' || typeof body.documentId !== 'string' || typeof body.workflowId !== 'string') {
    error(422, 'The request needs a documentId and a workflowId.');
  }
  if (!['mocks', 'implementations', 'mixed'].includes(body.target)) error(422, 'target must be mocks, implementations or mixed.');
  try {
    const result = await startWorkflowRun(body, locals.token);
    if ('runId' in result) return json(result, { status: 201 });
    return json({ message: result.error, errors: result.errors }, { status: result.status });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
