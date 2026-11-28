import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { verifyRealSession } from '#lib/server/sessions-arazzo.js';

// Verifies the calls a session received (POST /api/arazzo/sessions/{id}/verification): a run of origin Session.
export const POST = async ({ params, locals }) => {
  try {
    const session = await verifyRealSession(params.id, locals.token);
    if (!session) error(404, `Session "${params.id}" does not exist.`);
    return json(session);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
