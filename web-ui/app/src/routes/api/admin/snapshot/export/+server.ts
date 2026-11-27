import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { exportSnapshot } from '#lib/server/snapshot.js';

export const POST = async ({ request, locals }) => {
  const b = await request.json().catch(() => null);
  if (!b || !Array.isArray(b.serviceIds) || b.serviceIds.length === 0) error(400, 'serviceIds must list at least one Service');
  try {
    return json(await exportSnapshot(b.serviceIds.map(String), locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
