import { error, json } from '@sveltejs/kit';
import { getDetail, updateWorkflow } from '#lib/server/workflows.js';

export const GET = ({ params }) => {
  const detail = getDetail(params.docId, params.workflowId);
  if (!detail) error(404, `Workflow "${params.workflowId}" does not exist in document "${params.docId}".`);
  return json(detail);
};

export const PUT = async ({ params, request }) => {
  const b = await request.json().catch(() => null);
  if (!b || typeof b !== 'object') error(400, 'A JSON body is required');
  if (b.steps !== undefined && (!Array.isArray(b.steps) || b.steps.length === 0)) error(422, 'A workflow needs at least one step');
  if (b.summary !== undefined && (typeof b.summary !== 'string' || !b.summary.trim())) error(422, 'The summary cannot be empty');
  const wf = updateWorkflow(params.docId, params.workflowId, b);
  if (!wf) error(404, `Workflow "${params.workflowId}" does not exist in document "${params.docId}".`);
  return json(wf);
};
