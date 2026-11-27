import { error, json } from '@sveltejs/kit';
import { BackendError, fetchInvocationsForService } from '#lib/server/backend.js';
import { dayKey } from '#lib/server/invocations.js';

/** Microcks: GET /api/metrics/invocations/{serviceName}/{serviceVersion}?day -> DailyStatistic. */
export const GET = async ({ params, url, locals }) => {
  const day = url.searchParams.get('day') ?? dayKey(new Date());
  if (!/^\d{8}$/.test(day)) error(400, 'day must be formatted yyyyMMdd.');
  try {
    const stat = await fetchInvocationsForService(params.service, params.version, day, locals.token);
    if (!stat) error(404, `No invocation statistic for "${params.service}" ${params.version} on ${day}.`);
    return json(stat);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
