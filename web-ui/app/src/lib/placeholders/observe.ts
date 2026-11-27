/*
 * PLACEHOLDER: Live traces data.
 * Microcks has no API that streams individual mock invocations with their client, dispatch criteria and matched
 * example. Only aggregated counters exist (GET /api/metrics/invocations/{serviceName}/{version}, see
 * MetricsController). Everything below is synthetic: the call graph base counts, the client list, the request and
 * response payloads and the ticking stream. Replace `buildTopology`, `makeCall` and the stream in
 * lib/server/observe.ts with a Microcks tracing/invocation API when one exists. The services and operations
 * the calls refer to are real (from the store); the traffic is not.
 */
import type {
  TopologyClient,
  TopologyEdge,
  TopologyEndpoint,
  TopologyExample,
  TraceCall,
  TraceKind
} from '#lib/components/observe/traces.js';

export interface TraceContext {
  restServiceId: string;
  restServiceName: string;
  restBase: string;
  mcpPath: string;
  listResource: string;
  itemResource: string;
  /** Present only when the store holds an event service. */
  eventServiceId?: string;
  eventServiceName?: string;
  topic?: string;
  eventLabel?: string;
}

export const HISTORY_LENGTH = 10;
export const TRACE_TICK_MS = 3200;
export const SESSION_ID = 's-7f3a2c91';
export const RUN_IDS: Record<number, string> = { 12: '67290c5a9d3e41b7a6f8e215', 14: '6729a1d27e4c0b5f93a8c214' };
const ARAZZO_WORKFLOW = 'restock-pastry';

export const TOPOLOGY_CLIENTS: TopologyClient[] = [
  { id: 'c1', label: 'checkout-web', ip: '10.0.4.21', detail: '10.0.4.21 · HTTP', kind: 'REST' },
  { id: 'c3', label: 'copilot-agent (MCP)', ip: '10.0.9.3', detail: '10.0.9.3 · MCP client', kind: 'MCP' },
  { id: 'c2', label: 'order-service', ip: '10.0.7.12', detail: '10.0.7.12 · HTTP + Kafka', kind: 'MIXED' },
  { id: 'c4', label: 'microcks-arazzo', ip: '10.0.2.8', detail: 'Arazzo runs · HTTP', kind: 'ARAZZO' }
];

const CLIENT_EDGES: TopologyEdge[] = [
  { id: 'c1e1', from: 'c1', to: 'e1', kind: 'REST', base: 412 },
  { id: 'c1e2', from: 'c1', to: 'e2', kind: 'REST', base: 96 },
  { id: 'c3e1', from: 'c3', to: 'e1', kind: 'MCP', base: 38 },
  { id: 'c3e2', from: 'c3', to: 'e2', kind: 'MCP', base: 12 },
  { id: 'c2e3', from: 'c2', to: 'e3', kind: 'REST', base: 41 },
  { id: 'c2e4', from: 'c2', to: 'e4', kind: 'EVENT', base: 300 },
  { id: 'c4e2', from: 'c4', to: 'e2', kind: 'REST', base: 4 },
  { id: 'c4e1', from: 'c4', to: 'e1', kind: 'REST', base: 8 }
];

const EXAMPLE_EDGES: TopologyEdge[] = [
  { id: 'e1d1', from: 'e1', to: 'd1', base: 268 },
  { id: 'e1d2', from: 'e1', to: 'd2', base: 159 },
  { id: 'e1d6', from: 'e1', to: 'd6', base: 31 },
  { id: 'e2d3', from: 'e2', to: 'd3', base: 112 },
  { id: 'e3d4', from: 'e3', to: 'd4', base: 41 },
  { id: 'e4d5', from: 'e4', to: 'd5', base: 300 }
];

