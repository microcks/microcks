import type { CatalogExample } from './types.js';

export type SnippetFormat = 'curl' | 'httpie' | 'mcp';

export interface SnippetSpec {
  method: string;
  pathTemplate: string;
  example: CatalogExample;
  mockBase: string;
  mcpBase: string;
  mcpTool?: string;
}

/** Splits example parameters into those filling the path template and those going to the query string. */
export function splitParams(pathTemplate: string, params: Record<string, string>): { path: string; query: [string, string][] } {
  const used = new Set<string>();
  const path = pathTemplate.replace(/\{([^}]+)\}/g, (_, name: string) => {
    used.add(name);
    return encodeURIComponent(params[name] ?? name);
  });
  return { path, query: Object.entries(params).filter(([k]) => !used.has(k)) };
}

export function exampleUrlPath(pathTemplate: string, params: Record<string, string>): string {
  const { path, query } = splitParams(pathTemplate, params);
  return query.length ? `${path}?${query.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&')}` : path;
}

function compactBody(body: string | undefined): string {
  if (!body) return '';
  try {
    return JSON.stringify(JSON.parse(body));
  } catch {
    return body;
  }
}

export function curlSnippet(spec: SnippetSpec): string {
  const url = `${spec.mockBase}${exampleUrlPath(spec.pathTemplate, spec.example.request.params)}`;
  const body = compactBody(spec.example.request.body);
  return (
    `curl -X ${spec.method} '${url}' \\\n  -H 'Accept: application/json'` +
    (body ? ` \\\n  -H 'Content-Type: application/json' \\\n  -d '${body}'` : '')
  );
}

export function httpieSnippet(spec: SnippetSpec): string {
  const { path, query } = splitParams(spec.pathTemplate, spec.example.request.params);
  const extras = query.map(([k, v]) => ` ${k}==${v}`).join('');
  let json = '';
  if (spec.example.request.body) {
    try {
      const body = JSON.parse(spec.example.request.body) as Record<string, unknown>;
      json = Object.entries(body)
        .map(([k, v]) => ` ${k}:=${JSON.stringify(v)}`)
        .join('');
    } catch {
      json = '';
    }
  }
  return `http ${spec.method} '${spec.mockBase}${path}' Accept:application/json${extras}${json}`;
}

export function mcpSnippet(spec: SnippetSpec): string {
  const args: Record<string, unknown> = { ...spec.example.request.params };
  if (spec.example.request.body) {
    try {
      args.body = JSON.parse(spec.example.request.body);
    } catch {
      args.body = spec.example.request.body;
    }
  }
  const payload = { jsonrpc: '2.0', id: 1, method: 'tools/call', params: { name: spec.mcpTool ?? 'tool', arguments: args } };
  return `POST ${spec.mcpBase}\nContent-Type: application/json\n\n${JSON.stringify(payload, null, 2)}`;
}

export function buildSnippet(format: SnippetFormat, spec: SnippetSpec): string {
  return format === 'httpie' ? httpieSnippet(spec) : format === 'mcp' ? mcpSnippet(spec) : curlSnippet(spec);
}

export function requestHead(spec: SnippetSpec): string {
  const url = new URL(`${spec.mockBase}${exampleUrlPath(spec.pathTemplate, spec.example.request.params)}`);
  return (
    `${spec.method} ${url.pathname}${url.search} HTTP/1.1\nHost: ${url.host}\nAccept: application/json` +
    (spec.example.request.body ? '\nContent-Type: application/json' : '')
  );
}

export function paramLines(pathTemplate: string, params: Record<string, string>): string {
  const { query } = splitParams(pathTemplate, params);
  const queryNames = new Set(query.map(([k]) => k));
  return Object.entries(params)
    .map(([k, v]) => `${queryNames.has(k) ? 'query ' : 'path  '}  ${k} = ${v}`)
    .join('\n');
}

const plus = (text: string) => text.trim().replace(/\s+/g, '+');

export function mcpToolName(method: string, path: string): string {
  return `${method}_${path}`.toLowerCase().replace(/[{}]/g, '').replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
}

export function mcpBaseUrl(service: { name: string; version: string }, host: string): string {
  return `${host}/mcp/${plus(service.name)}/${service.version}`;
}

export function mcpServerName(name: string): string {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

export function deriveInputSchema(op: { method: string; path: string; dispatcher: string; rule: string }): string {
  const properties: Record<string, unknown> = {};
  const required: string[] = [];
  for (const m of op.path.matchAll(/\{([^}]+)\}/g)) {
    properties[m[1]] = { type: 'string', description: `Value of the ${m[1]} path parameter` };
    required.push(m[1]);
  }
  if (op.dispatcher === 'URI_PARAMS') {
    for (const name of op.rule.split('&&').map((x) => x.trim()).filter(Boolean)) {
      properties[name] = { type: 'string', description: `Value of the ${name} query parameter` };
      required.push(name);
    }
  }
  if (['POST', 'PUT', 'PATCH'].includes(op.method)) {
    properties.body = { type: 'object' };
    required.push('body');
  }
  return JSON.stringify({ type: 'object', properties, required }, null, 2);
}
