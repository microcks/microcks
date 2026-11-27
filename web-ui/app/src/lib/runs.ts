/*
 * Agent test (A2A conformance) model and pure logic, shared by the page and the BFF.
 * The A2A protocol does not exist in Microcks today: the runs are simulated by lib/server/runs.ts.
 */

import { formatDay } from '#lib/format.js';

export type TransportId = 'jsonrpc' | 'http' | 'grpc';
export type AgentCheckRow = 'schema' | 'iface' | 'caps' | 'sig' | (string & {});
export type AgentCheckResult = 'pass' | 'fail' | 'warn';

export interface AgentTestConversation {
  id: string;
  skillId: string;
  turns: string;
  expect: 'completed' | 'failed' | 'rejected';
  prompt: string;
  lifecycle: string;
  contains: string;
  regex: string;
  artifact: string;
  ms: Record<TransportId, number>;
}

export interface AgentTestSetup {
  cardUrl: string;
  transports: TransportId[];
  conversations: string[];
  /** Secret name, or empty for none. */
  secret: string;
  timeoutMs: number;
  textMode: 'contains' | 'regex';
  dataSchema: boolean;
}

export interface AgentSecretChoice {
  name: string;
  help: string;
  matches: boolean;
}

export interface AgentTestDefaults {
  setup: AgentTestSetup;
  transports: { id: TransportId; label: string; sub: string; url: string; preferred: boolean }[];
  conversations: AgentTestConversation[];
  secrets: AgentSecretChoice[];
  nextRunId: string;
  /** Where the data comes from: always the placeholder simulation for now. */
  simulated: true;
}

export interface AgentCheck {
  row: AgentCheckRow;
  transport: TransportId | '*';
  result: AgentCheckResult;
  ms: number;
  note?: string;
}

export type DetailTone = 'ok' | 'bad' | 'warn' | 'muted' | 'plain';
export interface DetailRow {
  label: string;
  value: string;
  tone: DetailTone;
}
export interface LaneNodeData {
  label: string;
  task?: string;
  at?: string;
  tone?: 'ok' | 'bad';
  absent?: boolean;
}
export interface ExchangeLine {
  text: string;
  kind?: 'head' | 'ok' | 'bad';
}
export interface Exchange {
  head: string;
  status: string;
  statusTone: DetailTone;
  highlight: boolean;
  request: ExchangeLine[];
  response: ExchangeLine[];
}
export interface DetailVerdict {
  title: string;
  body: string;
}
export interface StreamEventData {
  at: string;
  kind: string;
  detail: string;
  flags: { text: string; tone: DetailTone }[];
  mark?: 'x' | '+';
  expected?: boolean;
}
export interface AgentLane {
  label: string;
  sub: string;
  tone: 'ok' | 'bad';
  nodes: LaneNodeData[];
}

export type AgentCheckDetail =
  | { kind: 'summary'; title: string; sub: string; rows: DetailRow[]; note: string }
  | { kind: 'lanes'; title: string; sub: string; lanes: AgentLane[]; note?: string; verdict?: DetailVerdict; exchanges: Exchange[] }
  | { kind: 'stream'; title: string; sub: string; events: StreamEventData[]; verdict?: DetailVerdict };

export interface AgentTestRun {
  id: string;
  setup: AgentTestSetup;
  startedAt: string;
  launchedBy: string;
  checks: AgentCheck[];
  details: Record<string, AgentCheckDetail>;
  failures: { row: string; transport: TransportId; cause: string }[];
  simulated: true;
}

const CARD_ROWS = ['schema', 'iface', 'caps', 'sig'];

export function planChecks(setup: AgentTestSetup): { row: AgentCheckRow; transport: TransportId | '*' }[] {
  const plan: { row: AgentCheckRow; transport: TransportId | '*' }[] = [{ row: 'schema', transport: '*' }];
  for (const t of setup.transports) plan.push({ row: 'iface', transport: t });
  for (const t of setup.transports) plan.push({ row: 'caps', transport: t });
  plan.push({ row: 'sig', transport: '*' });
  for (const c of setup.conversations) for (const t of setup.transports) plan.push({ row: c, transport: t });
  return plan;
}

export function validateSetup(setup: AgentTestSetup): string | undefined {
  if (!/^https?:\/\/\S+$/.test(setup.cardUrl.trim())) return 'Enter the URL of the Agent Card to test.';
  if (setup.transports.length === 0) return 'Select at least one transport.';
  if (setup.conversations.length === 0) return 'Select at least one conversation.';
  if (!Number.isFinite(setup.timeoutMs) || setup.timeoutMs <= 0) return 'Enter a timeout in milliseconds, greater than 0.';
  return undefined;
}

