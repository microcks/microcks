/*
 * PLACEHOLDER: demo test history and usage figures for the Tests and Usage & conformance screens.
 *
 * Microcks keeps test results (`GET /api/tests/service/{serviceId}`, `GET /api/tests/{id}`), invocation counters
 * (`GET /api/metrics/invocations/...`) and conformance scores (`GET /api/metrics/conformance/...`), but only once a
 * test engine or real traffic has produced them. This UI has neither yet, so everything below is generated
 * deterministically from the real services of the store and is flagged "demo data" in the UI. What is missing from
 * the Microcks API and still needs an endpoint:
 *   - per-step violations, contract schema and payloads (TestResult only carries `message` per step; the payloads
 *     live in GET /api/tests/{id}/messages/{testCaseId});
 *   - the environment of a run (not a TestResult field);
 *   - invocation counts per operation and per day (DailyStatistic is per service and day, hourly and per minute);
 *   - the conformance "index" and the risk grouping by domain (TestConformanceMetric has the score and its trend).
 * Real requests made from "Run a test" are not placeholders: they are recorded by the store and shown as simulated.
 */
import type { Service } from '#lib/types.js';
import { assembleRun, type OperationOutcome, type TestResult, type TestRunDto, type TestStepResult } from '#lib/components/testing/model.js';

export const hashString = (s: string): number => {
  let h = 2166136261;
  for (let i = 0; i < s.length; i += 1) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return h >>> 0;
};

export function rng(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Stable reference time for the whole server process, so demo timestamps do not move between calls. */
const ANCHOR = Math.floor(Date.now() / 3_600_000) * 3_600_000;
const HOUR = 3_600_000;

export const DEMO_ENVIRONMENTS = ['staging', 'production'];

const runnerFor = (s: Pick<Service, 'protocol'>): string =>
  ({ REST: 'OPEN_API_SCHEMA', EVENT: 'ASYNC_API_SCHEMA', gRPC: 'GRPC_PROTOBUF', GRAPHQL: 'GRAPHQL_SCHEMA', SOAP: 'SOAP_HTTP' } as Record<string, string>)[s.protocol] ?? 'HTTP';

const slug = (s: string) => s.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '').toLowerCase();

/** Services that get a demo history; the others stay "never tested". */
export const hasDemoHistory = (svc: Pick<Service, 'id'>) => svc.id === 'api-pastry-2-0-2-0-0' || hashString(svc.id) % 3 !== 0;

const PASTRY_CONTRACT = [
  { key: 'name', schema: '{ type: string }' },
  { key: 'description', schema: '{ type: string }' },
  { key: 'size', schema: '{ type: string, enum: [S, M, L] }' },
  { key: 'price', schema: '{ type: number }' },
  { key: 'status', schema: '{ type: string, enum: [available, out_of_stock] }' }
];
const PASTRIES = [
  { name: 'Millefeuille', description: 'Delicate puff pastry with a vanilla cream filling', size: 'M', price: 4.4, status: 'available' },
  { name: 'Eclair Chocolat', description: 'Eclair with a rich dark chocolate icing', size: 'M', price: 2.5, status: 'available' },
  { name: 'Baba au rhum', description: 'Small yeast cake soaked in rum syrup', size: 'S', price: 3.2, status: 'out_of_stock' }
];

