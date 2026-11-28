import { error, json } from '@sveltejs/kit';
import { analyseArazzoDocument, BackendError, type BackendLinks } from '#lib/server/backend.js';

/** What Microcks says about an Arazzo document before it is imported (nothing is stored). */
export const POST = async ({ request, locals }) => {
  const body = (await request.json().catch(() => null)) as { content?: string; links?: BackendLinks | null } | null;
  if (!body || typeof body.content !== 'string') error(400, 'content is required');
  try {
    return json(await analyseArazzoDocument(body.content, body.links ?? undefined, locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
