import type { DiffLine, Source, SourceOrigin, SourceStatus } from '#lib/types.js';

export const SOURCE_ORIGINS: readonly { id: SourceOrigin; label: string; fact: string }[] = [
  { id: 'upload', label: 'Upload', fact: 'Upload · one-off file' },
  { id: 'url', label: 'URL', fact: 'URL · scheduled' },
  { id: 'git', label: 'Git', fact: 'Git repository · scheduled' },
  { id: 'hub', label: 'Hub', fact: 'Hub package · scheduled' }
];

export const originLabel = (o: SourceOrigin) => SOURCE_ORIGINS.find((x) => x.id === o)?.label ?? o;

export function sourceStatus(s: Source): SourceStatus {
  if (!s.active) return 'inactive';
  if (s.error) return 'failing';
  if (s.warning) return 'warning';
  return 'imported';
}

export interface SourceFilter {
  status: SourceStatus | 'all';
  origin: SourceOrigin | 'all';
  query: string;
}

export function filterSources(all: Source[], f: SourceFilter, nameOf: (id: string) => string[]): Source[] {
  const q = f.query.trim().toLowerCase();
  return all.filter(
    (s) =>
      (f.status === 'all' || sourceStatus(s) === f.status) &&
      (f.origin === 'all' || s.origin === f.origin) &&
      (!q || [s.name, s.url, s.secret ?? '', ...s.serviceIds.flatMap(nameOf)].join(' ').toLowerCase().includes(q))
  );
}

export function statusCounts(all: Source[]) {
  const c = { all: all.length, imported: 0, warning: 0, failing: 0, inactive: 0 };
  for (const s of all) c[sourceStatus(s)]++;
  return c;
}

export function originCounts(all: Source[]) {
  const c: Record<SourceOrigin | 'all', number> = { all: all.length, upload: 0, url: 0, git: 0, hub: 0 };
  for (const s of all) c[s.origin]++;
  return c;
}

export function diffStats(lines: DiffLine[]) {
  return { adds: lines.filter((l) => l.kind === 'add').length, dels: lines.filter((l) => l.kind === 'del').length };
}

export const ROLE_WORD = { PRIMARY: 'Primary', SECONDARY: 'Secondary' } as const;
