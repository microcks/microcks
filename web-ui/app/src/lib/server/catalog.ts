import { parse as parseYaml } from 'yaml';
import { fetchBackendServiceView, fetchBackendTests, type BackendExchange, type BackendOperation } from './backend';
import { recentCalls, todayInvocations } from './invocations';
import { toRunDto } from './testing';
import { runConformance, summarize } from '#lib/components/testing/model.js';
import { getService, getSource, isBackendService, getSourceContent, listServices, listSources } from './store';
import type { Behavior, CatalogExample, CatalogOperation, CockpitData, ServiceDetail } from '#lib/components/catalog/types.js';
import type { Protocol, Service } from '#lib/types.js';

type Obj = Record<string, any>;
const isObj = (v: unknown): v is Obj => typeof v === 'object' && v !== null && !Array.isArray(v);
const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

function load(content: string | undefined): Obj | undefined {
  if (!content) return undefined;
  try {
    const v = JSON.parse(content);
    return isObj(v) ? v : undefined;
  } catch {
    try {
      const v = parseYaml(content);
      return isObj(v) ? v : undefined;
    } catch {
      return undefined;
    }
  }
}

const text = (v: unknown) => (typeof v === 'string' ? v : JSON.stringify(v, null, 2));
const normalize = (path: string) => path.replace(/\/{2,}/g, '/').replace(/\{[^}]+\}|:[^/]+/g, '{}').replace(/\/+$/, '');

const defaultBehavior = (): Behavior => ({
  dispatcher: 'NONE',
  rule: '',
  delayMs: 0,
  delayStrategy: 'fixed',
  baseDispatcher: 'URI_PARTS',
  stateful: false,
  constraints: []
});

interface Extracted {
  summary?: string;
  dispatcher?: string;
  rule?: string;
  delay?: number;
  examples: CatalogExample[];
}

function fromOpenApi(doc: Obj, method: string, path: string): Extracted | undefined {
  const item = doc.paths?.[path] ?? doc.webhooks?.[path];
  const op = item?.[method.toLowerCase()];
  if (!isObj(op)) return undefined;
  const out: Extracted = { summary: op.summary ?? op.description, examples: [] };
  const ext = op['x-microcks-operation'];
  if (isObj(ext)) {
    out.dispatcher = ext.dispatcher;
    out.rule = ext.dispatcherRules;
    out.delay = ext.delay;
  }
  const byName = new Map<string, CatalogExample>();
  for (const [code, res] of Object.entries<Obj>(op.responses ?? {})) {
    for (const [mediaType, media] of Object.entries<Obj>(res?.content ?? {})) {
      for (const [name, ex] of Object.entries<Obj>(media?.examples ?? {})) {
        if (byName.has(name)) continue;
        byName.set(name, {
          id: slug(name) || `example-${byName.size + 1}`,
          name,
          request: { params: {} },
          response: { status: Number(code) || 200, contentType: mediaType, body: text(ex?.value) }
        });
      }
    }
  }
  const all = [...(item.parameters ?? []), ...(op.parameters ?? [])];
  for (const p of all) {
    for (const [name, ex] of Object.entries<Obj>(isObj(p?.examples) ? p.examples : {})) {
      const e = byName.get(name);
      if (e) e.request.params[p.name] = String(isObj(ex) && 'value' in ex ? ex.value : ex);
    }
  }
  for (const media of Object.values<Obj>(op.requestBody?.content ?? {})) {
    for (const [name, ex] of Object.entries<Obj>(media?.examples ?? {})) {
      const e = byName.get(name);
      if (e && e.request.body === undefined) e.request.body = text(ex?.value);
    }
  }
  out.examples = [...byName.values()];
  return out;
}

