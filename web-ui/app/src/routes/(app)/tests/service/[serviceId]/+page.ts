import { error } from '@sveltejs/kit';
import type { TestRunDto } from '#lib/components/testing/model.js';

export const load = async ({ fetch, params, depends }) => {
  depends('app:tests');
  const res = await fetch(`/api/tests/service/${encodeURIComponent(params.serviceId)}?size=500`);
  if (!res.ok) error(res.status, res.status === 404 ? `Service "${params.serviceId}" does not exist.` : 'The test history could not be loaded.');
  const runs: TestRunDto[] = await res.json();
  return { serviceId: params.serviceId, runs };
};