export function buildTopology(ctx: TraceContext) {
  const hasEvents = Boolean(ctx.eventServiceId);
  const svc = `${ctx.restServiceName}`;
  const endpoints: TopologyEndpoint[] = [
    { id: 'e1', serviceId: ctx.restServiceId, method: 'GET', label: ctx.itemResource, detail: `${svc} · URI_PARTS`, dispatcher: 'URI_PARTS · rule: name', kinds: ['REST', 'MCP'] },
    { id: 'e2', serviceId: ctx.restServiceId, method: 'GET', label: ctx.listResource, detail: `${svc} · URI_PARAMS`, dispatcher: 'URI_PARAMS · rule: size', kinds: ['REST', 'MCP'] },
    { id: 'e3', serviceId: ctx.restServiceId, method: 'PATCH', label: ctx.itemResource, detail: `${svc} · URI_PARTS`, dispatcher: 'URI_PARTS · rule: name', kinds: ['REST'] }
  ];
  const examples: TopologyExample[] = [
    { id: 'd1', label: 'Millefeuille', detail: '/name=Millefeuille', code: '200', ok: true },
    { id: 'd2', label: 'Eclair Cafe', detail: '/name=Eclair Cafe', code: '200', ok: true },
    { id: 'd6', label: 'No matching example', detail: 'unknown name', code: '400', ok: false },
    { id: 'd3', label: 'Size M', detail: '?size=M', code: '200', ok: true },
    { id: 'd4', label: 'Eclair Chocolat · price', detail: '/name=Eclair Chocolat', code: '200', ok: true }
  ];
  if (hasEvents) {
    endpoints.push({
      id: 'e4',
      serviceId: ctx.eventServiceId!,
      method: 'SUB',
      label: ctx.eventLabel ?? 'events',
      detail: `${ctx.eventServiceName} · Kafka`,
      dispatcher: 'none · published every 12 s',
      kinds: ['EVENT']
    });
    examples.push({ id: 'd5', label: 'Event payload', detail: 'Kafka message', code: 'msg', ok: true });
  }
  const keep = <T extends TopologyEdge>(edges: T[]) => edges.filter((e) => hasEvents || (e.to !== 'e4' && e.to !== 'd5'));
  return { clients: TOPOLOGY_CLIENTS, endpoints, examples, clientEdges: keep(CLIENT_EDGES), exampleEdges: keep(EXAMPLE_EDGES) };
}

const PASTRIES = {
  mille: { name: 'Millefeuille', description: 'Delicious Millefeuille, not caloric at all', size: 'L', price: 4.4, status: 'available' },
  cafe: { name: 'Eclair Cafe', description: 'Delicious Coffee Eclair, not caloric at all', size: 'M', price: 2.5, status: 'available' },
  choco: { name: 'Eclair Chocolat', description: 'Delicious Chocolate Eclair, not caloric at all', size: 'M', price: 2.4, status: 'unknown' }
};

const js = (value: unknown) => JSON.stringify(value, null, 2);
const httpReq = (method: string, path: string, agent: string, extra = '') =>
  `${method} ${path} HTTP/1.1\nHost: microcks.local:8080\nAccept: application/json\nUser-Agent: ${agent}${extra}`;
const httpRes = (code: string, body: string) =>
  `HTTP/1.1 ${code}\nContent-Type: application/json\nContent-Length: ${body.length}${body ? `\n\n${body}` : ''}`;

interface Template {
  clientId: string;
  endpointId: string;
  exampleId: string;
  exampleName: string;
  kind: TraceKind;
  method: string;
  /** Path under the REST base, or the MCP/topic path. */
  path: (c: TraceContext) => string;
  status: number;
  criteria: string;
  latency: number;
  mcpTool?: string;
  arazzo?: { workflow: string; step: string };
  session?: string;
  rawRequest: (c: TraceContext) => string;
  rawResponse: (c: TraceContext) => string;
}

const item = (c: TraceContext, name: string) => `${c.restBase}${c.itemResource.replace('{name}', encodeURIComponent(name))}`;
const list = (c: TraceContext, query: string) => `${c.restBase}${c.listResource}${query}`;
const mcpReq = (c: TraceContext, tool: string, args: unknown) =>
  `POST ${c.mcpPath} HTTP/1.1\nHost: microcks.local:8080\nContent-Type: application/json\nMcp-Session-Id: 7f3c9a1e\n\n${js({ jsonrpc: '2.0', id: 42, method: 'tools/call', params: { name: tool, arguments: args } })}`;
const mcpRes = (payload: unknown) =>
  `HTTP/1.1 200 OK\nContent-Type: application/json\n\n${js({ jsonrpc: '2.0', id: 42, result: { content: [{ type: 'text', text: JSON.stringify(payload) }], isError: false } })}`;

const getItem = (name: string, agent: string, pastry: unknown, extra = ''): Pick<Template, 'rawRequest' | 'rawResponse' | 'path'> => ({
  path: (c) => item(c, name),
  rawRequest: (c) => httpReq('GET', item(c, name), agent, extra),
  rawResponse: () => httpRes('200 OK', js(pastry))
});
const getList = (agent: string, extra = ''): Pick<Template, 'rawRequest' | 'rawResponse' | 'path'> => ({
  path: (c) => list(c, '?size=M'),
  rawRequest: (c) => httpReq('GET', list(c, '?size=M'), agent, extra),
  rawResponse: () => httpRes('200 OK', js([PASTRIES.cafe, PASTRIES.choco]))
});

