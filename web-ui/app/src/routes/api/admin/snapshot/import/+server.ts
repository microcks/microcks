import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { parseSnapshot, restoreSnapshot } from '#lib/server/snapshot.js';

export const POST = async ({ request, locals }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b.content !== 'string') error(400, 'content is required');
  const snap = parseSnapshot(b.content);
  if ('error' in snap) error(422, snap.error);
  try {
    return json(await restoreSnapshot(b.content, snap, locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
