import { error, json } from '@sveltejs/kit';
import { BackendError, countBackendTests } from '#lib/server/backend.js';

/** Microcks: GET /api/tests/service/{serviceId}/count -> { counter }. */
export const GET = async ({ params, locals }) => {
  try {
    return json({ counter: await countBackendTests(params.serviceId, locals.token) });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
