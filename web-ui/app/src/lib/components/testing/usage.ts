/*
 * Usage & conformance view model: the pure logic behind the /metrics screen. The raw series (`UsageData`) are
 * served by the BFF, derived from the Microcks `DailyStatistic` (invocations) and `TestConformanceMetric`
 * (conformance) documents; see `lib/server/testing.ts`.
 */

export type UsagePeriod = 'today' | '7d' | '30d';
export const USAGE_PERIODS: { id: UsagePeriod; label: string }[] = [
  { id: 'today', label: 'Today' },
  { id: '7d', label: '7 days' },
  { id: '30d', label: '30 days' }
];

export type ServiceProtocol = 'REST' | 'SOAP' | 'EVENT' | 'gRPC' | 'GRAPHQL' | 'DIRECT' | 'A2A';

export interface UsageRowData {
  id: string;
  name: string;
  protocol?: ServiceProtocol;
  method?: string;
  /** Dispatcher (operations) or operation count (services). */
  detail: string;
  today: number;
  w7: number;
  /** Trend vs the previous period, percent, by period. */
  trend: Record<UsagePeriod, number>;
  last: string;
  live: boolean;
}

export interface ConformanceItem {
  serviceId: string;
  name: string;
  protocol: ServiceProtocol;
  operations: number;
  /** Latest test score, 0..100, null when never tested. */
  score: number | null;
  /** Conformance index (coverage of the contract by examples), 0..100. */
  index: number;
  trend: 'up' | 'down' | 'stable' | null;
}

export interface DomainConformance {
  /** Value of the `domain` label, `other` when the service has none. */
  domain: string;
  /** Visible group name: "domain=pastry", or "no domain label". */
  label: string;
  score: number | null;
  services: number;
  items?: ConformanceItem[];
}

export interface UsageConformance {
  title: string;
  score: number | null;
  index?: number;
  subtitle: string;
  subject: string;
  trend?: string;
  direction?: 'up' | 'down' | 'stable' | 'mixed';
  advice?: string;
}

/** Raw usage series of a scope (`all` or a service id). */
export interface UsageData {
  scope: string;
  label: string;
  scopeLine: string;
  /** ISO date of the first daily value (a Monday). */
  dailyStart: string;
  /** One value per day, the last one is today (partial). */
  daily: number[];
  /** Hourly values of today so far. */
  hourly: number[];
  /** Totals of the comparison period, by period. */
  previous: Record<UsagePeriod, number>;
  lastCall: string;
  lastCallAgo?: string;
  rows: UsageRowData[];
  tableNote: string;
  conformance: UsageConformance;
  /** Today's date (UTC) and its last hour, for the captions. */
  today: string;
  /** Always true until a source of real invocation counters is wired. */
  demo: boolean;
}

export interface UsagePoint {
  name: string;
  tick: string;
  value: number;
  future: boolean;
}

export interface HeatCell {
  index: number;
  name: string;
  value: number;
  level: 0 | 1 | 2 | 3;
  week: number;
  weekday: number;
}

export interface UsageTableRow {
  id: string;
  name: string;
  protocol?: ServiceProtocol;
  method?: string;
  detail: string;
  calls: number;
  share: number;
  width: number;
  trend: number;
  last: string;
  live: boolean;
}

export interface UsageView {
  scope: string;
  label: string;
  scopeLine: string;
  period: UsagePeriod;
  periodLabel: string;
  demo: boolean;
  chart: { title: string; subtitle: string; unit: 'hour' | 'day'; points: UsagePoint[]; max: number; defaultSelected: number; summary: string };
  kpis: { total: number; growth: number; trendText: string; peakTitle: string; peak: number; peakWhen: string; lastCall: string; lastCallAgo: string };
  heatmap: { cells: HeatCell[]; months: string[]; subtitle: string; todayNote: string; total: number; weekdayAverage: number; busiest: string; defaultSelected: number };
  table: { title: string; subtitle: string; firstColumn: string; secondColumn: string; rows: UsageTableRow[]; total: number; trend: number; note: string };
  conformance: UsageConformance;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const LONG_WEEKDAYS = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const DAY_MS = 86_400_000;
const sum = (values: readonly number[]) => values.reduce((a, b) => a + b, 0);
const two = (n: number) => String(n).padStart(2, '0');

export const formatCount = (n: number) => n.toLocaleString('en-US');
export const formatTrend = (pct: number) => (pct > 0 ? `+${pct}%` : `${pct}%`);
export const trendPercent = (total: number, previous: number) => (previous === 0 ? 0 : Math.round(((total - previous) / previous) * 100));

/** Rounds a chart maximum up to 1, 2, 5 (x10^n) steps so the grid lines are readable. */
export function niceMax(max: number): number {
  if (max <= 0) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(max));
  const step = max / magnitude >= 5 ? magnitude : magnitude / 2;
  return Math.ceil(max / step) * step;
}

