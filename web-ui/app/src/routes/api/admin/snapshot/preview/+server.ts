import { error, json } from '@sveltejs/kit';
import { parseSnapshot, previewSnapshot } from '#lib/server/snapshot.js';

export const POST = async ({ request }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b.content !== 'string') error(400, 'content is required');
  const snap = parseSnapshot(b.content);
  if ('error' in snap) error(422, snap.error);
  return json(previewSnapshot(snap));
};
