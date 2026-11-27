import type { CockpitData } from '#lib/components/catalog/types.js';

export const load = async ({ fetch, depends }) => {
  depends('app:sources');
  const res = await fetch('/api/catalog/cockpit');
  if (!res.ok) return { cockpit: null as CockpitData | null, error: `The cockpit could not be loaded (HTTP ${res.status}).` };
  return { cockpit: (await res.json()) as CockpitData, error: '' };
};
