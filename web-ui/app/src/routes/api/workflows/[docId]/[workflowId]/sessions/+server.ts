import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { openRealSession } from '#lib/server/sessions-arazzo.js';
import { isPlaceholderDocument } from '#lib/server/workflows.js';

// Opens a verification session on Microcks (POST /api/arazzo/sessions) for a workflow of an imported document.
export const POST = async ({ params, request, locals }) => {
  if (isPlaceholderDocument(params.docId)) error(405, 'Sessions of the demo documents are not opened on Microcks.');
  const body = await request.json().catch(() => null);
  if (!body || typeof body !== 'object') error(400, 'A JSON body is required');
  const inputs = body.inputs && typeof body.inputs === 'object' ? body.inputs : undefined;
  const failures = body.failures && typeof body.failures === 'object' ? body.failures : undefined;
  try {
    const result = await openRealSession({ documentId: params.docId, workflowId: params.workflowId, inputs, failures }, locals.token);
    if ('session' in result) return json(result.session, { status: 201 });
    return json({ message: result.error }, { status: result.status });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
