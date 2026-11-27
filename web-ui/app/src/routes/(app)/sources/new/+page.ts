import type { SecretSummary } from '#lib/admin.js';
import type { ScanSchedule, Source } from '#lib/types.js';

export const load = async ({ fetch, depends }) => {
  depends('app:sources');
  const [{ sources, schedule }, samples, secrets]: [{ sources: Source[]; schedule: ScanSchedule }, string[], SecretSummary[]] = await Promise.all([
    fetch('/api/sources').then((r) => r.json()),
    fetch('/api/samples').then((r) => r.json()),
    fetch('/api/admin/secrets').then((r) => (r.ok ? r.json() : []), () => [])
  ]);
  return { sources, schedule, samples, secretNames: secrets.map((s) => s.name) };
};