function fromPostman(doc: Obj, method: string, path: string): Extracted | undefined {
  if (!Array.isArray(doc.item)) return undefined;
  const target = normalize(path);
  const walk = (items: Obj[]): Extracted | undefined => {
    for (const it of items) {
      if (Array.isArray(it.item)) {
        const r = walk(it.item);
        if (r) return r;
        continue;
      }
      const req = it.request;
      const parts: string[] = Array.isArray(req?.url?.path) ? req.url.path.filter(Boolean) : String(req?.url?.raw ?? '').replace(/^https?:\/\/[^/]*/, '').split('?')[0].split('/').filter(Boolean);
      if (String(req?.method).toUpperCase() !== method || normalize(`/${parts.join('/')}`) !== target) continue;
      const examples: CatalogExample[] = (it.response ?? []).map((r: Obj, i: number) => {
        const params: Record<string, string> = {};
        for (const v of r.originalRequest?.url?.variable ?? []) params[v.key] = String(v.value ?? '');
        for (const q of r.originalRequest?.url?.query ?? []) params[q.key] = String(q.value ?? '');
        const body = r.originalRequest?.body?.raw;
        return {
          id: slug(r.name ?? '') || `example-${i + 1}`,
          name: r.name ?? `example ${i + 1}`,
          request: { params, ...(body ? { body } : {}) },
          response: { status: Number(r.code) || 200, contentType: r._postman_previewlanguage === 'xml' ? 'text/xml' : 'application/json', body: String(r.body ?? '') }
        };
      });
      return { summary: it.request?.description || it.name, examples };
    }
    return undefined;
  };
  return walk(doc.item);
}

function fromMetadata(doc: Obj, name: string): Extracted | undefined {
  const op = doc.operations?.[name];
  return isObj(op) ? { dispatcher: op.dispatcher, rule: op.dispatcherRules, delay: op.delay, examples: [] } : undefined;
}

/** In-memory edits (behaviors, accepted AI examples, rejections). Lost on restart, like the rest of the preview store. */
const behaviors = new Map<string, Behavior>();
const aiExamples = new Map<string, CatalogExample[]>();
const key = (serviceId: string, opId: string) => `${serviceId}::${opId}`;

function opsOf(service: Service): CatalogOperation[] {
  const docs = service.sourceIds.map((id) => ({ id, doc: load(getSourceContent(id)) })).filter((d) => d.doc);
  const used = new Set<string>();
  return service.operations.map((o) => {
    const path = (o.resource ?? o.name).replace(/^\/{2,}/, '/').replace(/\/:([A-Za-z_]\w*)/g, '/{$1}');
    let id = slug(o.name) || 'operation';
    for (let n = 2; used.has(id); n++) id = `${slug(o.name)}-${n}`;
    used.add(id);
    const found: Extracted[] = [];
    for (const { doc } of docs) {
      const r = service.protocol === 'REST' ? (fromOpenApi(doc!, o.method, path) ?? fromPostman(doc!, o.method, path)) : undefined;
      const m = fromMetadata(doc!, o.name);
      if (r) found.push(r);
      if (m) found.push(m);
    }
    const contract = found.find((f) => f.dispatcher);
    const base = defaultBehavior();
    if (contract?.dispatcher) base.dispatcher = contract.dispatcher as Behavior['dispatcher'];
    base.rule = contract?.rule ?? '';
    base.delayMs = Number(found.find((f) => f.delay !== undefined)?.delay ?? 0);
    const saved = behaviors.get(key(service.id, id));
    const contractExamples = found.flatMap((f) => f.examples).filter((e, i, all) => all.findIndex((x) => x.name === e.name) === i);
    return {
      id,
      name: o.name,
      method: o.method,
      path,
      summary: found.find((f) => f.summary)?.summary ?? '',
      behaviorFromContract: Boolean(contract?.dispatcher),
      behavior: saved ?? base,
      examples: [...contractExamples, ...(aiExamples.get(key(service.id, id)) ?? [])]
    };
  });
}

/** Extracts the example parameters from a Microcks dispatch criteria: `/k=v/k2=v2` or `?k=v&k2=v2`. */
function paramsOf(criteria: string | undefined): Record<string, string> {
  const params: Record<string, string> = {};
  if (!criteria) return params;
  const parts = criteria.startsWith('?') ? criteria.slice(1).split('&') : criteria.split('/').filter(Boolean);
  for (const part of parts) {
    const i = part.indexOf('=');
    if (i > 0) params[part.slice(0, i)] = decodeURIComponent(part.slice(i + 1));
  }
  return params;
}

function examplesFromBackend(exchanges: BackendExchange[]): CatalogExample[] {
  const out: CatalogExample[] = [];
  const used = new Set<string>();
  for (const x of exchanges) {
    const name = x.response?.name ?? x.request?.name ?? x.eventMessage?.name ?? `example ${out.length + 1}`;
    let id = slug(name) || `example-${out.length + 1}`;
    for (let n = 2; used.has(id); n++) id = `${slug(name)}-${n}`;
    used.add(id);
    const params = paramsOf(x.response?.dispatchCriteria ?? x.eventMessage?.dispatchCriteria);
    for (const q of x.request?.queryParameters ?? []) params[q.name] = q.value;
    if (x.eventMessage) {
      out.push({
        id,
        name,
        request: { params },
        response: { status: 200, contentType: x.eventMessage.mediaType ?? 'application/json', body: x.eventMessage.content ?? '' }
      });
      continue;
    }
    out.push({
      id,
      name,
      request: { params, ...(x.request?.content ? { body: x.request.content } : {}) },
      response: {
        status: Number(x.response?.status) || 200,
        contentType: x.response?.mediaType ?? 'application/json',
        body: x.response?.content ?? ''
      }
    });
  }
  return out;
}