const TEMPLATES: Record<string, Template> = {
  'arazzo-list': { clientId: 'c4', endpointId: 'e2', exampleId: 'd3', exampleName: 'Size M', kind: 'REST', method: 'GET', status: 200, criteria: '?size=M', latency: 4, arazzo: { workflow: ARAZZO_WORKFLOW, step: 'listPastries' }, ...getList('microcks-arazzo') },
  'arazzo-get': { clientId: 'c4', endpointId: 'e1', exampleId: 'd2', exampleName: 'Eclair Cafe', kind: 'REST', method: 'GET', status: 200, criteria: '/name=Eclair Cafe', latency: 3, arazzo: { workflow: ARAZZO_WORKFLOW, step: 'getPastry' }, ...getItem('Eclair Cafe', 'microcks-arazzo', PASTRIES.cafe) },
  'session-size': { clientId: 'c1', endpointId: 'e2', exampleId: 'd3', exampleName: 'Size M', kind: 'REST', method: 'GET', status: 200, criteria: '?size=M', latency: 5, session: SESSION_ID, ...getList('checkout-web/3.4.1', `\nx-microcks-session: ${SESSION_ID}`) },
  'mille-web': { clientId: 'c1', endpointId: 'e1', exampleId: 'd1', exampleName: 'Millefeuille', kind: 'REST', method: 'GET', status: 200, criteria: '/name=Millefeuille', latency: 4, ...getItem('Millefeuille', 'checkout-web/3.4.1', PASTRIES.mille) },
  'cafe-web': { clientId: 'c1', endpointId: 'e1', exampleId: 'd2', exampleName: 'Eclair Cafe', kind: 'REST', method: 'GET', status: 200, criteria: '/name=Eclair Cafe', latency: 3, ...getItem('Eclair Cafe', 'checkout-web/3.4.1', PASTRIES.cafe) },
  'size-web': { clientId: 'c1', endpointId: 'e2', exampleId: 'd3', exampleName: 'Size M', kind: 'REST', method: 'GET', status: 200, criteria: '?size=M', latency: 5, ...getList('checkout-web/3.4.1') },
  'tatin-web': {
    clientId: 'c1', endpointId: 'e1', exampleId: 'd6', exampleName: 'No matching example', kind: 'REST', method: 'GET', status: 400, criteria: '/name=Tarte Tatin', latency: 2,
    path: (c) => item(c, 'Tarte Tatin'), rawRequest: (c) => httpReq('GET', item(c, 'Tarte Tatin'), 'checkout-web/3.4.1'), rawResponse: () => httpRes('400 Bad Request', '')
  },
  'patch-order': {
    clientId: 'c2', endpointId: 'e3', exampleId: 'd4', exampleName: 'Eclair Chocolat · price', kind: 'REST', method: 'PATCH', status: 200, criteria: '/name=Eclair Chocolat', latency: 6,
    path: (c) => item(c, 'Eclair Chocolat'),
    rawRequest: (c) => httpReq('PATCH', item(c, 'Eclair Chocolat'), 'order-service/1.12.0', `\nContent-Type: application/json\n\n${js({ price: 2.6 })}`),
    rawResponse: () => httpRes('200 OK', js({ ...PASTRIES.choco, price: 2.6 }))
  },
  'mcp-cafe': { clientId: 'c3', endpointId: 'e1', exampleId: 'd2', exampleName: 'Eclair Cafe', kind: 'MCP', method: 'POST', mcpTool: 'get_pastry_name', status: 200, criteria: '/name=Eclair Cafe', latency: 18, path: (c) => c.mcpPath, rawRequest: (c) => mcpReq(c, 'get_pastry_name', { name: 'Eclair Cafe' }), rawResponse: () => mcpRes(PASTRIES.cafe) },
  'mcp-size': { clientId: 'c3', endpointId: 'e2', exampleId: 'd3', exampleName: 'Size M', kind: 'MCP', method: 'POST', mcpTool: 'get_pastry', status: 200, criteria: '?size=M', latency: 21, path: (c) => c.mcpPath, rawRequest: (c) => mcpReq(c, 'get_pastry', { size: 'M' }), rawResponse: () => mcpRes([PASTRIES.cafe, PASTRIES.choco]) },
  event: {
    clientId: 'c2', endpointId: 'e4', exampleId: 'd5', exampleName: 'Event payload', kind: 'EVENT', method: 'SUB', status: 200, criteria: 'none', latency: 1,
    path: (c) => c.topic ?? '',
    rawRequest: (c) => `Kafka topic   ${c.topic}\nBroker        kafka.local:9092\nConsumer      order-service (group order-service-v1)\nOperation     SUBSCRIBE ${c.eventLabel}`,
    rawResponse: () => `Key           evt-20261102-0147\nHeaders       content-type: application/json\n\n${js({ id: 'evt-20261102-0147', status: 'VALIDATED' })}`
  }
};

