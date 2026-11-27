import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { createGroup, listGroups } from '#lib/server/admin.js';

export const GET = async ({ locals }) => {
  try {
    return json(await listGroups(locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};

export const POST = async ({ request, locals }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b.name !== 'string') error(400, 'name is required');
  try {
    const out = await createGroup(b.name.trim(), String(b.description ?? ''), locals.token);
    if ('error' in out) error(422, out.error);
    return json(out, { status: 201 });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
