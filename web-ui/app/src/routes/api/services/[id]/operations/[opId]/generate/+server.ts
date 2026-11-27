import { error, json } from '@sveltejs/kit';
import { serviceDetail } from '#lib/server/catalog.js';
import { generateProposals } from '#lib/placeholders/catalog.js';

/** PLACEHOLDER generation: no LLM call. Microcks equivalent: the AI Copilot samples endpoint. */
export const POST = async ({ params, request, locals }) => {
  const body = (await request.json().catch(() => null)) as { count?: number; hint?: string } | null;
  const count = Number(body?.count ?? 3);
  if (!Number.isInteger(count) || count < 1 || count > 5) error(400, 'count must be between 1 and 5');
  const op = (await serviceDetail(params.id, locals.token))?.operations.find((o) => o.id === params.opId);
  if (!op) error(404, 'Operation not found');
  return json({ placeholder: true, proposals: generateProposals(op, count, String(body?.hint ?? '')) });
};