function behaviorFromBackend(op: BackendOperation | undefined): { behavior: Behavior; fromContract: boolean } {
  const behavior = defaultBehavior();
  if (!op) return { behavior, fromContract: false };
  if (op.dispatcher) behavior.dispatcher = op.dispatcher as Behavior['dispatcher'];
  behavior.rule = op.dispatcherRules ?? '';
  behavior.delayMs = Number(op.defaultDelay ?? 0);
  behavior.delayStrategy = op.defaultDelayStrategy?.toLowerCase() === 'random' ? 'random' : 'fixed';
  return { behavior, fromContract: Boolean(op.dispatcher) };
}

/** Builds the detail of a Service stored in the backend: operations, dispatching and examples come from it. */
async function backendDetail(service: Service, token?: string): Promise<ServiceDetail | undefined> {
  const view = await fetchBackendServiceView(service.id, token);
  if (!view) return undefined;
  const used = new Set<string>();
  const operations: CatalogOperation[] = service.operations.map((o) => {
    const raw = view.service.operations?.find((x) => x.name === o.name);
    const path = (o.resource ?? o.name).replace(/^\/{2,}/, '/').replace(/\/:([A-Za-z_]\w*)/g, '/{$1}');
    let id = slug(o.name) || 'operation';
    for (let n = 2; used.has(id); n++) id = `${slug(o.name)}-${n}`;
    used.add(id);
    const { behavior, fromContract } = behaviorFromBackend(raw);
    return {
      id,
      name: o.name,
      method: o.method,
      path,
      summary: '',
      behaviorFromContract: fromContract,
      behavior: behaviors.get(key(service.id, id)) ?? behavior,
      examples: [...examplesFromBackend(view.messagesMap?.[o.name] ?? []), ...(aiExamples.get(key(service.id, id)) ?? [])]
    };
  });
  return {
    service,
    description: '',
    mockBase: service.mockUrl,
    operations,
    artifacts: [{ kind: service.artifacts[0]?.kind ?? 'primary', format: service.artifacts[0]?.format ?? '', file: service.name }],
    calls: await recentCalls(service, 7, token).catch(() => [])
  };
}

export async function serviceDetail(id: string, token?: string): Promise<ServiceDetail | undefined> {
  const service = getService(id);
  if (!service) return undefined;
  if (isBackendService(id)) return backendDetail(service, token);
  const docs = service.sourceIds.map((s) => load(getSourceContent(s))?.info?.description).filter(Boolean);
  return {
    service,
    description: docs[0] ?? '',
    mockBase: service.mockUrl,
    operations: opsOf(service),
    artifacts: service.sourceIds.map((sid, i) => ({
      kind: service.artifacts[i]?.kind ?? 'artifact',
      format: service.artifacts[i]?.format ?? '',
      file: getSource(sid)?.name ?? sid
    })),
    calls: []
  };
}

export async function saveBehavior(serviceId: string, opId: string, b: Behavior, token?: string): Promise<Behavior | undefined> {
  const d = await serviceDetail(serviceId, token);
  if (!d?.operations.some((o) => o.id === opId)) return undefined;
  behaviors.set(key(serviceId, opId), b);
  return b;
}

export async function acceptExamples(
  serviceId: string,
  opId: string,
  examples: CatalogExample[],
  token?: string
): Promise<CatalogExample[] | undefined> {
  const d = await serviceDetail(serviceId, token);
  const op = d?.operations.find((o) => o.id === opId);
  if (!op) return undefined;
  const list = aiExamples.get(key(serviceId, opId)) ?? [];
  const names = new Set(op.examples.map((e) => e.name));
  for (const e of examples) {
    if (names.has(e.name)) continue;
    names.add(e.name);
    list.push({ ...e, aiGenerated: true, acceptedBy: 'MA' });
  }
  aiExamples.set(key(serviceId, opId), list);
  return list;
}

