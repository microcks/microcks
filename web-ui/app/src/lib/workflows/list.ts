import type { ArazzoDocument, RunKindFilter, Workflow, WorkflowRun } from './types.js';
import { matchesRunKind } from './paths.js';

export type ResultFilter = 'all' | 'passed' | 'failed' | 'never';

export interface WorkflowFilter {
  query?: string;
  result?: ResultFilter;
  runKind?: RunKindFilter;
  sources?: string[];
}

export interface WorkflowListItem {
  workflow: Workflow;
  document: ArazzoDocument;
  lastRun?: WorkflowRun;
  lastMockUrlRun?: { number: number; startedAt: string };
}

const newest = (runs: WorkflowRun[]) => [...runs].sort((a, b) => b.number - a.number)[0];

/** Mirrors the mockup's listWorkflows use case: filters the workflows and attaches their last run. */
export function listWorkflows(
  documents: ArazzoDocument[],
  workflows: Workflow[],
  runs: WorkflowRun[],
  filter: WorkflowFilter = {}
): WorkflowListItem[] {
  const q = (filter.query ?? '').trim().toLowerCase();
  const kind = filter.runKind ?? 'any';
  const result = filter.result ?? 'all';
  const items: WorkflowListItem[] = [];
  for (const workflow of workflows) {
    const document = documents.find((d) => d.id === workflow.documentId);
    if (!document) continue;
    const hay = [workflow.summary, workflow.workflowId, document.file, ...workflow.steps.map((s) => s.stepId)]
      .join(' ')
      .toLowerCase();
    if (q && !hay.includes(q)) continue;
    if (filter.sources?.length && !workflow.steps.some((s) => filter.sources?.includes(s.sourceName))) continue;
    const mine = runs.filter((r) => r.documentId === workflow.documentId && r.workflowId === workflow.workflowId);
    const lastRun = newest(mine.filter((r) => matchesRunKind(r, kind)));
    const outcome = lastRun ? (lastRun.status === 'SUCCESS' ? 'passed' : 'failed') : 'never';
    if (result !== 'all' && outcome !== result) continue;
    const mock = newest(mine.filter((r) => r.origin.kind === 'mock-url'));
    items.push({
      workflow,
      document,
      lastRun,
      lastMockUrlRun: mock ? { number: mock.number, startedAt: mock.startedAt } : undefined
    });
  }
  return items;
}

export interface WorkflowsPayload {
  documents: ArazzoDocument[];
  workflows: Workflow[];
  runs: WorkflowRun[];
}