const HISTORY_TEMPLATES = ['mille-web', 'event', 'arazzo-list', 'arazzo-get', 'mcp-cafe', 'cafe-web', 'session-size', 'size-web', 'event', 'patch-order'];
const STREAM_CYCLE = ['mille-web', 'event', 'mcp-cafe', 'arazzo-list', 'cafe-web', 'size-web', 'event', 'arazzo-get', 'patch-order', 'tatin-web', 'mcp-size', 'event', 'cafe-web', 'session-size'];

/** Templates available for this instance: event calls need an event service in the store. */
function templateKey(seq: number, ctx: TraceContext): string {
  const key = seq <= HISTORY_LENGTH ? HISTORY_TEMPLATES[seq - 1] : STREAM_CYCLE[(seq - HISTORY_LENGTH - 1) % STREAM_CYCLE.length];
  return key === 'event' && !ctx.eventServiceId ? 'cafe-web' : key;
}

const ep = (id: string, ctx: TraceContext) => buildTopology(ctx).endpoints.find((e) => e.id === id);
const client = (id: string) => TOPOLOGY_CLIENTS.find((c) => c.id === id);

/** Builds call number `seq` at time `at`. History calls (1..10) follow HISTORY_TEMPLATES, later ones STREAM_CYCLE. */
export function makeCall(seq: number, ctx: TraceContext, at: Date): TraceCall {
  const tpl = TEMPLATES[templateKey(seq, ctx)];
  const cl = client(tpl.clientId);
  const endpoint = ep(tpl.endpointId, ctx);
  const isEvent = tpl.kind === 'EVENT';
  const runNumber = seq <= HISTORY_LENGTH ? 12 : 14;
  const bag = tpl.arazzo
    ? `microcks-arazzo-run=${RUN_IDS[runNumber]},microcks-arazzo-workflow=${tpl.arazzo.workflow},microcks-arazzo-step=${tpl.arazzo.step},microcks-arazzo-attempt=1`
    : undefined;
  const raw = tpl.rawRequest(ctx);
  return {
    seq,
    callId: `call-${seq}`,
    at: at.toISOString(),
    serviceId: endpoint?.serviceId ?? ctx.restServiceId,
    serviceName: isEvent ? (ctx.eventServiceName ?? '') : ctx.restServiceName,
    protocol: isEvent ? 'EVENT' : 'REST',
    method: tpl.method,
    path: tpl.path(ctx),
    status: tpl.status,
    ms: tpl.latency + (seq % 3),
    exampleName: tpl.exampleName,
    operationId: undefined,
    baggage: bag,
    arazzoRun: bag ? runNumber : undefined,
    workflowName: bag ? 'Restock a pastry' : undefined,
    kind: tpl.kind,
    clientId: tpl.clientId,
    clientName: cl?.label ?? '',
    clientIp: cl?.ip ?? '',
    endpointId: tpl.endpointId,
    endpointLabel: endpoint?.label ?? '',
    dispatcher: endpoint?.dispatcher ?? 'none',
    criteria: tpl.criteria,
    exampleId: tpl.exampleId,
    mcpTool: tpl.mcpTool,
    sessionId: tpl.session,
    rawRequest: bag ? `${raw}\nbaggage: ${bag}` : raw,
    rawResponse: tpl.rawResponse(ctx)
  };
}

/** The history the page opens with: oldest call first-numbered, spaced like the stream. */
export function historyCalls(ctx: TraceContext, now = Date.now()): TraceCall[] {
  return Array.from({ length: HISTORY_LENGTH }, (_, i) => {
    const seq = i + 1;
    return makeCall(seq, ctx, new Date(now - (HISTORY_LENGTH - seq + 1) * TRACE_TICK_MS - ((seq * 137) % 600)));
  });
}