export function heatLevel(value: number, max: number): 0 | 1 | 2 | 3 {
  const ratio = max === 0 ? 0 : value / max;
  return ratio < 0.25 ? 0 : ratio < 0.5 ? 1 : ratio < 0.75 ? 2 : 3;
}

const dayName = (startMs: number, index: number) => {
  const d = new Date(startMs + index * DAY_MS);
  return `${WEEKDAYS[d.getUTCDay()]}, ${MONTHS[d.getUTCMonth()]} ${d.getUTCDate()}`;
};
const shortDate = (startMs: number, index: number) => {
  const d = new Date(startMs + index * DAY_MS);
  return `${MONTHS[d.getUTCMonth()]} ${d.getUTCDate()}`;
};
const hourName = (h: number) => `${two(h)}:00–${two(h + 1)}:00`;

/** Composes everything the Usage page shows from raw series. */
export function buildUsageView(data: UsageData, period: UsagePeriod): UsageView {
  const startMs = Date.parse(`${data.dailyStart}T00:00:00Z`);
  const n = data.daily.length;
  const periodLabel = { today: 'today', '7d': '7 days', '30d': '30 days' }[period];
  const one = data.scope !== 'all';
  const today = new Date(`${data.today}T00:00:00Z`);
  const todayName = `${LONG_WEEKDAYS[today.getUTCDay()]}, ${MONTHS[today.getUTCMonth()]} ${today.getUTCDate()}`;
  let points: UsagePoint[];
  if (period === 'today') {
    points = Array.from({ length: 24 }, (_, h) => ({ name: hourName(h), tick: h % 3 === 0 ? `${two(h)}h` : '', value: data.hourly[h] ?? 0, future: h >= data.hourly.length }));
  } else {
    const count = period === '7d' ? 7 : 30;
    points = data.daily.slice(n - count).map((value, i) => {
      const day = n - count + i;
      const weekday = WEEKDAYS[new Date(startMs + day * DAY_MS).getUTCDay()] ?? '';
      return { name: dayName(startMs, day), tick: count === 7 ? weekday : (count - 1 - i) % 5 === 0 ? shortDate(startMs, day) : '', value, future: false };
    });
  }
  const real = points.filter((p) => !p.future);
  const peakValue = Math.max(0, ...real.map((p) => p.value));
  const peakPoint = real.find((p) => p.value === peakValue);
  const total = sum(real.map((p) => p.value));
  const growth = trendPercent(total, data.previous[period]);
  const unit = period === 'today' ? 'hour' : 'day';
  const range = `${points[0]?.name ?? ''} to ${points[points.length - 1]?.name ?? ''}`;
  const untilHour = `${two(data.hourly.length - 1)}:00`;

  const max = Math.max(0, ...data.daily);
  const cells: HeatCell[] = data.daily.map((value, index) => ({ index, name: dayName(startMs, index), value, level: heatLevel(value, max), week: Math.floor(index / 7), weekday: index % 7 }));
  const weeks = Math.ceil(n / 7);
  let lastMonth = -1;
  const months = Array.from({ length: weeks }, (_, w) => {
    const m = new Date(startMs + w * 7 * DAY_MS).getUTCMonth();
    const label = m !== lastMonth ? (MONTHS[m] ?? '') : '';
    lastMonth = m;
    return label;
  });
  const weekdayValues = data.daily.filter((_, d) => d % 7 < 5);
  const busiestIndex = data.daily.indexOf(max);

  const t30 = sum(data.daily.slice(n - 30));
  const w7Total = sum(data.rows.map((r) => r.w7)) || 1;
  let allocated = 0;
  const m30 = data.rows.map((r, i, all) => {
    if (i === all.length - 1) return t30 - allocated;
    const value = Math.round((t30 * r.w7) / w7Total);
    allocated += value;
    return value;
  });
  const counted = data.rows.map((r, i) => ({ r, calls: period === 'today' ? r.today : period === '7d' ? r.w7 : (m30[i] ?? 0) }));
  const tableTotal = sum(counted.map((c) => c.calls));
  const rowMax = Math.max(1, ...counted.map((c) => c.calls));
  const rows: UsageTableRow[] = counted
    .map(({ r, calls }) => ({
      id: r.id,
      name: r.name,
      protocol: r.protocol,
      method: r.method,
      detail: r.detail,
      calls,
      share: tableTotal === 0 ? 0 : Math.round((calls / tableTotal) * 100),
      width: Math.max(1, (calls / rowMax) * 100),
      trend: r.trend[period],
      last: r.last,
      live: r.live
    }))
    .sort((a, b) => b.calls - a.calls);

  return {
    scope: data.scope,
    label: data.label,
    scopeLine: data.scopeLine,
    period,
    periodLabel,
    demo: data.demo,
    chart: {
      title: period === 'today' ? 'Invocations per hour · today' : `Invocations per day · last ${periodLabel}`,
      subtitle: period === 'today' ? `${todayName} · until ${untilHour}, grey slots are still to come` : range,
      unit,
      points,
      max: niceMax(peakValue),
      defaultSelected: Math.max(0, real.length - 1),
      summary: `Invocations per ${unit} for ${one ? data.label : 'all services'}. Peak ${formatCount(peakValue)}.`
    },
    kpis: {
      total,
      growth,
      trendText: `${formatTrend(growth)} ${period === 'today' ? `vs last ${LONG_WEEKDAYS[today.getUTCDay()]}, same time` : `vs previous ${periodLabel}`}`,
      peakTitle: period === 'today' ? 'Peak hour' : 'Busiest day',
      peak: peakValue,
      peakWhen: `${peakPoint?.name ?? ''}${period === 'today' ? '' : ` · daily average ${formatCount(Math.round(total / Math.max(1, real.length)))}`}`,
      lastCall: data.lastCall,
      lastCallAgo: data.lastCallAgo ?? ''
    },
    heatmap: {
      cells,
      months,
      todayNote: `(today, until ${untilHour})`,
      subtitle: `${one ? data.label : 'All services'} · ${shortDate(startMs, 0)} to ${shortDate(startMs, n - 1)} · select a day to read its count`,
      total: sum(data.daily),
      weekdayAverage: Math.round(sum(weekdayValues) / Math.max(1, weekdayValues.length)),
      busiest: `${shortDate(startMs, busiestIndex)} · ${formatCount(max)}`,
      defaultSelected: n - 1
    },
    table: {
      title: one ? 'Invocations per operation' : 'Invocations per service',
      subtitle: `${one ? data.label : 'All services'} · ${periodLabel}`,
      firstColumn: one ? 'Operation' : 'Service',
      secondColumn: one ? 'Dispatcher' : 'Operations',
      rows,
      total: tableTotal,
      trend: growth,
      note: data.tableNote
    },
    conformance: data.conformance
  };
}

