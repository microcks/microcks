import { error, json } from '@sveltejs/kit';
import { linkSource } from '#lib/server/workflows.js';

export const POST = async ({ params, request }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b.serviceId !== 'string' || !b.serviceId) error(400, 'serviceId is required');
  const out = linkSource(params.docId, params.name, b.serviceId);
  if ('error' in out) error(out.status as 404 | 422, out.error);
  return json(out);
};
