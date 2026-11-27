import type { AgentTestDefaults, AgentTestRun } from '#lib/runs.js';

export const load = async ({ fetch, params }) => {
  const defaults: AgentTestDefaults = await (await fetch('/api/runs/agent')).json();
  let run: AgentTestRun | undefined;
  let error: string | undefined;
  if (params.runId) {
    const res = await fetch(`/api/runs/agent/${encodeURIComponent(params.runId)}`);
    if (res.ok) run = await res.json();
    else error = res.status === 404 ? `No agent test run #${params.runId}.` : `The run could not be loaded (${res.status}).`;
  }
  return { defaults, run, error, runId: params.runId };
};
