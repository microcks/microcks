import { error, json } from '@sveltejs/kit';
import { BackendError, importArazzoDocument, type BackendLinks } from '#lib/server/backend.js';
import { arazzoDocumentOf } from '#lib/server/workflows.js';

/** Imports an Arazzo document with the links chosen for its source descriptions. */
export const POST = async ({ request, locals }) => {
  const body = (await request.json().catch(() => null)) as { content?: string; fileName?: string; links?: BackendLinks } | null;
  if (!body || typeof body.content !== 'string' || !body.fileName) error(400, 'content and fileName are required');
  try {
    const summary = await importArazzoDocument(body.content, body.fileName, body.links ?? {}, locals.token);
    return json(await arazzoDocumentOf(summary.id, locals.token), { status: 201 });
  } catch (e) {
    if (e instanceof BackendError) error(e.status === 503 ? 503 : e.status === 400 ? 422 : e.status, e.message);
    throw e;
  }
};
