import type { Protocol } from '@microcks/deep-ink';

export const fmt = (n: number) => n.toLocaleString('en-US');
export const PROTOCOL_ORDER: readonly Protocol[] = ['REST', 'SOAP', 'EVENT', 'gRPC', 'GRAPHQL', 'DIRECT', 'A2A'];
export const pluralize = (n: number, one: string, many = `${one}s`) => `${fmt(n)} ${n === 1 ? one : many}`;

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/** "just now", "45 min ago", "2 h ago", "yesterday", "3 d ago", "2 wk ago", "3 mo ago". */
export function formatRelative(date: string | Date, now: Date = new Date()): string {
  const diff = now.getTime() - new Date(date).getTime();
  if (diff < MINUTE) return 'just now';
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)} min ago`;
  if (diff < DAY) return `${Math.floor(diff / HOUR)} h ago`;
  if (diff < 2 * DAY) return 'yesterday';
  if (diff < 7 * DAY) return `${Math.floor(diff / DAY)} d ago`;
  if (diff < 30 * DAY) return `${Math.floor(diff / (7 * DAY))} wk ago`;
  return `${Math.floor(diff / (30 * DAY))} mo ago`;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const two = (n: number) => String(n).padStart(2, '0');

/** "Nov 3, 09:10" (UTC). */
export function formatShortClock(date: string | Date): string {
  const d = new Date(date);
  return `${MONTHS[d.getUTCMonth()]} ${d.getUTCDate()}, ${two(d.getUTCHours())}:${two(d.getUTCMinutes())}`;
}

/** "Nov 3, 2026". */
export function formatDay(date: string | Date): string {
  const d = new Date(date);
  return `${MONTHS[d.getUTCMonth()]} ${d.getUTCDate()}, ${d.getUTCFullYear()}`;
}

export const formatHourMinute = (date: string | Date) => {
  const d = new Date(date);
  return `${two(d.getUTCHours())}:${two(d.getUTCMinutes())}`;
};

export const operationUnit = (protocol: Protocol): 'skill' | 'operation' => (protocol === 'A2A' ? 'skill' : 'operation');
