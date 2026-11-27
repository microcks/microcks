/*
 * Mock invocation counters read from the Microcks backend (`/api/metrics/invocations/*`, `DailyStatistic`).
 * The backend counts per service and per day/hour/minute, in server local time, and has no per-operation counter.
 */
import {
  fetchInvocationsForService,
  fetchInvocationsGlobal,
  fetchInvocationsLatest,
  fetchInvocationsTop,
  type BackendDailyStatistic
} from './backend.js';
import type { Service } from '#lib/types.js';

export const USAGE_DAYS = 84;
const TOP_LIMIT = 500;
const COMPARE_DAYS = 14;

const pad2 = (n: number) => String(n).padStart(2, '0');
export const dayKey = (d: Date) => `${d.getFullYear()}${pad2(d.getMonth() + 1)}${pad2(d.getDate())}`;
export const isoDay = (d: Date) => `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`;
const daysBack = (now: Date, n: number) => new Date(now.getFullYear(), now.getMonth(), now.getDate() - n);
const sumOf = (v: readonly number[]) => v.reduce((a, b) => a + b, 0);

/** Monday 11 weeks before the Monday of the current week: the heatmap origin. */
export function usageStart(now = new Date()): Date {
  const sinceMonday = (now.getDay() + 6) % 7;
  return daysBack(now, sinceMonday + 11 * 7);
}

/** Hourly counters of a statistic, for the hours elapsed so far today. */
function hourlyOf(stat: BackendDailyStatistic | undefined, now: Date): number[] {
  return Array.from({ length: now.getHours() + 1 }, (_, h) => stat?.hourlyCount?.[String(h)] ?? 0);
}

export interface InvocationRow {
  service: Service;
  /** Daily counts, index 0 is today, then the previous days. */
  counts: number[];
}

export interface Invocations {
  start: Date;
  /** One value per day since `start`, the last one is today. */
  daily: number[];
  hourly: number[];
  /** Per service counters; empty for a single service scope. */
  rows: InvocationRow[];
}

const sameService = (s: Service, st: BackendDailyStatistic) => st.serviceName === s.name && st.serviceVersion === s.version;

/** Invocations of all `services` (scope `all`) or of one service. */
export async function loadInvocations(services: Service[], one: Service | undefined, token?: string, now = new Date()): Promise<Invocations> {
  const start = usageStart(now);
  const length = Math.min(USAGE_DAYS, Math.round((daysBack(now, 0).getTime() - start.getTime()) / 86_400_000) + 1);
  const dayAt = (i: number) => dayKey(daysBack(start, -i));
  const today = dayKey(now);

  if (one) {
    const stats = await Promise.all(Array.from({ length }, (_, i) => fetchInvocationsForService(one.name, one.version, dayAt(i), token)));
    return { start, daily: stats.map((s) => s?.dailyCount ?? 0), hourly: hourlyOf(stats[length - 1], now), rows: [] };
  }

  const [latest, global, ...tops] = await Promise.all([
    fetchInvocationsLatest(length - 1, token),
    fetchInvocationsGlobal(today, token),
    ...Array.from({ length: COMPARE_DAYS }, (_, i) => fetchInvocationsTop(dayKey(daysBack(now, i)), TOP_LIMIT, token))
  ]);
  const rows = services.map((service) => ({
    service,
    counts: tops.map((top) => top.find((st) => sameService(service, st))?.dailyCount ?? 0)
  }));
  return { start, daily: Array.from({ length }, (_, i) => latest[dayAt(i)] ?? 0), hourly: hourlyOf(global, now), rows };
}

/** Today's invocations per service id, plus the hourly curve and total, for the dashboard. */
export async function todayInvocations(services: Service[], token?: string, now = new Date()) {
  const day = dayKey(now);
  const [top, global] = await Promise.all([fetchInvocationsTop(day, TOP_LIMIT, token), fetchInvocationsGlobal(day, token)]);
  const byService: Record<string, number> = {};
  for (const s of services) byService[s.id] = top.find((st) => sameService(s, st))?.dailyCount ?? 0;
  return { byService, hourly: hourlyOf(global, now), total: global?.dailyCount ?? sumOf(Object.values(byService)) };
}

/** Daily counts of one service over the last `days` days, oldest first. */
export async function recentCalls(service: Service, days: number, token?: string, now = new Date()): Promise<number[]> {
  const stats = await Promise.all(
    Array.from({ length: days }, (_, i) => fetchInvocationsForService(service.name, service.version, dayKey(daysBack(now, days - 1 - i)), token))
  );
  return stats.map((s) => s?.dailyCount ?? 0);
}
