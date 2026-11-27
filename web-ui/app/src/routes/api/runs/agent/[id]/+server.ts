import { error, json } from '@sveltejs/kit';
import { getAgentRun } from '#lib/server/runs.js';

// Real counterpart: GET /api/tests/{id}.
export const GET = ({ params }) => {
  const run = getAgentRun(params.id);
  if (!run) error(404, `Agent test run #${params.id} does not exist.`);
  return json(run);
};
