import type { Behavior, CatalogExample, CatalogOperation } from './types.js';

export type StepKind = 'ok' | 'fail' | 'warn' | 'skip' | 'info';
export interface SimStep {
  label: string;
  value: string;
  kind: StepKind;
}
export interface SimResult {
  outcome: 'matched' | 'no-match' | 'constraint' | 'forwarded' | 'no-operation';
  requestLine: string;
  status: number | null;
  statusLabel: string;
  chain: string;
  steps: SimStep[];
  mediaType: string;
  body: string;
  exampleId?: string;
}

export const DISPATCHERS: { id: Behavior['dispatcher']; label: string; hint: string }[] = [
  { id: 'NONE', label: 'None', hint: 'Always serve the first example.' },
  { id: 'URI_PARTS', label: 'URI parts', hint: 'Match path parameters, e.g. name && id.' },
  { id: 'URI_PARAMS', label: 'URI params', hint: 'Match query parameters, e.g. page && size.' },
  { id: 'QUERY_ARGS', label: 'Query args', hint: 'Match query arguments, e.g. status.' },
  { id: 'JSON_BODY', label: 'JSON body', hint: 'Match a JSON path of the request body.' },
  { id: 'SCRIPT', label: 'Script', hint: 'Groovy script returning the example name (not run here).' },
  { id: 'FALLBACK', label: 'Fallback', hint: 'Try a first dispatcher, then serve a named example.' },
  { id: 'PROXY_FALLBACK', label: 'Proxy fallback', hint: 'Try a first dispatcher, then forward to a real backend.' }
];

export const splitRule = (rule: string) => rule.split('&&').map((x) => x.trim()).filter(Boolean);

export const isValidRegExp = (src: string) => {
  try {
    new RegExp(src);
    return true;
  } catch {
    return false;
  }
};

export function matchPathTemplate(template: string, path: string): Record<string, string> | null {
  const t = template.split('/').filter(Boolean);
  const p = path.split('/').filter(Boolean);
  if (t.length !== p.length) return null;
  const out: Record<string, string> = {};
  for (let i = 0; i < t.length; i++) {
    const m = /^\{(.+)\}$/.exec(t[i]);
    if (m) out[m[1]] = decodeURIComponent(p[i]);
    else if (t[i] !== p[i]) return null;
  }
  return out;
}

