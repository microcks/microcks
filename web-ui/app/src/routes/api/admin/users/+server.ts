import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { listUsers } from '#lib/server/admin.js';

export const GET = async ({ locals }) => {
  try {
    return json(await listUsers(locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
