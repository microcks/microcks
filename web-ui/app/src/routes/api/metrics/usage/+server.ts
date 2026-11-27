import { error, json } from '@sveltejs/kit';
import { usageFor } from '#lib/server/testing.js';
import { BackendError } from '#lib/server/backend.js';

/** Extension (no Microcks equivalent): usage series of `?scope=all` or a service id, built from the backend invocation counters. */
export const GET = async ({ url, locals }) => {
  const scope = url.searchParams.get('scope') ?? 'all';
  try {
    const usage = await usageFor(scope, locals.token);
    if (!usage) error(404, `Service "${scope}" does not exist.`);
    return json(usage);
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