/** Text equivalent of the heatmap: one row per week, one column per weekday. */
export function heatmapWeeks(cells: readonly HeatCell[]): { week: string; values: (number | null)[] }[] {
  const weeks: { week: string; values: (number | null)[] }[] = [];
  for (const c of cells) {
    let row = weeks[c.week];
    if (!row) {
      row = { week: c.name.split(', ')[1] ?? '', values: Array<number | null>(7).fill(null) };
      weeks[c.week] = row;
    }
    row.values[c.weekday] = c.value;
  }
  return weeks;
}

export const WEEKDAY_HEADERS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'] as const;

// ── Conformance ──

export type ConformanceBandName = 'ok' | 'partial' | 'risk' | 'untested';

export const conformanceBand = (score: number | null): ConformanceBandName => (score === null ? 'untested' : score >= 80 ? 'ok' : score >= 50 ? 'partial' : 'risk');

export const BAND_TEXT: Record<ConformanceBandName, string> = { ok: 'conformant', partial: 'partial', risk: 'at risk', untested: 'never tested' };

export interface TreemapDomain extends DomainConformance {
  operations: number;
}
export interface TreemapRow {
  operations: number;
  domains: TreemapDomain[];
}

const opsOf = (items: readonly ConformanceItem[] | undefined) => (items ?? []).reduce((n, i) => n + i.operations, 0);

/** Two rows, biggest first: the two largest domains share the first row, the others the second. */
export function treemapRows(domains: readonly DomainConformance[]): TreemapRow[] {
  const sized = domains
    .map((d) => ({ ...d, items: [...(d.items ?? [])].sort((a, b) => b.operations - a.operations), operations: opsOf(d.items) }))
    .sort((a, b) => b.operations - a.operations);
  return [sized.slice(0, 2), sized.slice(2)].filter((r) => r.length > 0).map((r) => ({ operations: r.reduce((n, d) => n + d.operations, 0), domains: r }));
}

/** Services sorted by the gap between their index and their score; never tested last. */
export function gapList(domains: readonly DomainConformance[]): ConformanceItem[] {
  const gap = (i: ConformanceItem) => i.index - (i.score ?? 0);
  return domains
    .flatMap((d) => d.items ?? [])
    .sort((a, b) => Number(a.score === null) - Number(b.score === null) || gap(b) - gap(a) || (a.protocol < b.protocol ? -1 : a.protocol > b.protocol ? 1 : 0));
}
