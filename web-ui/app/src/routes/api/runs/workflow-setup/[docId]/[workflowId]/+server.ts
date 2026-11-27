import { error, json } from '@sveltejs/kit';
import { getRunSetup } from '#lib/server/runs-workflow.js';

// Run form payload: the workflow from Microcks (`/api/arazzo`), or from the placeholder when the document is a demo one.
export const GET = async ({ params, url, locals }) => {
  const setup = await getRunSetup(params.docId, params.workflowId, url.searchParams.get('scenario'), locals.token);
  if (!setup) error(404, `Workflow "${params.workflowId}" does not exist in document "${params.docId}".`);
  return json(setup);
};
