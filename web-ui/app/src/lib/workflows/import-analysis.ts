/*
 * What Microcks says about an Arazzo document before it is imported, or about an imported one before its sources are
 * linked again (`POST /api/arazzo/documents/analysis`, `POST /api/arazzo/documents/{id}/analysis`). Pure and
 * client-safe: the screens only word it.
 */

/** The API a source description is linked to; `serviceId` is absent when the backend only knows its name and version. */
export interface ApiRef {
  serviceId?: string | null;
  name: string;
  version: string;
}

export interface SourceAnalysis {
  sourceName: string;
  declaredUrl?: string | null;
  match?: { status: 'MATCHED' | 'NO_API' | 'UNREADABLE'; contractTitle?: string | null; contractVersion?: string | null } | null;
  link: { sourceName: string; apiName?: string | null; apiVersion?: string | null; linked: boolean };
  operationCount: number;
  foundOperationCount: number;
  unresolvedStepCount: number;
}

export interface WorkflowCheck {
  workflowId: string;
  runnable: boolean;
  apiNames: string[];
  unlinkedSources: string[];
  reasons: string[];
}

export interface ImportAnalysis {
  /** Why Microcks refuses the document (outside the subset it runs, unreadable...); absent when it can be imported. */
  refusal?: string | null;
  arazzoVersion?: string | null;
  title?: string | null;
  version?: string | null;
  workflowIds: string[];
  sources: SourceAnalysis[];
  checks: WorkflowCheck[];
  /** The APIs of Microcks a source can be linked to. */
  apis: ApiRef[];
}

/** The API chosen for each source description by name; null for a source left not linked. */
export type LinkChoices = Record<string, ApiRef | null>;

/** The choices that reproduce the links of an analysis: the API each linked source names, as listed by Microcks. */
export function initialLinks(analysis: Pick<ImportAnalysis, 'sources' | 'apis'>): LinkChoices {
  return Object.fromEntries(
    analysis.sources.map((s): [string, ApiRef | null] => {
      const { linked, apiName, apiVersion } = s.link;
      if (!linked || !apiName) return [s.sourceName, null];
      return [s.sourceName, analysis.apis.find((a) => a.name === apiName && a.version === apiVersion) ?? { name: apiName, version: apiVersion ?? '' }];
    })
  );
}

export interface SourceVerdict {
  tone: 'success' | 'warning';
  status: 'Linked' | 'Not linked';
  detail: string;
}

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;

/** Whether a source is linked and how many of the operations the workflow calls its API defines. */
export function sourceVerdict(source: SourceAnalysis): SourceVerdict {
  if (source.link.linked) {
    const unresolved = source.unresolvedStepCount > 0 ? `, ${plural(source.unresolvedStepCount, 'step')} unresolved` : '';
    return {
      tone: source.foundOperationCount === source.operationCount ? 'success' : 'warning',
      status: 'Linked',
      detail: `${source.foundOperationCount} of ${plural(source.operationCount, 'operation')} found${unresolved}`
    };
  }
  const url = source.declaredUrl ?? 'its declared URL';
  const match = source.match;
  const detail =
    match?.status === 'NO_API'
      ? `No API named ${match.contractTitle ?? ''} ${match.contractVersion ?? ''} in Microcks: import it first, or pick the API to link`
      : match?.status === 'UNREADABLE'
        ? `The contract at ${url} could not be read: pick the API to link`
        : 'Pick the API to link';
  return { tone: 'warning', status: 'Not linked', detail };
}

const sentence = (text: string) => text.charAt(0).toUpperCase() + text.slice(1);

/** Whether a workflow can run with the links of the analysis, or what it waits for. */
export function workflowVerdict(check: WorkflowCheck): { runnable: boolean; note: string } {
  if (check.runnable) return { runnable: true, note: 'Ready to run' };
  if (check.unlinkedSources.length > 0) return { runnable: false, note: `Needs ${check.unlinkedSources.join(', ')} linked` };
  return { runnable: false, note: check.reasons.map(sentence).join('. ') || 'Not ready to run' };
}

/** How many workflows of the document can run with the links of the analysis, and what the others wait for. */
export function readiness(analysis: Pick<ImportAnalysis, 'checks'>): { runnable: number; total: number; blocked: { workflowId: string; note: string }[] } {
  const verdicts = analysis.checks.map((c) => ({ workflowId: c.workflowId, ...workflowVerdict(c) }));
  return {
    runnable: verdicts.filter((v) => v.runnable).length,
    total: verdicts.length,
    blocked: verdicts.filter((v) => !v.runnable).map(({ workflowId, note }) => ({ workflowId, note }))
  };
}
