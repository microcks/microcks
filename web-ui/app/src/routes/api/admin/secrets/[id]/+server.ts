import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { deleteSecret, updateSecret } from '#lib/server/admin.js';

export const PUT = async ({ params, request, locals }) => {
  const b = await request.json().catch(() => null);
  if (!b || !['basic', 'token'].includes(b.kind)) error(400, 'a valid kind is required');
  try {
    const out = await updateSecret(
      params.id,
      { kind: b.kind, description: String(b.description ?? ''), secret: String(b.secret ?? ''), user: String(b.user ?? ''), ca: String(b.ca ?? ''), clearCa: b.clearCa === true },
      locals.token
    );
    if ('status' in out) error(out.status, out.error);
    return json(out);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};

export const DELETE = async ({ params, locals }) => {
  try {
    await deleteSecret(params.id, locals.token);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
  return json({ ok: true });
};
