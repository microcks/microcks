import { withMicrocksBase } from '#lib/base-url.js';
import type { ServiceDetail } from '#lib/components/catalog/types.js';
import type { TestRunDto } from '#lib/components/testing/model.js';
import type { WorkflowsPayload } from '#lib/workflows/list.js';

/** A workflow whose steps call this API (S12): shown under "Used in N workflows" of each operation. */
export interface WorkflowUsage {
  documentId: string;
  workflowId: string;
  summary: string;
  steps: { stepId: string; operationId: string; method: string; path: string; calls: boolean }[];
  lastRun?: { status: string; number: number; startedAt: string };
}

/** The workflows of the real Arazzo documents (not the placeholder) whose linked sources point at this API. */
function usagesOf(serviceId: string, payload: WorkflowsPayload | undefined): WorkflowUsage[] | undefined {
  if (!payload || payload.demo) return undefined;
  const usages: WorkflowUsage[] = [];
  for (const wf of payload.workflows) {
    const doc = payload.documents.find((d) => d.id === wf.documentId);
    const sources = new Set(doc?.sources.filter((s) => s.link.status === 'linked' && s.link.serviceId === serviceId).map((s) => s.name));
    const steps = wf.steps.map((s) => ({ stepId: s.stepId, operationId: s.operationId, method: s.method, path: s.path, calls: sources.has(s.sourceName) }));
    if (!steps.some((s) => s.calls)) continue;
    const last = payload.runs.filter((r) => r.documentId === wf.documentId && r.workflowId === wf.workflowId).sort((a, b) => b.number - a.number)[0];
    usages.push({ documentId: wf.documentId, workflowId: wf.workflowId, summary: wf.summary, steps, lastRun: last && { status: last.status, number: last.number, startedAt: last.startedAt } });
  }
  return usages;
}

const absolute = (d: ServiceDetail): ServiceDetail => ({ ...d, mockBase: withMicrocksBase(d.mockBase) });

export const load = async ({ fetch, params, depends }) => {
  depends('catalog:service');
  depends('app:sources');
  depends('app:tests');
  const res = await fetch(`/api/services/${encodeURIComponent(params.serviceId)}`);
  if (!res.ok) return { detail: null as ServiceDetail | null, status: res.status, runs: [] as TestRunDto[], usages: undefined as WorkflowUsage[] | undefined };
  const [runsRes, workflowsRes] = await Promise.all([
    fetch(`/api/tests/service/${encodeURIComponent(params.serviceId)}?size=500`).catch(() => undefined),
    fetch('/api/workflows').catch(() => undefined)
  ]);
  const runs: TestRunDto[] = runsRes?.ok ? await runsRes.json() : [];
  const workflows = workflowsRes?.ok ? ((await workflowsRes.json()) as WorkflowsPayload) : undefined;
  return { detail: absolute((await res.json()) as ServiceDetail), status: 200, runs, usages: usagesOf(params.serviceId, workflows) };
};
