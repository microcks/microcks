import { error, json } from '@sveltejs/kit';
import { getAgentDefaults, parseAgentSetup, startAgentRun } from '#lib/server/runs.js';

// Simulated A2A test. Real counterpart: POST /api/tests with runner A2A_CONFORMANCE (not in Microcks today).
export const GET = () => json(getAgentDefaults());

export const POST = async ({ request }) => {
  const body = await request.json().catch(() => null);
  const setup = parseAgentSetup(body);
  if ('error' in setup) error(422, setup.error);
  return json(startAgentRun(setup), { status: 201 });
};
