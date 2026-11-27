import { error, json } from '@sveltejs/kit';
import { BackendError, fetchInvocationsLatest } from '#lib/server/backend.js';

/** Microcks: GET /api/metrics/invocations/global/latest?limit -> { yyyyMMdd: count }. */
export const GET = async ({ url, locals }) => {
  const limit = Number(url.searchParams.get('limit') ?? 20);
  if (!Number.isInteger(limit) || limit < 1 || limit > 84) error(400, 'limit must be an integer between 1 and 84.');
  try {
    return json(await fetchInvocationsLatest(limit, locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
