import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { deleteUser, setUserDisabled } from '#lib/server/admin.js';

export const PUT = async ({ params, request, locals }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b.disabled !== 'boolean') error(400, 'disabled is required');
  try {
    await setUserDisabled(params.id, b.disabled, locals.token);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
  return json({ ok: true });
};

export const DELETE = async ({ params, locals }) => {
  try {
    await deleteUser(params.id, locals.token);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
  return json({ ok: true });
};
