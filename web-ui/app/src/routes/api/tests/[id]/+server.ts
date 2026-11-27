import { error, json } from '@sveltejs/kit';
import { BackendError, fetchBackendTest } from '#lib/server/backend.js';
import { toRunDto } from '#lib/server/testing.js';

/** Microcks: GET /api/tests/{id} -> TestResult. */
export const GET = async ({ params, locals }) => {
  try {
    const result = await fetchBackendTest(params.id, locals.token);
    if (!result) error(404, `Test "${params.id}" does not exist.`);
    return json(toRunDto(result));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
