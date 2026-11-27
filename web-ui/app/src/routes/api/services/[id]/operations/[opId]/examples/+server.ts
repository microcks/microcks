import { error, json } from '@sveltejs/kit';
import { acceptExamples } from '#lib/server/catalog.js';
import type { CatalogExample } from '#lib/components/catalog/types.js';

/** Adds reviewed examples to an operation (Copilot "Accept"). In memory in this preview. */
export const POST = async ({ params, request, locals }) => {
  const body = (await request.json().catch(() => null)) as { examples?: CatalogExample[] } | null;
  if (!body?.examples?.length) error(400, 'No example to add');
  if (body.examples.some((e) => !e?.name?.trim() || typeof e.response?.body !== 'string')) error(400, 'Each example needs a name and a response body');
  const list = await acceptExamples(params.id, params.opId, body.examples, locals.token);
  if (!list) error(404, 'Operation not found');
  return json({ examples: list }, { status: 201 });
};
