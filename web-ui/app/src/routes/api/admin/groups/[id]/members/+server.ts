import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { addMember } from '#lib/server/admin.js';

export const POST = async ({ params, request, locals }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b.who !== 'string' || typeof b.role !== 'string') error(400, 'who and role are required');
  try {
    const out = await addMember(params.id, b.who, b.role, locals.token);
    if ('error' in out) error(422, out.error);
    return json(out, { status: 201 });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
