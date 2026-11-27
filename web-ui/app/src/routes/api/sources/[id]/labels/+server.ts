import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { updateSource } from '#lib/server/store.js';

/** Same contract as the service labels route: `{ labels }` replaces every label of the importer. */
export const PUT = async ({ params, request, locals }) => {
  const body = (await request.json().catch(() => null)) as { labels?: Record<string, unknown> } | null;
  const entries = Object.entries(body?.labels ?? {});
  if (!body?.labels || entries.some(([k, v]) => !k.trim() || typeof v !== 'string')) error(400, 'labels must be an object of non-empty keys and string values.');
  try {
    const source = await updateSource(params.id, { labels: Object.fromEntries(entries) as Record<string, string> }, locals.token);
    if (!source) error(404, `Source ${params.id} not found`);
    return json({ labels: source.labels });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
