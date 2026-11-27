import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { analyseLink, isPlaceholderDocument } from '#lib/server/workflows.js';

/** What Microcks makes of an imported document if a source description were linked to an API; nothing is stored. */
export const POST = async ({ params, request, locals }) => {
  if (isPlaceholderDocument(params.docId)) error(404, 'Demo documents are not analysed by Microcks.');
  const b = await request.json().catch(() => null);
  if (!b || typeof b.source !== 'string' || typeof b.serviceId !== 'string') error(400, 'source and serviceId are required');
  try {
    const out = await analyseLink(params.docId, b.source, b.serviceId, locals.token);
    if ('error' in out) error(out.status as 404 | 422, out.error);
    return json(out);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
