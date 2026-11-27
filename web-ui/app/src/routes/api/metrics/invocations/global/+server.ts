import { error, json } from '@sveltejs/kit';
import { BackendError, fetchInvocationsGlobal } from '#lib/server/backend.js';
import { dayKey } from '#lib/server/invocations.js';

/** Microcks: GET /api/metrics/invocations/global?day=yyyyMMdd -> aggregated DailyStatistic. */
export const GET = async ({ url, locals }) => {
  const day = url.searchParams.get('day') ?? dayKey(new Date());
  if (!/^\d{8}$/.test(day)) error(400, 'day must be formatted yyyyMMdd.');
  try {
    return json((await fetchInvocationsGlobal(day, locals.token)) ?? { day, dailyCount: 0, hourlyCount: {}, minuteCount: {} });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
