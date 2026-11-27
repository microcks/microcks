import type { SnapshotPreviewItem } from '#lib/admin.js';
import { exportRepository, importRepository, protocolOf } from './backend';
import { isBackendService, listServices, refreshServices } from './store';

/** A repository snapshot is the Microcks backend export: services, resources, requests, responses, event messages. */
interface RepositoryExport {
  services: { id: string; name: string; version?: string; type: string; operations?: unknown[] }[];
}

export function parseSnapshot(raw: string): RepositoryExport | { error: string } {
  let data: Partial<RepositoryExport>;
  try {
    data = JSON.parse(raw);
  } catch {
    return { error: 'The file is not valid JSON.' };
  }
  if (!data || !Array.isArray(data.services)) return { error: 'Not a Microcks repository snapshot.' };
  const valid = data.services.every((s) => s && typeof s.id === 'string' && typeof s.name === 'string' && typeof s.type === 'string');
  if (!valid) return { error: 'The snapshot has malformed Services.' };
  return data as RepositoryExport;
}

/** Services stored by the backend, the only ones a snapshot can hold. */
export const snapshotServices = () => listServices().filter((s) => isBackendService(s.id));

export const exportSnapshot = async (ids: string[], token?: string) => JSON.parse(await exportRepository(ids, token));

export function previewSnapshot(snap: RepositoryExport): SnapshotPreviewItem[] {
  const known = new Set(snapshotServices().map((s) => s.id));
  return snap.services.map((s) => {
    const exists = known.has(s.id);
    const n = s.operations?.length ?? 0;
    return {
      name: `${s.name} ${s.version ?? ''}`.trim(),
      protocol: protocolOf(s.type),
      kind: exists ? 'replace' : 'new',
      effect: exists ? 'already exists · will be replaced' : `new · ${n} ${n === 1 ? 'operation' : 'operations'}`
    };
  });
}

export async function restoreSnapshot(raw: string, snap: RepositoryExport, token?: string) {
  const preview = previewSnapshot(snap);
  await importRepository(raw, token);
  await refreshServices(token, true);
  const replaced = preview.filter((p) => p.kind === 'replace').length;
  return { created: preview.length - replaced, replaced };
}
