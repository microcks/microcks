import { error } from '@sveltejs/kit';
import { removeAiExample } from '#lib/server/catalog.js';

/** Removes an accepted AI example (Copilot "Undo"). Contract examples cannot be removed. */
export const DELETE = ({ params }) => {
  if (!removeAiExample(params.id, params.opId, params.exampleId)) error(404, 'AI example not found');
  return new Response(null, { status: 204 });
};
