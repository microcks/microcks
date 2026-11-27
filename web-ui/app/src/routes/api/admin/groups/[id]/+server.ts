import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { deleteGroup } from '#lib/server/admin.js';

export const DELETE = async ({ params, locals }) => {
  try {
    await deleteGroup(params.id, locals.token);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
  return json({ ok: true });
};
