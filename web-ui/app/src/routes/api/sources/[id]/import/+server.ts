import { error, json } from '@sveltejs/kit';
import { importNow } from '#lib/server/store.js';

export const POST = async ({ params, locals }) => {
  try {
    return json(await importNow(params.id, locals.token));
  } catch (e) {
    error(400, e instanceof Error ? e.message : 'Import failed');
  }
};
