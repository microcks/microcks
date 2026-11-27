import type { RunKindFilter, WorkflowRun } from './types.js';

export type WorkflowTab = 'loom' | 'steps' | 'source' | 'mock-url' | 'sessions' | 'examples' | 'runs';

export const WORKFLOW_TABS: readonly WorkflowTab[] = ['loom', 'steps', 'source', 'mock-url', 'sessions', 'examples', 'runs'];

export function isWorkflowTab(value: string | undefined): value is WorkflowTab {
  return WORKFLOW_TABS.includes(value as WorkflowTab);
}

export function workflowPath(
  documentId: string,
  workflowId: string,
  tab?: WorkflowTab,
  query?: Record<string, string | undefined>
): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query ?? {})) if (value) params.set(key, value);
  const search = params.toString();
  return `/workflows/${documentId}/${workflowId}${tab ? `/${tab}` : ''}${search ? `?${search}` : ''}`;
}

export const runSetupPath = (documentId: string, workflowId: string, scenario?: string) =>
  `/workflows/${documentId}/${workflowId}/run${scenario ? `?scenario=${scenario}` : ''}`;

export const runPath = (runId: string, live = false) => `/workflows/runs/${runId}${live ? '?live' : ''}`;

export function matchesRunKind(run: Pick<WorkflowRun, 'origin' | 'target'>, filter: RunKindFilter): boolean {
  switch (filter) {
    case 'any':
      return true;
    case 'mock-url':
      return run.origin.kind === 'mock-url';
    case 'session':
      return run.origin.kind === 'session';
    case 'mocks':
      return run.target.kind === 'mocks' && (run.origin.kind === 'ui' || run.origin.kind === 'api');
    case 'implementations':
      return run.target.kind === 'implementations';
  }
}
