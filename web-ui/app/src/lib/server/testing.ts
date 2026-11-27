/*
 * Server side of the Tests screens. Shapes follow Microcks `TestController`:
 *   GET /api/tests/service/{serviceId}  -> TestResult[], newest first
 *   GET /api/tests/{id}                 -> TestResult
 * POST /api/tests, GET /api/tests/{id} and GET /api/tests/service/{serviceId} are served by the backend (routes/api/tests).
 * The usage and conformance screens still use the demo history from `lib/placeholders/testing.ts`.
 */
import { getService, listServices } from './store.js';
import type { BackendTestResult } from './backend.js';
import { assembleRun, runConformance, summarize, type TestRunDto, type TestStepResult } from '#lib/components/testing/model.js';
import type { ConformanceItem, DomainConformance, UsageData, UsageRowData } from '#lib/components/testing/usage.js';
import { demoConformanceIndex, demoRunsFor, hashString, rng } from '#lib/placeholders/testing.js';
import { isoDay, loadInvocations } from './invocations.js';
import { targetFor } from '#lib/testing.js';
import type { Service } from '#lib/types.js';

/** Splits a Microcks operation name such as `GET /pastry` into verb and path. */
function splitOperation(name: string): { method: string; path: string } {
  const m = /^([A-Z]+) (\/.*)$/.exec(name);
  return m ? { method: m[1], path: m[2] } : { method: '', path: name };
}

/** Maps a backend TestResult to the document the Tests screens read, deriving `details` from its test cases. */
export function toRunDto(t: BackendTestResult): TestRunDto {
  const cases = t.testCaseResults ?? [];
  const steps: TestStepResult[] = cases.flatMap((c, ci) => {
    const { method, path } = splitOperation(c.operationName);
    return (c.testStepResults ?? []).map((s, si) => ({
      id: `s${ci + 1}-${si + 1}`,
      operationId: c.operationName,
      name: s.requestName ?? s.eventMessageName ?? `step ${si + 1}`,
      method,
      path,
      ms: s.elapsedTime,
      result: s.success ? ('passed' as const) : ('failed' as const),
      violations: !s.success && s.message ? [{ path: '$', message: s.message }] : []
    }));
  });
  const run = assembleRun({
    id: t.id,
    number: t.testNumber,
    startedAt: new Date(t.testDate).toISOString(),
    endpoint: t.testedEndpoint,
    serviceId: t.serviceId,
    runner: t.runnerType,
    environment: 'manual',
    timeoutMs: t.timeout,
    durationMs: Math.max(t.elapsedTime, 0),
    result: t.success ? 'passed' : 'failed',
    steps,
    operations: cases.map((c) => (c.success ? ('pass' as const) : ('fail' as const))),
    operationLabels: cases.map((c) => ({ id: c.operationName, ...splitOperation(c.operationName) })),
    reason: t.runnerMessage || undefined
  });
  run.inProgress = t.inProgress;
  if (t.inProgress) run.details.result = 'running';
  return run;
}

/** Placeholder history of a service (conformance screens only). Real runs come from the backend. */
export function runsForService(serviceId: string): TestRunDto[] | null {
  const svc = getService(serviceId);
  if (!svc) return null;
  return demoRunsFor(svc).sort((a, b) => b.testDate.localeCompare(a.testDate));
}

export const allRuns = (): TestRunDto[] => listServices().flatMap((s) => runsForService(s.id) ?? []);
export type { Service };

// ── Usage & conformance ──
// Shapes follow Microcks `MetricsController`; the counters come from PLACEHOLDER generators.

const pad2 = (n: number) => String(n).padStart(2, '0');
const yyyymmdd = (d: Date) => `${d.getUTCFullYear()}${pad2(d.getUTCMonth() + 1)}${pad2(d.getUTCDate())}`;
const sumOf = (v: readonly number[]) => v.reduce((a, b) => a + b, 0);

function latestRun(serviceId: string): TestRunDto | undefined {
  return (runsForService(serviceId) ?? []).find((r) => !r.inProgress);
}

function conformanceItem(svc: Service): ConformanceItem {
  const run = latestRun(svc.id);
  const runs = (runsForService(svc.id) ?? []).filter((r) => !r.inProgress);
  const score = run ? runConformance(summarize(run)) : null;
  const previous = runs[1] ? runConformance(summarize(runs[1])) : null;
  return {
    serviceId: svc.id,
    name: `${svc.name} ${svc.version}`,
    protocol: svc.protocol as ConformanceItem['protocol'],
    operations: svc.operations.length,
    score,
    index: Math.max(demoConformanceIndex(svc), score ?? 0),
    trend: score === null || previous === null ? null : score > previous ? 'up' : score < previous ? 'down' : 'stable'
  };
}

/** Domain-grouped conformance of every service (extension: Microcks only exposes the per-label aggregate). */
export function conformanceByDomain(): DomainConformance[] {
  const groups = new Map<string, ConformanceItem[]>();
  for (const svc of listServices()) {
    const key = svc.labels.domain || 'other';
    groups.set(key, [...(groups.get(key) ?? []), conformanceItem(svc)]);
  }
  return [...groups.entries()].map(([domain, items]) => {
    const scored = items.filter((i) => i.score !== null).map((i) => i.score as number);
    return {
      domain,
      label: domain === 'other' ? 'no domain label' : `domain=${domain}`,
      score: scored.length ? Math.round(sumOf(scored) / scored.length) : null,
      services: items.length,
      items
    };
  });
}

/** Microcks `/api/metrics/conformance/aggregate`: average score per label value, weight = service count. */
export const conformanceAggregate = () =>
  conformanceByDomain().map((d) => ({ name: d.domain, weight: d.services, value: d.score ?? 0 }));

