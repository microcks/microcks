/** Identifier of an operation on the API page: its name (`GET /pastry/{name}`) as a slug. */
const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

/** The page of the operation a step calls, on the API its source description is linked to; the list of APIs if none. */
export function operationHref(
  document: { sources: { name: string; link: { status: 'linked'; serviceId: string } | { status: 'not-linked' } }[] } | undefined,
  step: { sourceName: string; method: string; path: string }
): string {
  const link = document?.sources.find((s) => s.name === step.sourceName)?.link;
  if (!link || link.status !== 'linked') return '/services';
  return `/services/${link.serviceId}?op=${slug(`${step.method} ${step.path}`)}`;
}