/** Parses `GET /pastry/Eclair%20Cafe?size=S` or a bare path. */
export function parseCall(input: string, defaultMethod: string): { method: string; path: string; query: Record<string, string> } {
  const m = /^(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s+(.*)$/i.exec(input.trim());
  const method = (m?.[1] ?? defaultMethod).toUpperCase();
  const rest = (m?.[2] ?? input).trim();
  const [path, qs = ''] = rest.split('?');
  const query: Record<string, string> = {};
  for (const [k, v] of new URLSearchParams(qs)) query[k] = v;
  return { method, path: path || '/', query };
}

const jsonAt = (doc: unknown, exp: string): unknown =>
  exp
    .replace(/^\$\.?/, '')
    .split('.')
    .filter(Boolean)
    .reduce<any>((acc, key) => (acc && typeof acc === 'object' ? acc[key] : undefined), doc);

const criteriaFor = (kind: Behavior['dispatcher'], rule: string, params: Record<string, string>): string => {
  const names = splitRule(rule);
  if (kind === 'URI_PARTS') return names.map((n) => `/${n}=${params[n] ?? ''}`).join('');
  if (kind === 'URI_PARAMS' || kind === 'QUERY_ARGS') return names.map((n) => `?${n}=${params[n] ?? ''}`).join('');
  return '';
};

export function reachability(op: CatalogOperation, behavior: Behavior): { name: string; reachable: boolean; criteria: string }[] {
  const kind = behavior.dispatcher === 'FALLBACK' || behavior.dispatcher === 'PROXY_FALLBACK' ? behavior.baseDispatcher : behavior.dispatcher;
  const seen = new Set<string>();
  return op.examples.map((example) => {
    const criteria = criteriaFor(kind, behavior.rule, example.request.params);
    const reachable = kind === 'NONE' ? seen.size === 0 : !seen.has(criteria);
    seen.add(criteria);
    return { name: example.name, reachable, criteria: criteria || (kind === 'JSON_BODY' ? 'JSON body' : kind) };
  });
}

/** The x-microcks-operation block that a save would write into the contract. */
export function storedAs(behavior: Behavior): string {
  const lines = ['x-microcks-operation:', `  delay: ${behavior.delayMs}`, `  dispatcher: ${behavior.dispatcher}`];
  if (behavior.dispatcher !== 'NONE') lines.push(`  dispatcherRules: ${JSON.stringify(behavior.rule)}`);
  return lines.join('\n');
}

export const storedSummary = (b: Behavior) =>
  `${b.dispatcher}${b.dispatcher === 'NONE' ? '' : ` · ${b.rule || 'no rule'}`} · ${b.delayMs} ms`;

export interface SimInput {
  call: string;
  body?: string;
  headers?: Record<string, string>;
}

export function simulate(op: CatalogOperation, behavior: Behavior, input: SimInput): SimResult {
  const call = parseCall(input.call, op.method);
  const requestLine = `${call.method} ${call.path}${Object.keys(call.query).length ? `?${new URLSearchParams(call.query)}` : ''}`;
  const base = { requestLine, mediaType: 'application/json', body: '' };
  const steps: SimStep[] = [];
  const pathParams = matchPathTemplate(op.path, call.path);
  if (call.method !== op.method || !pathParams) {
    steps.push({ label: 'Operation', value: `${call.method} ${call.path} does not match ${op.method} ${op.path}`, kind: 'fail' });
    return { ...base, outcome: 'no-operation', status: 404, statusLabel: '404 Not Found', chain: 'no operation', steps, body: '{"error":"no such operation"}' };
  }
  steps.push({ label: 'Operation', value: `${op.method} ${op.path}`, kind: 'ok' });
  const params = { ...pathParams, ...call.query };

  const headers = Object.fromEntries(Object.entries(input.headers ?? {}).map(([k, v]) => [k.toLowerCase(), v]));
  const violated = behavior.constraints.filter((c) => {
    if (!c.name) return false;
    const v = c.in === 'header' ? headers[c.name.toLowerCase()] : call.query[c.name];
    if (!v) return c.required;
    return Boolean(c.mustMatchRegexp) && isValidRegExp(c.mustMatchRegexp ?? '') && !new RegExp(c.mustMatchRegexp ?? '').test(v);
  });
  if (violated.length) {
    const what = violated.map((c) => `${c.in} ${c.name}`).join(', ');
    steps.push({ label: 'Constraints', value: `violated: ${what}`, kind: 'fail' });
    return { ...base, outcome: 'constraint', status: 400, statusLabel: '400 Bad Request', chain: `constraint ${what}`, steps, body: `{"error":"constraint violated: ${violated[0].name}"}` };
  }
  steps.push({ label: 'Constraints', value: behavior.constraints.length ? `${behavior.constraints.length} checked` : 'none', kind: behavior.constraints.length ? 'ok' : 'skip' });

  const run = (kind: Behavior['dispatcher']): CatalogExample | undefined | 'unsupported' => {
    if (kind === 'NONE') return op.examples[0];
    if (kind === 'SCRIPT') return 'unsupported';
    if (kind === 'JSON_BODY') {
      try {
        const rule = JSON.parse(behavior.rule || '{}') as { exp?: string; cases?: Record<string, string> };
        const value = String(jsonAt(JSON.parse(input.body ?? '{}'), rule.exp ?? '$'));
        const name = rule.cases?.[value] ?? rule.cases?.default;
        return op.examples.find((e) => e.name === name);
      } catch {
        return undefined;
      }
    }
    const wanted = criteriaFor(kind, behavior.rule, params);
    return op.examples.find((e) => criteriaFor(kind, behavior.rule, e.request.params) === wanted);
  };

  const first = behavior.dispatcher === 'FALLBACK' || behavior.dispatcher === 'PROXY_FALLBACK' ? behavior.baseDispatcher : behavior.dispatcher;
  steps.push({ label: 'Dispatch criteria', value: criteriaFor(first, behavior.rule, params) || first, kind: 'info' });
  let found = run(first);
  if (found === 'unsupported') {
    steps.push({ label: 'Script', value: 'Scripts run on the Microcks server; not evaluated in this dry run.', kind: 'warn' });
    found = undefined;
  }
  if (!found && behavior.dispatcher === 'PROXY_FALLBACK') {
    steps.push({ label: 'Fallback', value: `forward to ${behavior.proxyUrl || '(no backend URL)'}`, kind: 'warn' });
    return { ...base, outcome: 'forwarded', status: null, statusLabel: 'Forwarded', chain: `→ ${behavior.proxyUrl}`, steps };
  }
  if (!found && behavior.dispatcher === 'FALLBACK') {
    found = op.examples.find((e) => e.name === behavior.fallback);
    steps.push({ label: 'Fallback', value: behavior.fallback ?? 'no fallback example', kind: found ? 'warn' : 'fail' });
  }
  if (!found) {
    steps.push({ label: 'Matched example', value: 'none', kind: 'fail' });
    return { ...base, outcome: 'no-match', status: 404, statusLabel: '404 Not Found', chain: `${criteriaFor(first, behavior.rule, params) || first} → no example`, steps, body: '{"error":"no example matches"}' };
  }
  steps.push({ label: 'Matched example', value: found.name, kind: 'ok' });
  const status = found.response.status;
  return {
    ...base,
    outcome: 'matched',
    status,
    statusLabel: `${status} ${status < 300 ? 'OK' : status < 400 ? 'Redirect' : status < 500 ? 'Client error' : 'Server error'}`,
    chain: `${criteriaFor(first, behavior.rule, params) || first} → ${found.name} → ${status}`,
    steps,
    mediaType: found.response.contentType,
    body: found.response.body,
    exampleId: found.id
  };
}

export const ruleNames = splitRule;

export const JSON_RULE_SAMPLE = JSON.stringify({ exp: '$.name', operator: 'equals', cases: { default: '' } }, null, 2);

export function parseJsonBodyRule(rule: string): { exp: string; operator: string; cases: Record<string, string> } | { error: string } {
  let doc: any;
  try {
    doc = JSON.parse(rule);
  } catch (e) {
    return { error: `Invalid JSON: ${(e as Error).message}` };
  }
  if (!doc || typeof doc.exp !== 'string' || !doc.exp) return { error: 'exp is required, e.g. "$.name"' };
  if (!['equals', 'range', 'size', 'regexp', 'presence'].includes(doc.operator)) return { error: 'operator must be equals, range, size, regexp or presence' };
  if (!doc.cases || typeof doc.cases !== 'object' || !('default' in doc.cases)) return { error: 'cases needs a "default" entry' };
  return doc;
}

export function scriptSample(lang: 'GROOVY' | 'JS', stateful: boolean): string {
  if (lang === 'JS') {
    return stateful
      ? "const views = (store.get('views') || 0) + 1;\nstore.put('views', views);\nrequestContext.views = views;\nreturn mockRequest.getRequestParameter('name');"
      : "// Return the name of the example to serve.\nreturn mockRequest.getRequestParameter('name');";
  }
  return stateful
    ? "def views = (store.get('views') ?: 0) + 1\nstore.put('views', views)\nrequestContext.views = views\nreturn mockRequest.getRequestParameter('name')"
    : "// Return the name of the example to serve.\nreturn mockRequest.getRequestParameter('name')";
}
