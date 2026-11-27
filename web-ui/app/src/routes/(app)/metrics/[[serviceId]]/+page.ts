import { error } from '@sveltejs/kit';
import type { DomainConformance, UsageData } from '#lib/components/testing/usage.js';

export const load = async ({ fetch, params, depends }) => {
  depends('app:metrics');
  const scope = params.serviceId ?? 'all';
  const [usage, domains] = await Promise.all([fetch(`/api/metrics/usage?scope=${encodeURIComponent(scope)}`), fetch('/api/metrics/conformance/domains')]);
  if (!usage.ok) error(usage.status, usage.status === 404 ? `Service "${scope}" does not exist.` : 'Usage could not be loaded.');
  return { scope, usage: (await usage.json()) as UsageData, domains: (domains.ok ? await domains.json() : []) as DomainConformance[] };
};