function pastryDemo(svc: Service): TestRunDto[] {
  const find = (method: string, pred: (r: string) => boolean) => svc.operations.find((o) => o.method === method && pred(o.resource ?? o.name));
  const list = find('GET', (r) => !r.includes('{'));
  const one = find('GET', (r) => r.includes('{'));
  const patch = find('PATCH', () => true);
  if (!list || !one || !patch) return genericDemo(svc);
  const id = (o: { name: string }) => o.name;
  const labels = [list, one, patch].map((o) => ({ id: id(o), method: o.method, path: o.resource ?? o.name }));
  const endpoint = 'https://pastry.staging.acme.io';
  const N = 21;
  const failing = new Map<number, 'price' | 'status'>([[6, 'price'], [12, 'price'], [13, 'status'], [17, 'status'], [20, 'price']]);
  const out: TestRunDto[] = [];
  for (let k = 0; k < N; k += 1) {
    const r = rng(hashString(`${svc.id}:${k}`));
    const number = 99 + k;
    const kind = failing.get(k);
    const timeout = k === 9;
    const ms = () => Math.round(60 + r() * 160);
    const steps: TestStepResult[] = [
      { id: 's1', operationId: id(list), name: 'List pastries', method: 'GET', path: labels[0].path, ms: ms(), result: 'passed', violations: [], httpStatus: 200, payload: PASTRIES, schemaRef: '#/components/schemas/Pastry', contract: PASTRY_CONTRACT },
      { id: 's2', operationId: id(one), name: 'Get Millefeuille', method: 'GET', path: labels[1].path.replace('{name}', 'Millefeuille'), ms: ms(), result: 'passed', violations: [], httpStatus: 200, payload: PASTRIES[0], schemaRef: '#/components/schemas/Pastry', contract: PASTRY_CONTRACT },
      { id: 's3', operationId: id(one), name: 'Get Eclair Chocolat', method: 'GET', path: labels[1].path.replace('{name}', 'Eclair%20Chocolat'), ms: ms(), result: 'passed', violations: [], httpStatus: 200, payload: PASTRIES[1], schemaRef: '#/components/schemas/Pastry', contract: PASTRY_CONTRACT }
    ];
    const bad = kind === 'price' ? { ...PASTRIES[0], price: '4.60' } : kind === 'status' ? { ...PASTRIES[0], price: 4.6, status: 'sold_out' } : { ...PASTRIES[0], price: 4.6 };
    steps.push({
      id: 's4',
      operationId: id(patch),
      name: 'Change the Millefeuille price',
      method: 'PATCH',
      path: labels[2].path.replace('{name}', 'Millefeuille'),
      ms: ms(),
      result: kind ? 'failed' : 'passed',
      violations:
        kind === 'price'
          ? [{ path: '$.price', message: 'expected a number, got a string', raw: 'Instance type (string) does not match any allowed primitive type (allowed: ["number"])' }]
          : kind === 'status'
            ? [{ path: '$.status', message: 'value is not in the enum [available, out_of_stock]', raw: 'Instance value ("sold_out") not found in enum (possible values: ["available","out_of_stock"])' }]
            : [],
      httpStatus: 200,
      requestBody: '{"price":4.6}',
      payload: bad,
      schemaRef: '#/components/schemas/Pastry',
      contract: PASTRY_CONTRACT
    });
    const operations: OperationOutcome[] = [ 'pass', 'pass', kind ? 'fail' : 'pass' ];
    const result: Exclude<TestResult, 'running'> = timeout ? 'timeout' : kind ? 'failed' : 'passed';
    if (timeout) {
      steps[3] = { ...steps[3], result: 'failed', violations: [{ path: '$', message: 'no response within the timeout', raw: 'Read timed out after 5000 ms' }], payload: undefined };
      operations[2] = 'fail';
    }
    out.push(
      assembleRun({
        id: `demo-${svc.id}-${number}`,
        number,
        startedAt: new Date(ANCHOR - (N - k) * 27 * HOUR + Math.round(r() * 40) * 60_000).toISOString(),
        endpoint: k % 5 === 4 ? 'https://pastry.acme.io' : endpoint,
        serviceId: svc.id,
        runner: 'OPEN_API_SCHEMA',
        environment: k % 5 === 4 ? 'production' : 'staging',
        timeoutMs: 5000,
        durationMs: timeout ? 5140 : 140 + steps.reduce((n, s) => n + s.ms + 60, 0),
        result,
        steps,
        operations,
        operationLabels: labels,
        reason: timeout ? 'Timed out after 5 s' : undefined,
        contractFile: 'pastry-openapi.yaml',
        demo: true,
        launchedBy: k % 3 === 0 ? 'ci' : 'laurent'
      })
    );
  }
  return out;
}

