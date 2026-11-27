import { error, json } from '@sveltejs/kit';
import { BackendError, fetchInvocationsTop } from '#lib/server/backend.js';
import { dayKey } from '#lib/server/invocations.js';

/** Microcks: GET /api/metrics/invocations/top?day&limit -> DailyStatistic[], busiest services first. */
export const GET = async ({ url, locals }) => {
  const day = url.searchParams.get('day') ?? dayKey(new Date());
  const limit = Number(url.searchParams.get('limit') ?? 20);
  if (!/^\d{8}$/.test(day)) error(400, 'day must be formatted yyyyMMdd.');
  if (!Number.isInteger(limit) || limit < 1 || limit > 500) error(400, 'limit must be an integer between 1 and 500.');
  try {
    return json(await fetchInvocationsTop(day, limit, locals.token));
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
