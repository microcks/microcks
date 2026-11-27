import { microcksBaseUrl } from '#lib/base-url.js';
/*
 * Test runs as the Web UI reads them. The core fields mirror the Microcks `TestResult` document served by
 * `GET /api/tests/{id}` and `GET /api/tests/service/{serviceId}`; `details` carries what the contract diff,
 * the runner log and the history need and that Microcks serves through other calls (messages per test case)
 * or does not serve at all.
 */

export type TestResult = 'passed' | 'failed' | 'timeout' | 'running';
export type OperationOutcome = 'pass' | 'fail' | 'none';

export interface TestViolation {
  /** JSON path, e.g. `$.price`. */
  path: string;
  message: string;
  /** Raw validator message. */
  raw?: string;
}

export interface ContractProperty {
  key: string;
  /** Schema fragment, e.g. `{ type: number }`. */
  schema: string;
}

export interface TestStepResult {
  id: string;
  operationId: string;
  name: string;
  method: string;
  path: string;
  ms: number;
  result: 'passed' | 'failed';
  violations: TestViolation[];
  httpStatus?: number;
  requestBody?: string;
  payload?: unknown;
  /** e.g. `#/components/schemas/Pastry`. */
  schemaRef?: string;
  contract?: ContractProperty[];
}

/** One request/response exchanged by a test step, as `GET /api/tests/{id}/messages/{testCaseId}` serves it. */
export interface StepExchange {
  request?: { name?: string; content?: string; headers?: { name: string; values?: string[] }[] };
  response?: { content?: string; status?: string; mediaType?: string; headers?: { name: string; values?: string[] }[] };
}

