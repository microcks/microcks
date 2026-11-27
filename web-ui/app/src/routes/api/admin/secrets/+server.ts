import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { createSecret, listSecrets } from '#lib/server/admin.js';

const KINDS = ['basic', 'token'];

export const GET = async ({ locals }) => {
  try {
    return json(await listSecrets(locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};

export const POST = async ({ request, locals }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b.name !== 'string' || !KINDS.includes(b.kind)) error(400, 'name and a valid kind are required');
  try {
    const out = await createSecret(
      { name: b.name.trim(), kind: b.kind, description: String(b.description ?? ''), secret: String(b.secret ?? ''), user: String(b.user ?? ''), ca: String(b.ca ?? ''), clearCa: b.clearCa === true },
      locals.token
    );
    if ('error' in out) error(422, out.error);
    return json(out, { status: 201 });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
