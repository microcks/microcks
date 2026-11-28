import { error, json } from '@sveltejs/kit';
import { sessionSetupOf } from '#lib/server/sessions-arazzo.js';
import { getWorkflowDetail } from '#lib/server/workflows.js';

// What the session builder offers for each step: the mock to call and the examples the step can be made to fail with.
export const GET = async ({ params, locals }) => {
  const detail = await getWorkflowDetail(params.docId, params.workflowId, locals.token);
  if (!detail) error(404, `Workflow "${params.workflowId}" does not exist in document "${params.docId}".`);
  return json(detail.demo ? {} : await sessionSetupOf(detail.document, detail.workflow, locals.token));
};
