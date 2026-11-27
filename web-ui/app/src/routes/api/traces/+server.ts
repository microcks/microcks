import { error, json } from '@sveltejs/kit';
import { getTopology } from '#lib/server/observe.js';

/** BFF for Live traces. Microcks has no per-call trace API yet: see lib/placeholders/observe.ts. */
export const GET = () => {
  const topology = getTopology();
  if (!topology) error(404, 'No REST service in the catalogue to trace. Import a source first.');
  return json(topology);
};