export function setupSummary(setup: AgentTestSetup): string {
  const n = setup.transports.length * setup.conversations.length;
  return `${setup.transports.length} × ${setup.conversations.length} = ${n} conversation checks, plus 4 Agent Card checks.`;
}

export function totalsOf(checks: readonly AgentCheck[]) {
  const conv = checks.filter((c) => !CARD_ROWS.includes(c.row));
  return {
    conversationPassed: conv.filter((c) => c.result === 'pass').length,
    conversationTotal: conv.length,
    conversationFailed: conv.filter((c) => c.result === 'fail').length,
    pass: checks.filter((c) => c.result === 'pass').length,
    fail: checks.filter((c) => c.result === 'fail').length,
    warn: checks.filter((c) => c.result === 'warn').length
  };
}

export const agentRunPassed = (checks: readonly AgentCheck[]) => checks.every((c) => c.result !== 'fail');

export const AGENT_TRANSPORT_NAMES: Record<TransportId, string> = { jsonrpc: 'JSON-RPC', http: 'HTTP+JSON', grpc: 'gRPC' };
const CARD_ROW_NAMES: Record<string, string> = {
  schema: 'Card schema',
  iface: 'Interface reachable',
  caps: 'Capabilities honoured',
  sig: 'Card signature'
};

export function agentCellName(row: string, transport: TransportId | '*'): string {
  const name = CARD_ROW_NAMES[row] ?? row;
  return transport === '*' ? name : `${AGENT_TRANSPORT_NAMES[transport]} × ${name}`;
}

export const cellKey = (row: string, transport: string) => `${row}|${transport}`;

/** 120 ms of setup, then each check and 40 ms between checks. */
export function agentElapsedMs(checks: readonly AgentCheck[], shown = checks.length): number {
  return 120 + checks.slice(0, shown).reduce((a, c) => a + c.ms + 40, 0);
}

export type AgentLogLevel = 'INFO' | 'SEND' | 'PASS' | 'FAIL' | 'WARN' | 'WAIT';
export interface AgentLogLine {
  time: string;
  level: AgentLogLevel;
  text: string;
  tone?: 'emphasis' | 'error';
}

export const fmtMs = (ms: number): string => (ms >= 1000 ? `${(ms / 1000).toFixed(2)} s` : `${ms} ms`);

/** Runner log after `shown` checks; `running` adds the WAIT line of the current check. */
export function agentRunnerLog(run: Pick<AgentTestRun, 'id' | 'setup' | 'startedAt' | 'checks'>, shown: number, running: boolean): AgentLogLine[] {
  let now = new Date(run.startedAt).getTime() + 214;
  const two = (n: number, w = 2) => String(n).padStart(w, '0');
  const clock = (t: number) => {
    const d = new Date(t);
    return `${two(d.getUTCHours())}:${two(d.getUTCMinutes())}:${two(d.getUTCSeconds())}.${two(d.getUTCMilliseconds(), 3)}`;
  };
  const lines: AgentLogLine[] = [];
  const log = (level: AgentLogLevel, text: string, tone?: AgentLogLine['tone']) => lines.push({ time: clock(now), level, text, tone });
  const { transports, conversations, cardUrl } = run.setup;
  log('INFO', `Test #${run.id} started · runner A2A_CONFORMANCE · ${transports.length} transports · ${conversations.length} conversations`, 'emphasis');
  now += 36;
  log('SEND', `→ GET ${cardUrl}`);
  now += 84;
  const n = run.checks.length;
  for (let i = 0; i <= Math.min(shown, n - 1); i++) {
    const c = run.checks[i];
    if (!c) break;
    const name = agentCellName(c.row, c.transport);
    if (i < shown) {
      now += c.ms + 40;
      log(c.result === 'pass' ? 'PASS' : c.result === 'fail' ? 'FAIL' : 'WARN', c.note ? `${name} · ${c.note}` : name, c.result === 'fail' ? 'error' : undefined);
    } else if (running) {
      log('WAIT', `${name} · waiting for the agent…`);
    }
  }
  if (!running && shown >= n) {
    const t = totalsOf(run.checks);
    log('INFO', `Test #${run.id} completed in ${fmtMs(agentElapsedMs(run.checks))} · ${t.conversationPassed}/${t.conversationTotal} conversation checks passed`, 'emphasis');
    if (t.conversationFailed) log('FAIL', `Result: FAILED · ${t.conversationFailed} conversation checks broke the A2A task contract`, 'error');
  }
  return lines;
}

/** "Nov 3, 2026 09:10:04" (UTC). */
export function formatClock(date: string | Date): string {
  const d = new Date(date);
  const t = [d.getUTCHours(), d.getUTCMinutes(), d.getUTCSeconds()].map((n) => String(n).padStart(2, '0')).join(':');
  return `${formatDay(d)} ${t}`;
}