/** Id of the test case of an operation, as the backend records its messages (`/` is written `!` in URLs). */
export const testCaseId = (run: Pick<TestRunDto, 'id' | 'testNumber'>, operationName: string) =>
  `${run.id}-${run.testNumber}-${operationName}`.replace(/\//g, '!');

export interface TestCaseResultDto {
  success: boolean;
  elapsedTime: number;
  operationName: string;
  testStepResults: { success: boolean; elapsedTime: number; requestName: string; message?: string }[];
}

export interface TestRunDetails {
  environment: string;
  result: TestResult;
  passed: number;
  total: number;
  /** Stored conformance score when it is not passed/total. */
  conformance?: number;
  reason?: string;
  operations: OperationOutcome[];
  operationLabels: { id: string; method: string; path: string }[];
  contractFile?: string;
  steps: TestStepResult[];
  /** The run replays its steps when the page opens. */
  live?: boolean;
  launchedBy?: string;
}

export interface TestRunDto {
  id: string;
  testNumber: number;
  testDate: string;
  testedEndpoint: string;
  serviceId: string;
  /** Milliseconds. */
  timeout: number;
  elapsedTime: number;
  success: boolean;
  inProgress: boolean;
  runnerType: string;
  runnerMessage?: string;
  testCaseResults: TestCaseResultDto[];
  /** Recorded from the Run a test form, "executed" by a simulation: no test engine is connected. */
  simulated?: boolean;
  /** Placeholder history: not produced by any test engine. */
  demo?: boolean;
  details: TestRunDetails;
}

export interface TestRunSummary {
  id: string;
  number: number;
  serviceId: string;
  runner: string;
  environment: string;
  endpoint: string;
  result: TestResult;
  startedAt: string;
  durationMs: number;
  passed: number;
  total: number;
  conformance?: number;
  reason?: string;
  operations: OperationOutcome[];
  simulated: boolean;
  demo: boolean;
}

export const summarize = (r: TestRunDto): TestRunSummary => ({
  id: r.id,
  number: r.testNumber,
  serviceId: r.serviceId,
  runner: r.runnerType,
  environment: r.details.environment,
  endpoint: r.testedEndpoint,
  result: r.details.result,
  startedAt: r.testDate,
  durationMs: r.elapsedTime,
  passed: r.details.passed,
  total: r.details.total,
  conformance: r.details.conformance,
  reason: r.details.reason,
  operations: r.details.operations,
  simulated: !!r.simulated,
  demo: !!r.demo
});

export const testRunHref = (r: { id: string }) => `/tests/runner/${encodeURIComponent(r.id)}`;

export const TEST_RESULT_WORD: Record<TestResult, string> = { passed: 'Passed', failed: 'Failed', timeout: 'Timed out', running: 'Running' };
export const TEST_RESULT_ICON = { passed: 'check', failed: 'cross', timeout: 'clock', running: 'running' } as const;

/** Conformance score of a run: the stored score, else passed steps over total, rounded. */
export function runConformance(run: Pick<TestRunSummary, 'passed' | 'total' | 'conformance'>): number {
  if (run.conformance !== undefined) return run.conformance;
  return run.total === 0 ? 0 : Math.round((100 * run.passed) / run.total);
}

export function filterRuns<T extends TestRunSummary>(runs: readonly T[], f: { result?: string; runner?: string; environment?: string }): T[] {
  return runs.filter(
    (r) =>
      (!f.result || f.result === 'all' || (f.result === 'passed' ? r.result === 'passed' : r.result !== 'passed')) &&
      (!f.runner || f.runner === 'all' || r.runner === f.runner) &&
      (!f.environment || f.environment === 'all' || r.environment === f.environment)
  );
}

export interface AssembleInput {
  id: string;
  number: number;
  startedAt: string;
  endpoint: string;
  serviceId: string;
  runner: string;
  environment: string;
  timeoutMs: number;
  durationMs: number;
  result: Exclude<TestResult, 'running'>;
  steps: TestStepResult[];
  operations: OperationOutcome[];
  operationLabels: { id: string; method: string; path: string }[];
  reason?: string;
  score?: number;
  contractFile?: string;
  live?: boolean;
  simulated?: boolean;
  demo?: boolean;
  launchedBy?: string;
}

/** Builds a run document, with the Microcks `testCaseResults` grouped by operation. */
export function assembleRun(i: AssembleInput): TestRunDto {
  const byOp = new Map<string, TestStepResult[]>();
  for (const s of i.steps) byOp.set(s.operationId, [...(byOp.get(s.operationId) ?? []), s]);
  const opName = (id: string) => {
    const l = i.operationLabels.find((o) => o.id === id);
    return l ? `${l.method} ${l.path}` : id;
  };
  return {
    id: i.id,
    testNumber: i.number,
    testDate: i.startedAt,
    testedEndpoint: i.endpoint,
    serviceId: i.serviceId,
    timeout: i.timeoutMs,
    elapsedTime: i.durationMs,
    success: i.result === 'passed',
    inProgress: false,
    runnerType: i.runner,
    runnerMessage: i.reason,
    testCaseResults: [...byOp].map(([id, steps]) => ({
      success: steps.every((s) => s.result === 'passed'),
      elapsedTime: steps.reduce((n, s) => n + s.ms, 0),
      operationName: opName(id),
      testStepResults: steps.map((s) => ({
        success: s.result === 'passed',
        elapsedTime: s.ms,
        requestName: s.name,
        message: s.violations[0]?.message
      }))
    })),
    simulated: i.simulated,
    demo: i.demo,
    details: {
      environment: i.environment,
      result: i.result,
      passed: i.steps.filter((s) => s.result === 'passed').length,
      total: i.steps.length,
      conformance: i.score,
      reason: i.reason,
      operations: i.operations,
      operationLabels: i.operationLabels,
      contractFile: i.contractFile,
      steps: i.steps,
      live: i.live,
      launchedBy: i.launchedBy
    }
  };
}

// ── Formatting (UTC, like the rest of the UI) ──

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const two = (n: number, w = 2) => String(n).padStart(w, '0');
export const dayOf = (iso: string) => `${MONTHS[new Date(iso).getUTCMonth()]} ${new Date(iso).getUTCDate()}`;
/** `Nov 2, 09:22` (UTC). */
export const shortDate = (iso: string) => {
  const d = new Date(iso);
  return `${dayOf(iso)}, ${two(d.getUTCHours())}:${two(d.getUTCMinutes())}`;
};
/** `960 ms`, `1.84 s`. */
export const fmtDuration = (ms: number) => (ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(2)} s`);
export const formatClock = (iso: string) => {
  const d = new Date(iso);
  return `${two(d.getUTCHours())}:${two(d.getUTCMinutes())}:${two(d.getUTCSeconds())} UTC`;
};

// ── Runner log ──

export type LogLevel = 'INFO' | 'SEND' | 'PASS' | 'FAIL' | 'WAIT';
export interface LogLine {
  time: string;
  level: LogLevel;
  message: string;
  tone?: 'emphasis' | 'error' | 'muted';
}

function schemaOf(step: TestStepResult): string {
  const name = step.schemaRef?.split('/').pop();
  if (!name) return '';
  return Array.isArray(step.payload) ? ` against ${name}[] (${step.payload.length})` : ` against ${name}`;
}

function clock(ms: number): string {
  return `${two(Math.floor(ms / 3_600_000))}:${two(Math.floor(ms / 60_000) % 60)}:${two(Math.floor(ms / 1000) % 60)}.${two(ms % 1000, 3)}`;
}

/** Elapsed time once `finished` steps are done: the sum of their measured durations. */
export const elapsedMs = (steps: TestStepResult[], finished: number) => steps.slice(0, finished).reduce((n, s) => n + s.ms, 0);

export function buildRunnerLog(run: TestRunDto, result: TestResult, finished: number, done: boolean): LogLine[] {
  const d = run.details;
  const start = new Date(run.testDate);
  let now = ((start.getUTCHours() * 60 + start.getUTCMinutes()) * 60 + start.getUTCSeconds()) * 1000;
  const lines: LogLine[] = [];
  const log = (level: LogLevel, message: string, tone?: LogLine['tone']) => lines.push({ time: clock(now), level, message, ...(tone ? { tone } : {}) });
  const label = (id: string) => {
    const op = d.operationLabels.find((o) => o.id === id);
    return op ? `${op.method} ${op.path}` : id;
  };
  const steps = d.steps;
  const tag = `#${run.testNumber}`;
  log('INFO', `Test ${tag} started · runner ${run.runnerType} · timeout ${Math.round(run.timeout / 1000)} s`);
  log('INFO', `${d.operationLabels.length || new Set(steps.map((s) => s.operationId)).size} operations · ${steps.length} test steps`);
  const last = done ? steps.length - 1 : finished;
  for (let i = 0; i <= last && i < steps.length; i += 1) {
    const step = steps[i];
    if (!step) break;
    if (i === 0 || steps[i - 1]?.operationId !== step.operationId) log('INFO', `Operation ${label(step.operationId)}`, 'emphasis');
    log('SEND', `→ ${step.method} ${run.testedEndpoint}${step.path}${step.requestBody ? `  ${step.requestBody}` : ''}`);
    if (i < finished || done) {
      now += step.ms;
      if (step.result === 'failed') {
        log('FAIL', `← ${step.httpStatus ? `${step.httpStatus} · ` : ''}${step.ms} ms · ${step.violations.length} ${step.violations.length === 1 ? 'violation' : 'violations'}`, 'error');
        for (const v of step.violations) log('FAIL', `  ${v.path} ${v.raw ?? v.message}`);
      } else {
        log('PASS', `← ${step.httpStatus ? `${step.httpStatus} · ` : ''}${step.ms} ms · valid${schemaOf(step)}`);
      }
    } else {
      log('WAIT', '  waiting for response…', 'muted');
    }
  }
  if (done) {
    const passed = steps.filter((s) => s.result === 'passed').length;
    log('INFO', `Test ${tag} completed in ${elapsedMs(steps, steps.length)} ms · ${passed}/${steps.length} steps passed · conformance ${Math.round((100 * passed) / Math.max(1, steps.length))}%`, 'emphasis');
    const broken = [...new Set(steps.filter((s) => s.result === 'failed').map((s) => s.operationId))].map(label);
    const ok = result === 'passed';
    log(ok ? 'INFO' : 'FAIL', `Result: ${result.toUpperCase()}${!ok && broken.length ? ` · ${broken.join(', ')} broke ${broken.length === 1 ? 'its' : 'their'} contract` : ''}`, ok ? 'emphasis' : 'error');
  }
  return lines;
}

