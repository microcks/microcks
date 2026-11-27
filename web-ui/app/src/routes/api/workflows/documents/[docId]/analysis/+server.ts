import { error, json } from '@sveltejs/kit';
import { analyseImportedArazzoDocument, BackendError, type BackendLinks } from '#lib/server/backend.js';
import { isPlaceholderDocument } from '#lib/server/workflows.js';

/** What Microcks says about the links of an imported document (nothing is stored); no `links` = those of its overlay. */
export const POST = async ({ params, request, locals }) => {
  if (isPlaceholderDocument(params.docId)) error(404, 'Demo documents are not analysed by Microcks.');
  const body = (await request.json().catch(() => null)) as { links?: BackendLinks | null } | null;
  try {
    const analysis = await analyseImportedArazzoDocument(params.docId, body?.links ?? undefined, locals.token);
    return analysis ? json(analysis) : error(404, `Document "${params.docId}" does not exist.`);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
