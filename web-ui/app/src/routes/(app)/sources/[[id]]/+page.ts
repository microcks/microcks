import type { ScanSchedule, Source } from '#lib/types.js';

export const load = async ({ fetch, depends }) => {
  depends('app:sources');
  const body: { sources: Source[]; schedule: ScanSchedule } = await (await fetch('/api/sources')).json();
  return body;
};