function genericDemo(svc: Service): TestRunDto[] {
  const ops = svc.operations.slice(0, 8);
  if (!ops.length) return [];
  const seed = hashString(svc.id);
  const N = 8 + (seed % 7);
  const labels = ops.map((o) => ({ id: o.name, method: o.method, path: o.resource ?? o.name }));
  const endpoint = `https://${slug(svc.name).slice(0, 24)}.staging.example.org`;
  const out: TestRunDto[] = [];
  for (let k = 0; k < N; k += 1) {
    const r = rng(hashString(`${svc.id}:${k}`));
    const failOp = r() < 0.25 ? Math.floor(r() * ops.length) : -1;
    const steps: TestStepResult[] = ops.map((o, i) => ({
      id: `s${i + 1}`,
      operationId: o.name,
      name: `${o.name} example`,
      method: o.method,
      path: o.resource ?? o.name,
      ms: Math.round(40 + r() * 260),
      result: i === failOp ? 'failed' : 'passed',
      violations: i === failOp ? [{ path: '$', message: 'response does not match the contract', raw: 'Response does not match the contract' }] : [],
      httpStatus: i === failOp ? 500 : 200
    }));
    const number = k + 100;
    out.push(
      assembleRun({
        id: `demo-${svc.id}-${number}`,
        number,
        startedAt: new Date(ANCHOR - (N - k) * (20 + (seed % 30)) * HOUR).toISOString(),
        endpoint,
        serviceId: svc.id,
        runner: runnerFor(svc),
        environment: DEMO_ENVIRONMENTS[k % 4 === 3 ? 1 : 0],
        timeoutMs: 5000,
        durationMs: 140 + steps.reduce((n, s) => n + s.ms + 60, 0),
        result: failOp >= 0 ? 'failed' : 'passed',
        steps,
        operations: ops.map((_, i): OperationOutcome => (i === failOp ? 'fail' : 'pass')),
        operationLabels: labels,
        demo: true,
        launchedBy: 'ci'
      })
    );
  }
  return out;
}

/** Oldest first. */
export function demoRunsFor(svc: Service): TestRunDto[] {
  if (!hasDemoHistory(svc)) return [];
  return svc.id === 'api-pastry-2-0-2-0-0' ? pastryDemo(svc) : genericDemo(svc);
}

// ── Usage placeholders (PLACEHOLDER: no invocation counters exist yet) ──

export const USAGE_DAYS = 84;
const DAY_MS = 86_400_000;

/** Monday 12 weeks before the Monday of the current week (UTC): the heatmap origin. */
export function usageStart(now = new Date()): Date {
  const midnight = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const sinceMonday = (new Date(midnight).getUTCDay() + 6) % 7;
  return new Date(midnight - sinceMonday * DAY_MS - 11 * 7 * DAY_MS);
}

/** Daily invocation counts of one operation, USAGE_DAYS long; the last day is today and partial. */
export function demoDailyCounts(seedKey: string, base: number, start: Date, now = new Date()): number[] {
  const r = rng(hashString(seedKey));
  const todayIndex = Math.round((Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()) - start.getTime()) / DAY_MS);
  const days = Math.min(USAGE_DAYS, todayIndex + 1);
  const dayFraction = (now.getUTCHours() * 60 + now.getUTCMinutes()) / 1440;
  return Array.from({ length: days }, (_, i) => {
    const weekday = (new Date(start.getTime() + i * DAY_MS).getUTCDay() + 6) % 7;
    const weekend = weekday >= 5 ? 0.35 : 1;
    const growth = 0.7 + (0.5 * i) / USAGE_DAYS;
    const value = base * weekend * growth * (0.75 + r() * 0.5);
    return Math.round(i === days - 1 ? value * Math.max(0.05, dayFraction) : value);
  });
}

/** Hourly split of a day count, with a office-hours curve, for the hours elapsed so far. */
export function demoHourly(seedKey: string, todayCount: number, now = new Date()): number[] {
  const r = rng(hashString(seedKey + ':h'));
  const hours = now.getUTCHours() + 1;
  const curve = Array.from({ length: hours }, (_, h) => (0.25 + Math.max(0, Math.sin(((h - 6) / 14) * Math.PI))) * (0.85 + r() * 0.3));
  const total = curve.reduce((a, b) => a + b, 0) || 1;
  let left = todayCount;
  return curve.map((c, h) => {
    const v = h === hours - 1 ? left : Math.min(left, Math.round((todayCount * c) / total));
    left -= v;
    return v;
  });
}

/** Conformance index (coverage of the contract by examples): not exposed by Microcks yet. */
export const demoConformanceIndex = (svc: Pick<Service, 'id'>): number => 70 + (hashString(svc.id + ':idx') % 31);
