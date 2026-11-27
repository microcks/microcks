import { validateSetup, type AgentTestDefaults, type AgentTestRun, type AgentTestSetup, type TransportId } from '#lib/runs.js';
import { CONVERSATIONS, SEED_AGENT_RUN_ID, agentTestDefaults, simulateAgentTest } from '#lib/placeholders/runs.js';
import { cachedSecrets } from './admin';

/*
 * Runs BFF. Agent test runs are kept in memory and SIMULATED (see lib/placeholders/runs.ts): Microcks has no A2A runner.
 * Real counterpart: POST /api/tests (TestRequest) and GET /api/tests/{id} (TestResult).
 */

const agentRuns = new Map<string, AgentTestRun>();
let nextAgentRun = 4;

const secretNames = () => cachedSecrets().map((s) => s.name);

function seedAgentRun() {
  if (agentRuns.has(SEED_AGENT_RUN_ID)) return;
  const defaults = agentTestDefaults(String(nextAgentRun), secretNames());
  const startedAt = new Date(Date.now() - 3 * 3_600_000).toISOString();
  agentRuns.set(SEED_AGENT_RUN_ID, simulateAgentTest(SEED_AGENT_RUN_ID, defaults.setup, startedAt));
}

export function getAgentDefaults(): AgentTestDefaults {
  seedAgentRun();
  return agentTestDefaults(String(nextAgentRun), secretNames());
}

export function getAgentRun(id: string): AgentTestRun | undefined {
  seedAgentRun();
  return agentRuns.get(id);
}

const TRANSPORT_IDS: TransportId[] = ['jsonrpc', 'http', 'grpc'];

export function parseAgentSetup(raw: unknown): AgentTestSetup | { error: string } {
  if (!raw || typeof raw !== 'object') return { error: 'A test setup is required.' };
  const b = raw as Record<string, unknown>;
  const known = new Set(CONVERSATIONS.map((c) => c.id));
  const setup: AgentTestSetup = {
    cardUrl: String(b.cardUrl ?? '').trim(),
    transports: Array.isArray(b.transports) ? TRANSPORT_IDS.filter((t) => (b.transports as unknown[]).includes(t)) : [],
    conversations: Array.isArray(b.conversations) ? b.conversations.map(String).filter((c) => known.has(c)) : [],
    secret: String(b.secret ?? ''),
    timeoutMs: Number(b.timeoutMs),
    textMode: b.textMode === 'regex' ? 'regex' : 'contains',
    dataSchema: Boolean(b.dataSchema)
  };
  const problem = validateSetup(setup);
  if (problem) return { error: problem };
  if (setup.secret && !secretNames().includes(setup.secret)) return { error: `Secret "${setup.secret}" does not exist.` };
  return setup;
}

export function startAgentRun(setup: AgentTestSetup): AgentTestRun {
  seedAgentRun();
  const id = String(nextAgentRun++);
  const run = simulateAgentTest(id, setup, new Date().toISOString());
  agentRuns.set(id, run);
  return run;
}
