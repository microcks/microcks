import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { importViaBackend, listSources, schedule, type ImportRequest } from '#lib/server/store.js';

export const GET = () => json({ sources: listSources(), schedule: schedule() });

export const POST = async ({ request, locals }) => {
  const body = (await request.json()) as ImportRequest;
  if (!body.fileName || typeof body.content !== 'string') error(400, 'fileName and content are required');
  try {
    return json(await importViaBackend(body, locals.token), { status: 201 });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
