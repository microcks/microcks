import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { closeRealSession } from '#lib/server/sessions-arazzo.js';

// Closes a session (DELETE /api/workflows/sessions/{id}): it records no more calls.
export const DELETE = async ({ params, locals }) => {
  try {
    const session = await closeRealSession(params.id, locals.token);
    if (!session) error(404, `Session "${params.id}" does not exist.`);
    return json(session);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
