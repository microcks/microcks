import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { deleteRemovedServices, deleteSourceJob, removeSource, setActive, updateSource, type SourcePatch } from '#lib/server/store.js';

const isLabels = (v: unknown): v is Record<string, string> =>
  typeof v === 'object' && v !== null && Object.entries(v).every(([k, x]) => k.trim() && typeof x === 'string');

/** `{ active }` pauses or resumes the schedule; the other fields edit the importer. */
export const PATCH = async ({ params, request, locals }) => {
  const body = (await request.json().catch(() => null)) as (SourcePatch & { active?: boolean }) | null;
  if (!body) error(400, 'A JSON body is required');
  if (body.labels !== undefined && !isLabels(body.labels)) error(400, 'labels must be an object of non-empty keys and string values.');
  if (body.role !== undefined && body.role !== 'PRIMARY' && body.role !== 'SECONDARY') error(400, 'role must be PRIMARY or SECONDARY');
  const { active, ...patch } = body;
  try {
    let source = Object.keys(patch).length ? await updateSource(params.id, patch, locals.token) : undefined;
    if (typeof active === 'boolean') source = await setActive(params.id, active, locals.token);
    if (!source) error(404, 'Unknown source');
    return json(source);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};

export const DELETE = async ({ params, locals }) => {
  const removed = removeSource(params.id);
  if (!removed) error(404, 'Unknown source');
  try {
    await deleteSourceJob(params.id, locals.token);
    await deleteRemovedServices(removed, locals.token);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
  return json({ ok: true });
};
