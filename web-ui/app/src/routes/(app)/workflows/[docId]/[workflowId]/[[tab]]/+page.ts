import type { WorkflowsPayload } from '#lib/workflows/list.js';
import type { WorkflowDetailPayload } from '#lib/server/workflows.js';

export const load = async ({ fetch, params, depends }) => {
  depends('app:workflows');
  const res = await fetch(`/api/workflows/${encodeURIComponent(params.docId)}/${encodeURIComponent(params.workflowId)}`);
  if (!res.ok) return { detail: null as WorkflowDetailPayload | null, siblings: [] as WorkflowsPayload['workflows'], status: res.status };
  const detail = (await res.json()) as WorkflowDetailPayload;
  const all = (await (await fetch('/api/workflows')).json()) as WorkflowsPayload;
  return { detail, siblings: all.workflows.filter((w) => w.documentId === detail.document.id), status: 200 };
};
