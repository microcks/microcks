import { error, json } from '@sveltejs/kit';
import { analyseImportedArazzoDocument, BackendError } from '#lib/server/backend.js';
import { analyseLink, isPlaceholderDocument } from '#lib/server/workflows.js';

/**
 * What Microcks makes of an imported document: as linked now, or if a source description were linked to an API
 * (`source` and `serviceId`). Nothing is stored.
 */
export const POST = async ({ params, request, locals }) => {
  if (isPlaceholderDocument(params.docId)) error(404, 'Demo documents are not analysed by Microcks.');
  const b = await request.json().catch(() => null);
  try {
    // No link given: the document as it is linked now (the Validate button of the Source tab).
    if (!b || (b.source === undefined && b.serviceId === undefined)) {
      const current = await analyseImportedArazzoDocument(params.docId, undefined, locals.token);
      return current ? json(current) : error(404, `Document "${params.docId}" does not exist.`);
    }
    if (typeof b.source !== 'string' || typeof b.serviceId !== 'string') error(400, 'source and serviceId go together');
    const out = await analyseLink(params.docId, b.source, b.serviceId, locals.token);
    if ('error' in out) error(out.status as 404 | 422, out.error);
    return json(out);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
