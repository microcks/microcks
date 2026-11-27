import type { WorkflowsPayload } from '#lib/workflows/list.js';

export const load = async ({ fetch, depends }) => {
  depends('app:workflows');
  const res = await fetch('/api/workflows');
  const payload: WorkflowsPayload = await res.json();
  return payload;
};
