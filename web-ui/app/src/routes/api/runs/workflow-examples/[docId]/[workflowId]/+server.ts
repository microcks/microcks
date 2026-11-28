import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { isPlaceholderDocument } from '#lib/server/workflows.js';
import { startRealExampleRuns } from '#lib/server/runs-arazzo.js';

// Plays every recorded example of a workflow on the mocks, one run each (POST …/workflows/{wf}/examples/runs).
// The demo documents have no such endpoint: their examples are played one at a time from the run form.
export const POST = async ({ params, request, locals }) => {
  if (isPlaceholderDocument(params.docId)) error(404, 'Demo documents play their examples one at a time.');
  const body = await request.json().catch(() => ({}));
  try {
    const result = await startRealExampleRuns(params.docId, params.workflowId, { validateSchemas: body?.validateSchemas === true, timeoutMs: typeof body?.timeoutMs === 'number' ? body.timeoutMs : undefined }, locals.token);
    if ('launches' in result) return json(result, { status: 201 });
    return json({ message: result.error }, { status: result.status });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
