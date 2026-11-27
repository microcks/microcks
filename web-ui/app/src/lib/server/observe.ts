import { listServices } from '#lib/server/store.js';
import type { TraceCall, TraceTopology } from '#lib/components/observe/traces.js';
import { buildTopology, historyCalls, HISTORY_LENGTH, makeCall, TRACE_TICK_MS, type TraceContext } from '#lib/placeholders/observe.js';

export { HISTORY_LENGTH, TRACE_TICK_MS };

/**
 * Resolves the real services the placeholder traffic refers to: a Pastry-like REST service (GET list, GET item,
 * PATCH item) and, when present, the first event service of the store.
 */
export function traceContext(): TraceContext | undefined {
  const services = listServices();
  const rest =
    services.find((s) => s.protocol === 'REST' && /pastry/i.test(s.name) && s.operations.some((o) => o.method === 'PATCH')) ??
    services.find((s) => s.protocol === 'REST' && /pastr/i.test(s.name)) ??
    services.find((s) => s.protocol === 'REST' && s.operations.length >= 2);
  if (!rest) return undefined;
  const ops = rest.operations.filter((o) => o.resource);
  const itemOp = ops.find((o) => o.method === 'GET' && o.resource!.includes('{')) ?? ops[0];
  const listOp = ops.find((o) => o.method === 'GET' && o !== itemOp && !o.resource!.includes('{')) ?? ops[0];
  const event = services.find((s) => s.protocol === 'EVENT');
  const base = rest.mockUrl.startsWith('/') ? rest.mockUrl : (rest.mockUrl.match(/\/rest\/.*/)?.[0] ?? `/rest/${rest.name}/${rest.version}`);
  const eventOp = event?.operations[0];
  return {
    restServiceId: rest.id,
    restServiceName: `${rest.name} ${rest.version}`,
    restBase: base,
    mcpPath: base.replace('/rest/', '/mcp/'),
    listResource: listOp.resource!,
    itemResource: itemOp.resource!.includes('{') ? itemOp.resource! : `${itemOp.resource}/{name}`,
    eventServiceId: event?.id,
    eventServiceName: event ? `${event.name} ${event.version}` : undefined,
    topic: event ? `${event.name.replace(/\s+/g, '')}-${event.version}-${(eventOp?.resource ?? 'events').replace(/\//g, '-')}` : undefined,
    eventLabel: eventOp?.resource ?? eventOp?.name
  };
}

export function getTopology(): TraceTopology | undefined {
  const ctx = traceContext();
  if (!ctx) return undefined;
  return { ...buildTopology(ctx), history: historyCalls(ctx) };
}

let replays = 0;

/** A replayed call is the same traffic stamped now, in its own number range. */
export function replayCall(callId: string): TraceCall | undefined {
  const ctx = traceContext();
  const original = /^call-(\d+)$/.exec(callId)?.[1];
  if (!ctx || !original) return undefined;
  replays += 1;
  const seq = 1000 + replays;
  return { ...makeCall(Number(original), ctx, new Date()), seq, callId: `call-${seq}` };
}

/** Next streamed call: numbers continue after the history. */
export function streamCall(tick: number): TraceCall | undefined {
  const ctx = traceContext();
  return ctx ? makeCall(HISTORY_LENGTH + tick + 1, ctx, new Date()) : undefined;
}

