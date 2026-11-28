import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { isPlaceholderDocument, linkRealSource, linkSource } from '#lib/server/workflows.js';

export const POST = async ({ params, request, locals }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b.serviceId !== 'string' || !b.serviceId) error(400, 'serviceId is required');
  try {
    const out = isPlaceholderDocument(params.docId)
      ? linkSource(params.docId, params.name, b.serviceId)
      : await linkRealSource(params.docId, params.name, b.serviceId, locals.token);
    if ('error' in out) error(out.status as 404 | 422, out.error);
    return json(out);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
