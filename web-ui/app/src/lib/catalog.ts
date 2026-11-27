import type { Protocol, Service } from '#lib/types.js';

export interface ServiceFilter {
  query: string;
  protocols: Protocol[];
  labels: string[];
}

export const labelToString = (key: string, value: string) => `${key}=${value}`;
export const serviceLabels = (s: Service): string[] => Object.entries(s.labels).map(([k, v]) => labelToString(k, v));

export function matches(s: Service, f: ServiceFilter, skip?: 'protocols' | 'labels'): boolean {
  const q = f.query.trim().toLowerCase();
  if (q && !`${s.name} ${s.version}`.toLowerCase().includes(q)) return false;
  if (skip !== 'protocols' && f.protocols.length && !f.protocols.includes(s.protocol)) return false;
  if (skip !== 'labels' && f.labels.length) {
    const own = serviceLabels(s);
    if (!f.labels.every((l) => own.includes(l))) return false;
  }
  return true;
}

/** Facet counts ignore the facet's own selection, so a chip shows what it would add. */
export function facets(all: Service[], f: ServiceFilter) {
  const protocols: Partial<Record<Protocol, number>> = {};
  for (const s of all.filter((x) => matches(x, f, 'protocols'))) protocols[s.protocol] = (protocols[s.protocol] ?? 0) + 1;
  const labels: Record<string, number> = {};
  for (const s of all.filter((x) => matches(x, f, 'labels'))) for (const l of serviceLabels(s)) labels[l] = (labels[l] ?? 0) + 1;
  return { protocols, labels };
}
