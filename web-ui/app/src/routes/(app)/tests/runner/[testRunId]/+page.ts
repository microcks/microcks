import { error } from '@sveltejs/kit';
import type { TestRunDto } from '#lib/components/testing/model.js';

export const load = async ({ fetch, params, depends }) => {
  depends('app:test-run');
  const res = await fetch(`/api/tests/${encodeURIComponent(params.testRunId)}`);
  if (!res.ok) error(res.status, res.status === 404 ? `Test run "${params.testRunId}" does not exist.` : 'The test run could not be loaded.');
  const run: TestRunDto = await res.json();
  const hist = await fetch(`/api/tests/service/${encodeURIComponent(run.serviceId)}?size=500`);
  const history: TestRunDto[] = hist.ok ? await hist.json() : [];
  return { run, history };
};
