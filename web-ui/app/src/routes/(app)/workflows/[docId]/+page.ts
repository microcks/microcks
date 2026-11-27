import { redirect } from '@sveltejs/kit';
import { workflowPath } from '#lib/workflows/paths.js';
import type { WorkflowsPayload } from '#lib/workflows/list.js';

/** A document has no page of its own: open its first workflow (the importers of Sources link here). */
export const load = async ({ params, fetch }) => {
  const payload: WorkflowsPayload = await (await fetch('/api/workflows')).json();
  const first = payload.workflows.find((w) => w.documentId === params.docId);
  redirect(307, first ? workflowPath(first.documentId, first.workflowId) : '/workflows');
};