// ── Contract diff ──

export interface DiffRow {
  n: number;
  text: string;
  bad: boolean;
}

const pad = (key: string) => `${key}:             `.slice(0, 13);
const isRecord = (v: unknown): v is Record<string, unknown> => typeof v === 'object' && v !== null && !Array.isArray(v);

/** Aligns the contract (resolved schema) with the actual response, row by row. */
export function buildContractDiff(step: Pick<TestStepResult, 'payload' | 'contract' | 'violations'>): { left: DiffRow[]; right: DiffRow[] } {
  const contract = step.contract ?? [];
  const bad = new Set(step.violations.map((v) => v.path.replace(/^\$\./, '')));
  const isArray = Array.isArray(step.payload);
  const items = isArray ? (step.payload as unknown[]) : [];
  const first = isArray ? items[0] : step.payload;
  const obj: Record<string, unknown> = isRecord(first) ? first : {};
  const left: { text: string; bad: boolean }[] = [];
  const right: { text: string; bad: boolean }[] = [];
  const push = (l: string, r: string, b = false) => {
    left.push({ text: l, bad: b });
    right.push({ text: r, bad: b });
  };
  const indent = isArray ? '    ' : '  ';
  if (isArray) {
    push('type: array', '[');
    push('items:', '  {');
    push('  type: object', '');
    push('  properties:', '');
  } else {
    push('type: object', '{');
    push('properties:', '');
  }
  contract.forEach((prop, i) => {
    const lastProp = i === contract.length - 1;
    push(indent + pad(prop.key) + prop.schema, `${indent}${JSON.stringify(prop.key)}: ${JSON.stringify(obj[prop.key])}${lastProp ? '' : ','}`, bad.has(prop.key));
  });
  if (isArray) {
    const more = items.length - 1;
    if (more > 0) {
      push('', '  },');
      push('', `  { … }   + ${more} more ${more === 1 ? 'item' : 'items'}, also valid`);
      push('', ']');
    } else {
      push('', '  }');
      push('', ']');
    }
  } else {
    push('', '}');
  }
  const number = (rows: { text: string; bad: boolean }[]): DiffRow[] => rows.map((r, i) => ({ n: i + 1, ...r }));
  return { left: number(left), right: number(right) };
}

export function ciCommandForRun(apiRef: string, endpoint: string, runner: string, waitSec: number): string {
  return [
    `microcks-cli test '${apiRef}' ${endpoint} ${runner} \\`,
    `  --microcksURL=${microcksBaseUrl()}/api/ \\`,
    '  --keycloakClientId=microcks-serviceaccount \\',
    '  --keycloakClientSecret="$MICROCKS_CLIENT_SECRET" \\',
    `  --waitFor=${waitSec}sec`
  ].join('\n');
}

export const hostOf = (url: string) => {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
};

export const basePath = (url: string) => {
  try {
    const p = new URL(url).pathname;
    return p === '/' ? '' : p.replace(/\/$/, '');
  } catch {
    return '';
  }
};
