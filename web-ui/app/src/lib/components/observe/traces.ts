import type { Protocol } from '#lib/types.js';

/** Which way a mock call went: a REST call, an MCP tool call or an event. */
export type TraceKind = 'REST' | 'MCP' | 'EVENT';

/** One mock call, as shown in Live traces. */
export interface TraceCall {
  seq: number;
  callId: string;
  at: string;
  serviceId: string;
  serviceName: string;
  protocol: Protocol;
  method: string;
  path: string;
  status: number;
  ms: number;
  exampleName?: string;
  operationId?: string;
  /** W3C baggage when the call belongs to an Arazzo run. */
  baggage?: string;
  arazzoRun?: number;
  workflowName?: string;
  kind: TraceKind;
  clientId: string;
  clientName: string;
  clientIp: string;
  endpointId: string;
  endpointLabel: string;
  dispatcher: string;
  criteria: string;
  exampleId: string;
  mcpTool?: string;
  /** Verification session header (`x-microcks-session`). */
  sessionId?: string;
  rawRequest: string;
  rawResponse: string;
}

export interface TopologyClient {
  id: string;
  label: string;
  ip: string;
  detail: string;
  kind: 'REST' | 'MCP' | 'MIXED' | 'ARAZZO';
}

export interface TopologyEndpoint {
  id: string;
  serviceId: string;
  method: string;
  label: string;
  detail: string;
  dispatcher: string;
  kinds: TraceKind[];
}

export interface TopologyExample {
  id: string;
  label: string;
  detail: string;
  /** `200`, `400` or `msg`. */
  code: string;
  ok: boolean;
}

export interface TopologyEdge {
  id: string;
  from: string;
  to: string;
  kind?: TraceKind;
  /** Calls in the last hour when the page opens. */
  base: number;
}

export interface TraceTopology {
  clients: TopologyClient[];
  endpoints: TopologyEndpoint[];
  examples: TopologyExample[];
  clientEdges: TopologyEdge[];
  exampleEdges: TopologyEdge[];
  history: TraceCall[];
}

export interface TraceFilter {
  serviceId?: string;
  kind?: TraceKind;
  status?: 'ok' | 'error';
  arazzoOnly?: boolean;
  runId?: string;
}

export interface FlowCounts {
  clientEdge: Record<string, number>;
  exampleEdge: Record<string, number>;
}

/** Value of `?run=`: a run id, or the whole W3C baggage string copied from a run page. */
export function parseRunParam(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  let trimmed = value.trim();
  try {
    trimmed = decodeURIComponent(trimmed);
  } catch {
    return undefined;
  }
  const id = /microcks-arazzo-run=([^,;\s]+)/.exec(trimmed)?.[1] ?? trimmed;
  return /^[A-Za-z0-9_-]+$/.test(id) ? id : undefined;
}

export const arazzoRunId = (c: Pick<TraceCall, 'baggage'>) =>
  c.baggage ? /microcks-arazzo-run=([^,;\s]+)/.exec(c.baggage)?.[1] : undefined;
export const arazzoStep = (c: Pick<TraceCall, 'baggage'>) =>
  c.baggage ? /microcks-arazzo-step=([^,;\s]+)/.exec(c.baggage)?.[1] : undefined;
export const arazzoWorkflow = (c: Pick<TraceCall, 'baggage'>) =>
  c.baggage ? /microcks-arazzo-workflow=([^,;\s]+)/.exec(c.baggage)?.[1] : undefined;
export const arazzoAttempt = (c: Pick<TraceCall, 'baggage'>) =>
  c.baggage ? /microcks-arazzo-attempt=(\d+)/.exec(c.baggage)?.[1] : undefined;

export function arazzoTitle(c: TraceCall): string {
  return `From workflow ${c.workflowName ?? arazzoWorkflow(c) ?? ''} · run #${c.arazzoRun ?? ''} · step ${arazzoStep(c) ?? ''} · attempt ${arazzoAttempt(c) ?? '1'}`;
}

export function matchesTraceFilter(call: TraceCall, f: TraceFilter): boolean {
  if (f.serviceId && call.serviceId !== f.serviceId) return false;
  if (f.kind && call.kind !== f.kind) return false;
  if (f.status === 'ok' && call.status >= 400) return false;
  if (f.status === 'error' && call.status < 400) return false;
  if (f.arazzoOnly && !call.baggage && !call.sessionId) return false;
  if (f.runId && arazzoRunId(call) !== f.runId) return false;
  return true;
}

