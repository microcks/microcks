import { error, json } from '@sveltejs/kit';
import { BackendError, fetchBackendTestMessages } from '#lib/server/backend.js';

/** Microcks: GET /api/tests/{id}/messages/{testCaseId} -> request/response pairs exchanged by a test case. */
export const GET = async ({ params, locals }) => {
  try {
    return json(await fetchBackendTestMessages(params.id, params.testCaseId, locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
