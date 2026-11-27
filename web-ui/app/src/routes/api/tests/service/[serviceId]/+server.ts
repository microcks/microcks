import { error, json } from '@sveltejs/kit';
import { BackendError, fetchBackendTests } from '#lib/server/backend.js';
import { toRunDto } from '#lib/server/testing.js';

/** Microcks: GET /api/tests/service/{serviceId}?page&size -> TestResult[], newest first. */
export const GET = async ({ params, url, locals }) => {
  const page = Number(url.searchParams.get('page') ?? 0);
  const size = Number(url.searchParams.get('size') ?? 20);
  if (!Number.isInteger(page) || page < 0 || !Number.isInteger(size) || size < 1 || size > 500) error(400, 'page and size must be positive integers (size <= 500).');
  try {
    return json((await fetchBackendTests(params.serviceId, page, size, locals.token)).map(toRunDto));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
