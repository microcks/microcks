import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { setServiceLabels } from '#lib/server/store.js';

/** Microcks: PUT /api/services/{id}/metadata. Body `{ labels: { key: value } }` replaces every label of the service. */
export const PUT = async ({ params, request, locals }) => {
  const body = (await request.json().catch(() => null)) as { labels?: Record<string, unknown> } | null;
  const entries = Object.entries(body?.labels ?? {});
  if (!body?.labels || entries.some(([k, v]) => !k.trim() || typeof v !== 'string')) error(400, 'labels must be an object of non-empty keys and string values.');
  try {
    const service = await setServiceLabels(params.id, Object.fromEntries(entries) as Record<string, string>, locals.token);
    if (!service) error(404, `Service ${params.id} not found`);
    return json({ labels: service.labels });
  } catch (e) {
    if (e instanceof BackendError) error(e.status === 403 ? 403 : e.status, e.status === 403 ? 'You are not allowed to edit this service.' : e.message);
    throw e;
  }
};
