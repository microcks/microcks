import { error, json } from '@sveltejs/kit';
import { getWorkflowDetail, isPlaceholderDocument, updateWorkflow } from '#lib/server/workflows.js';

export const GET = async ({ params, locals }) => {
  const detail = await getWorkflowDetail(params.docId, params.workflowId, locals.token);
  if (!detail) error(404, `Workflow "${params.workflowId}" does not exist in document "${params.docId}".`);
  return json(detail);
};

export const PUT = async ({ params, request }) => {
  if (!isPlaceholderDocument(params.docId)) error(405, 'Microcks keeps Arazzo documents as imported: edit the file and import it again.');
  const b = await request.json().catch(() => null);
  if (!b || typeof b !== 'object') error(400, 'A JSON body is required');
  if (b.steps !== undefined && (!Array.isArray(b.steps) || b.steps.length === 0)) error(422, 'A workflow needs at least one step');
  if (b.summary !== undefined && (typeof b.summary !== 'string' || !b.summary.trim())) error(422, 'The summary cannot be empty');
  const wf = updateWorkflow(params.docId, params.workflowId, b);
  if (!wf) error(404, `Workflow "${params.workflowId}" does not exist in document "${params.docId}".`);
  return json(wf);
};