/** Microcks `/api/metrics/conformance/service/{id}` (TestConformanceMetric). */
export function conformanceForService(serviceId: string) {
  const svc = getService(serviceId);
  if (!svc) return undefined;
  const item = conformanceItem(svc);
  const runs = (runsForService(serviceId) ?? []).filter((r) => !r.inProgress);
  return {
    serviceId,
    maxPossibleScore: item.index,
    currentScore: item.score ?? 0,
    lastUpdateDay: runs[0] ? yyyymmdd(new Date(runs[0].testDate)) : yyyymmdd(new Date()),
    latestTrend: item.trend === 'up' ? 'UP' : item.trend === 'down' ? 'DOWN' : 'STABLE',
    latestScores: Object.fromEntries(runs.slice(0, 10).map((r) => [yyyymmdd(new Date(r.testDate)), runConformance(summarize(r))]))
  };
}

/** Microcks `/api/metrics/tests/latest`: latest results, newest first. */
export function latestTests(limit: number) {
  return allRuns()
    .filter((r) => !r.inProgress)
    .sort((a, b) => b.testDate.localeCompare(a.testDate))
    .slice(0, limit)
    .map((r) => ({ id: r.id, testDate: r.testDate, serviceId: r.serviceId, success: r.success }));
}

const TREND_WORDS = { up: 'up', down: 'down', stable: 'stable', mixed: 'mixed' } as const;

function sparse(series: number[], from: number, to: number) {
  return sumOf(series.slice(Math.max(0, from), Math.max(0, to)));
}

/** The raw series the Usage screen needs for a scope (`all` or a service id); null when the service is unknown. */
export async function usageFor(scope: string, token?: string, now = new Date()): Promise<UsageData | null> {
  const all = listServices();
  const one = scope === 'all' ? undefined : all.find((s) => s.id === scope);
  if (scope !== 'all' && !one) return null;
  const { start, daily, hourly, rows: counted } = await loadInvocations(all, one, token, now);
  const n = daily.length;
  const pct = (a: number, b: number) => (b === 0 ? 0 : Math.round(((a - b) / b) * 100));
  const previous = {
    today: Math.round((daily[n - 8] ?? 0) * Math.min(1, (hourly.length - 0.5) / 24 + 0.05)),
    '7d': sparse(daily, n - 14, n - 7),
    '30d': sparse(daily, n - 60, n - 30)
  };
  // Per-service counters only cover the last 14 days: the 30 days trend is unknown at row level.
  const rows: UsageRowData[] = counted.map(({ service: s, counts }) => ({
    id: s.id,
    name: `${s.name} ${s.version}`,
    protocol: s.protocol as UsageRowData['protocol'],
    detail: `${s.operations.length} ${s.operations.length === 1 ? 'operation' : 'operations'}`,
    today: counts[0] ?? 0,
    w7: sumOf(counts.slice(0, 7)),
    trend: { today: pct(counts[0] ?? 0, counts[7] ?? 0), '7d': pct(sumOf(counts.slice(0, 7)), sumOf(counts.slice(7, 14))), '30d': 0 },
    last: (counts[0] ?? 0) > 0 ? 'today' : '—',
    live: false
  }));
  const latest = rows.find((r) => r.today > 0);
  const items = (one ? [one] : all).map(conformanceItem);
  const scored = items.filter((i) => i.score !== null).map((i) => i.score as number);
  const score = scored.length ? Math.round(sumOf(scored) / scored.length) : null;
  const index = items.length ? Math.round(sumOf(items.map((i) => i.index)) / items.length) : 0;
  const directions = new Set(items.map((i) => i.trend).filter((t) => t !== null));
  const direction: NonNullable<UsageData['conformance']['direction']> | undefined = directions.size === 0 ? undefined : directions.size > 1 ? 'mixed' : TREND_WORDS[[...directions][0]!];
  const run = one ? latestRun(one.id) : undefined;
  const gap = index - (score ?? 0);
  return {
    scope,
    label: one ? `${one.name} ${one.version}` : 'All services',
    scopeLine: one ? `${one.protocol} · ${one.operations.length} operations · mock ${one.mockUrl}` : `${all.length} services · ${sumOf(all.map((s) => s.operations.length))} operations`,
    dailyStart: isoDay(start),
    daily,
    hourly,
    previous,
    lastCall: latest ? latest.name : '—',
    lastCallAgo: latest?.last,
    rows,
    tableNote: one
      ? 'Microcks keeps invocation counters per service and per day: there is no per-operation counter.'
      : 'Invocation counters of the last 14 days. The 30 days trend is only available for the whole catalogue.',
    conformance: {
      title: one ? 'Conformance' : 'Average conformance',
      score,
      index,
      subtitle: score === null ? 'No test run yet' : `Index ${index}% · ${scored.length}/${items.length} tested`,
      subject: one
        ? run
          ? `${one.name} ${one.version} · latest test #${run.testNumber} · ${run.runnerType} · ${run.demo ? 'demo run' : 'simulated run'}`
          : `${one.name} ${one.version} · never tested`
        : `${items.length} services · latest test of each`,
      trend: direction ? (direction === 'mixed' ? 'services go both ways' : `${direction} since the previous test`) : undefined,
      direction,
      advice: score !== null && gap > 10 ? 'Demo advice: attach more examples to the contract to raise the index, then re-run the test.' : undefined
    },
    today: isoDay(now),
    demo: false
  };
}

export const usageScopes = () => [{ id: 'all', label: 'All services' }, ...listServices().map((s) => ({ id: s.id, label: `${s.name} ${s.version}` }))];
