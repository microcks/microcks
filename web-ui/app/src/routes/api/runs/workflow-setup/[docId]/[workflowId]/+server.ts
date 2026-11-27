import { error, json } from '@sveltejs/kit';
import { getRunSetup } from '#lib/server/runs-workflow.js';

// PLACEHOLDER form payload: Microcks has no Arazzo API (documents and workflows come from the workflows store).
export const GET = ({ params, url }) => {
  const setup = getRunSetup(params.docId, params.workflowId, url.searchParams.get('scenario'));
  if (!setup) error(404, `Workflow "${params.workflowId}" does not exist in document "${params.docId}".`);
  return json(setup);
};