export const isMiss = (call: Pick<TraceCall, 'status'>) => call.status >= 400;

/** Words for the status column: never only a colour. */
export function statusLabel(call: Pick<TraceCall, 'status' | 'kind'>): string {
  if (call.kind === 'EVENT') return 'delivered';
  if (call.status === 200) return '200 OK';
  if (call.status === 400) return '400 Bad Request';
  return String(call.status);
}

export function clockTime(iso: string): string {
  const d = new Date(iso);
  const two = (n: number, w = 2) => String(n).padStart(w, '0');
  return `${two(d.getUTCHours())}:${two(d.getUTCMinutes())}:${two(d.getUTCSeconds())}.${two(d.getUTCMilliseconds(), 3)}`;
}

const hex = (n: number) => ((n * 2654435761) >>> 0).toString(16).padStart(8, '0');

/** Deterministic W3C trace and span ids for a call. */
export function traceIds(seq: number): { traceId: string; spanId: string } {
  return { traceId: hex(seq) + hex(seq + 7) + hex(seq + 13) + hex(seq + 29), spanId: hex(seq + 3) + hex(seq + 5) };
}

/** Span events of a call (what Microcks did), in order. */
export function traceEvents(call: TraceCall): { at: string; text: string }[] {
  const base = new Date(call.at).getTime();
  const at = (offset: number) => clockTime(new Date(base + offset).toISOString());
  const events =
    call.kind === 'EVENT'
      ? [
          { at: at(0), text: `message produced · SUBSCRIBE ${call.endpointLabel}` },
          { at: at(1), text: `example selected · ${call.exampleName ?? 'none'}` },
          { at: at(1), text: `published to ${call.path}` }
        ]
      : [
          {
            at: at(0),
            text: `invocation received · ${call.kind === 'MCP' ? `tools/call ${call.mcpTool ?? ''}` : `${call.method} ${call.endpointLabel}`}`
          },
          { at: at(1), text: `dispatcher ${call.dispatcher.split(' ·')[0]} · criteria ${call.criteria}` },
          { at: at(1), text: isMiss(call) ? 'no response found for criteria' : `response selected · ${call.exampleName ?? 'none'}` },
          { at: at(call.ms), text: `response sent · ${call.status}` }
        ];
  const run = call.baggage
    ? /microcks-arazzo-workflow=([^,]+),microcks-arazzo-step=([^,]+),microcks-arazzo-attempt=(\d+)/.exec(call.baggage)
    : null;
  if (run) events.splice(1, 0, { at: at(0), text: `baggage · workflow ${run[1]} · step ${run[2]} · attempt ${run[3]}` });
  if (call.sessionId) events.splice(1, 0, { at: at(0), text: `session ${call.sessionId} · answered along restock-pastry` });
  return events;
}

/** Base counts of the topology plus the calls streamed since the page opened. */
export function flowCounts(topology: TraceTopology, streamed: readonly TraceCall[]): FlowCounts {
  const clientEdge: Record<string, number> = {};
  const exampleEdge: Record<string, number> = {};
  for (const e of topology.clientEdges) clientEdge[`${e.from}>${e.to}`] = e.base;
  for (const e of topology.exampleEdges) exampleEdge[`${e.from}>${e.to}`] = e.base;
  for (const c of streamed) {
    const k1 = `${c.clientId}>${c.endpointId}`;
    const k2 = `${c.endpointId}>${c.exampleId}`;
    clientEdge[k1] = (clientEdge[k1] ?? 0) + 1;
    exampleEdge[k2] = (exampleEdge[k2] ?? 0) + 1;
  }
  return { clientEdge, exampleEdge };
}

/** The path of a REST call without the mock base: the resource part of the URL, query included. */
export function shortPath(c: Pick<TraceCall, 'path' | 'endpointLabel'>): string {
  const prefix = c.endpointLabel.split('{')[0].replace(/\/$/, '');
  const at = prefix ? c.path.lastIndexOf(prefix) : -1;
  return at >= 0 ? c.path.slice(at) : c.path;
}
