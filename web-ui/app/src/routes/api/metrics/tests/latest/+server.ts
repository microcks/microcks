import { error, json } from '@sveltejs/kit';
import { latestTests } from '#lib/server/testing.js';

/** Microcks: GET /api/metrics/tests/latest?limit -> [{ id, testDate, serviceId, success }], newest first. */
export const GET = ({ url }) => {
  const limit = Number(url.searchParams.get('limit') ?? 20);
  if (!Number.isInteger(limit) || limit < 1 || limit > 500) error(400, 'limit must be an integer between 1 and 500.');
  return json(latestTests(limit));
};
