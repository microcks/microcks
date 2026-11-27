import { error, json } from '@sveltejs/kit';
import { serviceDetail } from '#lib/server/catalog.js';

/** Microcks: GET /api/services/{id}. Returns the service with its operations, behaviors and examples. */
export const GET = async ({ params, locals }) => {
  const detail = await serviceDetail(params.id, locals.token);
  if (!detail) error(404, `Service ${params.id} not found`);
  return json(detail);
};
