import { error, json } from '@sveltejs/kit';
import { undoRemove } from '#lib/server/store.js';

export const POST = async ({ params, locals }) => {
  try {
    if (!(await undoRemove(params.id, locals.token))) error(404, 'Nothing to restore');
  } catch (e) {
    if (e instanceof Error && 'status' in e) throw e;
    error(502, e instanceof Error ? e.message : 'Restore failed');
  }
  return json({ ok: true });
};
