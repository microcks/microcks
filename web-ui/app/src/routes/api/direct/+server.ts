import { error, json } from '@sveltejs/kit';
import { createDirectApi } from '#lib/server/store.js';

export const POST = async ({ request }) => {
  const b = await request.json();
  if (!b.name || !b.version || !b.resource) error(400, 'name, version and resource are required');
  return json(createDirectApi(b), { status: 201 });
};