export function removeAiExample(serviceId: string, opId: string, exampleId: string): boolean {
  const list = aiExamples.get(key(serviceId, opId)) ?? [];
  const next = list.filter((e) => e.id !== exampleId);
  aiExamples.set(key(serviceId, opId), next);
  return next.length !== list.length;
}

const WEEK_MS = 7 * 24 * 3600 * 1000;

async function cockpitTests(services: Service[], token?: string): Promise<{ tests: CockpitData['tests']; untested: Service[]; scores: Map<string, NonNullable<CockpitData['wall'][number]['conformance']>> }> {
  const perService = await Promise.all(
    services.map(async (svc) => ({ svc, runs: (await fetchBackendTests(svc.id, 0, 50, token).catch(() => [])).map((t) => summarize(toRunDto(t))) }))
  );
  const since = Date.now() - WEEK_MS;
  const all = perService.flatMap(({ svc, runs }) => runs.map((r) => ({ svc, r })));
  const week = all.filter(({ r }) => r.result !== 'running' && new Date(r.startedAt).getTime() >= since);
  const latest = perService.flatMap(({ runs }) => {
    const done = runs.filter((r) => r.result !== 'running').sort((a, b) => b.startedAt.localeCompare(a.startedAt))[0];
    return done ? [runConformance(done)] : [];
  });
  const recent = all
    .sort((a, b) => b.r.startedAt.localeCompare(a.r.startedAt))
    .slice(0, 5)
    .map(({ svc, r }) => ({ id: r.id, serviceId: svc.id, serviceName: svc.name, version: svc.version, protocol: svc.protocol, runner: r.runner, result: r.result, conformance: runConformance(r), at: r.startedAt }));
  const scores = new Map<string, NonNullable<CockpitData['wall'][number]['conformance']>>();
  for (const { svc, runs } of perService) {
    const done = runs.filter((r) => r.result !== 'running').sort((a, b) => b.startedAt.localeCompare(a.startedAt))[0];
    if (!done) continue;
    const tested = done.operations.map((o) => (o === 'pass' ? 'pass' : o === 'fail' ? 'fail' : 'untested') as 'pass' | 'fail' | 'untested');
    const pad = Array.from({ length: Math.max(0, svc.operations.length - tested.length) }, () => 'untested' as const);
    scores.set(svc.id, { score: runConformance(done), outcomes: [...tested, ...pad] });
  }
  return {
    scores,
    tests: {
      last7d: { passed: week.filter(({ r }) => r.result === 'passed').length, failed: week.filter(({ r }) => r.result !== 'passed').length },
      averageConformance: latest.length ? Math.round(latest.reduce((a, b) => a + b, 0) / latest.length) : null,
      recent
    },
    untested: perService.filter(({ runs }) => runs.length === 0).map(({ svc }) => svc)
  };
}

export async function cockpit(token?: string): Promise<CockpitData> {
  const services = listServices();
  const today = await todayInvocations(services, token).catch(() => ({ byService: {} as Record<string, number>, hourly: [] as number[], total: 0 }));
  const { tests, untested, scores } = await cockpitTests(services, token);
  const counts = new Map<Protocol, number>();
  for (const s of services) counts.set(s.protocol, (counts.get(s.protocol) ?? 0) + 1);
  const sources = listSources();
  const attention: CockpitData['attention'] = sources
    .filter((s) => s.error)
    .map((s) => ({ id: `src-${s.id}`, tone: 'danger' as const, title: `Import failing: ${s.name}`, detail: s.error ?? '', href: `/sources/${s.id}`, cta: 'Open source' }));
  if (untested[0]) {
    const rest = untested.length - 1;
    attention.push({
      id: 'never-tested',
      tone: 'warning',
      title: `${untested[0].name} ${untested[0].version}${rest > 0 ? ` and ${rest} other${rest > 1 ? 's' : ''}` : ''} never tested`,
      detail: 'No test run is recorded yet. Run a conformance test against an implementation.',
      href: `/tests/create?service=${untested[0].id}`,
      cta: 'Run a test'
    });
  }
  return {
    services: services.length,
    sources: sources.length,
    operations: services.reduce((n, s) => n + s.operations.length, 0),
    protocols: [...counts].map(([protocol, count]) => ({ protocol, count })),
    wall: services.map((s) => ({ id: s.id, name: s.name, version: s.version, protocol: s.protocol, operations: s.operations.length, callsToday: today.byService[s.id] ?? 0, conformance: scores.get(s.id) ?? null })),
    invocations: { total: today.total, hourly: today.hourly },
    tests,
    attention
  };
}
