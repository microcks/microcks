import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { listFlags } from '#lib/server/admin.js';

export const GET = async ({ locals }) => {
  try {
    return json(await listFlags(locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
