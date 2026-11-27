import { loadCatalogue, type HubPackage } from '#lib/hub.js';
import type { Service, Source } from '#lib/types.js';

export const load = async ({ fetch, depends }) => {
  depends('app:hub');
  const empty = { endpoint: '', packages: [] as HubPackage[], sources: [] as Source[], services: [] as Service[] };
  try {
    const { endpoint } = (await fetch('/api/hub/config').then((r) => r.json())) as { endpoint: string };
    const [packages, { sources }, services] = await Promise.all([
      // The SPA reads the Hub directly, as in v1.
      loadCatalogue(endpoint),
      fetch('/api/sources').then((r) => r.json()) as Promise<{ sources: Source[] }>,
      fetch('/api/services').then((r) => r.json()) as Promise<Service[]>
    ]);
    return { ...empty, endpoint, packages, sources, services, error: '' };
  } catch (e) {
    return { ...empty, error: e instanceof Error ? e.message : 'The Hub catalogue could not be loaded.' };
  }
};
