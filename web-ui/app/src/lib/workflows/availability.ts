/*
 * Which Arazzo features work on the documents Microcks really holds. The screens come from the Deep Ink mockup and show
 * every feature of the story map; the ones whose story is not ported yet are still drawn but disabled (never hidden)
 * on real documents. The placeholder documents (demo data) keep every feature, as in the mockup.
 *
 * To switch a feature on once its story is ported, flip its entry in `PORTED` to `true`: nothing else changes.
 */

export type ArazzoFeature =
  /** Generating a missing mock example with the AI Copilot. */
  | 'ai-copilot'
  /** Editing the document or the workflow (the Loom edit): Microcks keeps documents as imported (PUT refused). */
  | 'document-edit'
  /** Calling a workflow through its Mock URL (story S27). */
  | 'mock-url'
  /** Verification sessions (story S15). */
  | 'sessions';

/** Whether the backend serves the feature for real documents. */
export const PORTED: Readonly<Record<ArazzoFeature, boolean>> = {
  'ai-copilot': false,
  'document-edit': false,
  'mock-url': false,
  sessions: false
};

const REASON: Readonly<Record<ArazzoFeature, string>> = {
  'ai-copilot': 'Not available yet: AI generation is not connected to imported documents',
  'document-edit': 'Not available yet: Microcks keeps the document as imported, edit the file and import it again',
  'mock-url': 'Not available yet: the Mock URL of workflows is not served by Microcks',
  sessions: 'Not available yet: verification sessions are not served by Microcks'
};

/** Real documents have the 24 hex characters id of the backend; the placeholder ones are named (`pastry`, `beer`). */
const BACKEND_ID = /^[0-9a-f]{24}$/i;

/** What decides: the document of the screen (or its id), or `true` when the screen shows demo data. */
export type AvailabilityScope = { id: string; demo?: boolean } | string | boolean | undefined;

/** A document of the placeholder data (demo): the mockup's behavior stays as is. */
export function isDemo(scope: AvailabilityScope): boolean {
  if (typeof scope === 'boolean') return scope;
  if (scope === undefined) return true;
  if (typeof scope === 'string') return !BACKEND_ID.test(scope);
  return scope.demo ?? !BACKEND_ID.test(scope.id);
}

/** Whether `feature` can be used on this document: always on demo data, once ported on real documents. */
export function isAvailable(feature: ArazzoFeature, scope: AvailabilityScope): boolean {
  return isDemo(scope) || PORTED[feature];
}

/** The `title` of a disabled control; undefined while the feature is available. */
export function unavailableReason(feature: ArazzoFeature, scope: AvailabilityScope): string | undefined {
  return isAvailable(feature, scope) ? undefined : REASON[feature];
}
