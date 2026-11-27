import type { ArazzoDocument, SourceDescription, Workflow } from './types.js';

/**
 * Identity of a source description in the filters: two documents can both name a source `orderApi`, and only the same
 * name linked to the same API (or both not linked) is the same chip.
 */
export function sourceKey(source: Pick<SourceDescription, 'name' | 'link'>): string {
  return `${source.name}|${source.link.status === 'linked' ? source.link.serviceId : ''}`;
}

/** The source descriptions of all documents, each distinct one once, in document order. */
export function distinctSources(documents: readonly ArazzoDocument[]): SourceDescription[] {
  const seen = new Set<string>();
  return documents.flatMap((d) => d.sources).filter((s) => !seen.has(sourceKey(s)) && !!seen.add(sourceKey(s)));
}

/** Whether a step of the workflow calls one of the source descriptions (by `sourceKey`). */
export function callsOneOf(workflow: Workflow, document: ArazzoDocument, keys: readonly string[]): boolean {
  return workflow.steps.some((st) => {
    const source = document.sources.find((x) => x.name === st.sourceName);
    return source !== undefined && keys.includes(sourceKey(source));
  });
}
