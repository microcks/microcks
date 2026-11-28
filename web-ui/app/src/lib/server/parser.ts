import { parse as parseYaml } from 'yaml';
import type { Operation, ParsedArtifact } from '#lib/types.js';

const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'options', 'head', 'patch', 'trace'];

function tryJson(content: string): unknown {
  try {
    return JSON.parse(content);
  } catch {
    return undefined;
  }
}

function tryYaml(content: string): unknown {
  try {
    return parseYaml(content);
  } catch {
    return undefined;
  }
}

function base(fileName: string, kind: string, format: string): ParsedArtifact {
  return { fileName, kind, format, role: 'PRIMARY', operations: [], labels: {}, warnings: [] };
}

type Obj = Record<string, any>;
const isObj = (v: unknown): v is Obj => typeof v === 'object' && v !== null && !Array.isArray(v);

function openApi(fileName: string, doc: Obj): ParsedArtifact {
  const swagger = typeof doc.swagger === 'string';
  const a = base(fileName, swagger ? 'swagger' : 'openapi', swagger ? `Swagger ${doc.swagger}` : `OpenAPI ${doc.openapi}`);
  a.protocol = 'REST';
  a.name = doc.info?.title;
  a.version = doc.info?.version !== undefined ? String(doc.info.version) : undefined;
  for (const [path, item] of Object.entries<Obj>(doc.paths ?? {})) {
    for (const m of HTTP_METHODS) if (isObj(item?.[m])) a.operations.push({ name: `${m.toUpperCase()} ${path}`, method: m.toUpperCase(), resource: path });
  }
  for (const [name, item] of Object.entries<Obj>(doc.webhooks ?? {})) {
    for (const m of HTTP_METHODS) if (isObj(item?.[m])) a.operations.push({ name: `${m.toUpperCase()} ${name}`, method: m.toUpperCase(), resource: name });
  }
  return a;
}

function asyncApi(fileName: string, doc: Obj): ParsedArtifact {
  const a = base(fileName, 'asyncapi', `AsyncAPI ${doc.asyncapi}`);
  a.protocol = 'EVENT';
  a.name = doc.info?.title;
  a.version = doc.info?.version !== undefined ? String(doc.info.version) : undefined;
  const ops: Operation[] = [];
  if (isObj(doc.operations)) {
    for (const [id, op] of Object.entries<Obj>(doc.operations)) {
      const chan = String(op.channel?.$ref ?? '').split('/').pop();
      const address = chan && doc.channels?.[chan]?.address;
      ops.push({ name: `${String(op.action ?? 'receive').toUpperCase()} ${address ?? chan ?? id}`, method: String(op.action ?? 'receive').toUpperCase(), resource: address ?? chan });
    }
  } else if (isObj(doc.channels)) {
    for (const [channel, item] of Object.entries<Obj>(doc.channels)) {
      if (item.subscribe) ops.push({ name: `SUBSCRIBE ${channel}`, method: 'SUBSCRIBE', resource: channel });
      if (item.publish) ops.push({ name: `PUBLISH ${channel}`, method: 'PUBLISH', resource: channel });
    }
  }
  a.operations = ops;
  return a;
}

function graphql(fileName: string, content: string): ParsedArtifact {
  const a = base(fileName, 'graphql', 'GraphQL schema');
  a.protocol = 'GRAPHQL';
  const id = content.match(/^#\s*microcksId:\s*(.+?)\s*:\s*([^\s:]+)\s*$/m);
  if (id) {
    a.name = id[1];
    a.version = id[2];
  } else {
    a.warnings.push('No "# microcksId: <name> : <version>" comment: the name is taken from the file name.');
  }
  for (const type of ['Query', 'Mutation', 'Subscription']) {
    const block = content.match(new RegExp(`type\\s+${type}\\s*\\{([^}]*)\\}`, 's'));
    if (!block) continue;
    for (const line of block[1].split('\n')) {
      const field = line.match(/^\s*([A-Za-z_]\w*)\s*[(:]/);
      if (field) a.operations.push({ name: field[1], method: type.toUpperCase(), resource: field[1] });
    }
  }
  return a;
}

function proto(fileName: string, content: string): ParsedArtifact {
  const a = base(fileName, 'protobuf', 'Protobuf');
  a.protocol = 'gRPC';
  const pkg = content.match(/^\s*package\s+([\w.]+)\s*;/m)?.[1];
  const svc = content.match(/service\s+(\w+)\s*\{/)?.[1];
  a.name = pkg && svc ? `${pkg}.${svc}` : svc;
  for (const m of content.matchAll(/rpc\s+(\w+)\s*\(/g)) a.operations.push({ name: m[1], method: 'RPC', resource: m[1] });
  return a;
}

function wsdl(fileName: string, content: string): ParsedArtifact {
  const a = base(fileName, 'wsdl', 'WSDL 1.1');
  a.protocol = 'SOAP';
  a.name = content.match(/<wsdl:service\s+name="([^"]+)"/)?.[1] ?? content.match(/<service\s+name="([^"]+)"/)?.[1];
  const portType = content.match(/<(?:wsdl:)?portType[^>]*>([\s\S]*?)<\/(?:wsdl:)?portType>/)?.[1] ?? '';
  for (const m of portType.matchAll(/<(?:wsdl:)?operation\s+name="([^"]+)"/g)) a.operations.push({ name: m[1], method: 'SOAP', resource: m[1] });
  return a;
}

function soapUi(fileName: string, content: string): ParsedArtifact {
  const a = base(fileName, 'soapui', 'SoapUI project');
  a.role = 'PRIMARY';
  a.protocol = 'SOAP';
  a.name = content.match(/<con:soapui-project[^>]*\sname="([^"]+)"/)?.[1];
  a.version = content.match(/<con:property><con:name>version<\/con:name><con:value>([^<]+)</)?.[1] ?? '1.0';
  const seen = new Set<string>();
  const iface = content.match(/<con:interface[\s\S]*?<\/con:interface>/)?.[0] ?? content;
  for (const m of iface.matchAll(/<con:operation\s[^>]*\sname="([^"]+)"/g)) {
    if (!seen.has(m[1])) {
      seen.add(m[1]);
      a.operations.push({ name: m[1], method: 'SOAP', resource: m[1] });
    }
  }
  return a;
}

function postman(fileName: string, doc: Obj): ParsedArtifact {
  const a = base(fileName, 'postman', 'Postman collection');
  a.role = 'SECONDARY';
  a.name = doc.info?.name;
  a.version = String(doc.info?.description ?? '').match(/version=([^\s]+)/)?.[1];
  let protocol: ParsedArtifact['protocol'] = 'REST';
  const walk = (items: Obj[], folder = '') => {
    for (const it of items) {
      if (Array.isArray(it.item)) walk(it.item, it.name);
      else if (it.request) {
        const body = it.request.body;
        if (body?.mode === 'graphql') {
          protocol = 'GRAPHQL';
          a.operations.push({ name: it.name, method: folder.toUpperCase().replace(/S$/, '') || 'QUERY', resource: it.name });
          continue;
        }
        const raw = String(it.request.url?.raw ?? '');
        const path = it.request.url?.path ? '/' + it.request.url.path.join('/') : raw.replace(/^[a-z]+:\/\/[^/]*/, '').replace(/^\{\{[^}]+\}\}/, '');
        const method = String(it.request.method ?? 'GET').toUpperCase();
        a.operations.push({ name: it.name, method, resource: path.split('?')[0] });
      }
    }
  };
  walk(doc.item ?? []);
  a.protocol = protocol;
  if (!a.version) a.warnings.push('No "version=x.y" in the collection description: the version defaults to 1.0.');
  return a;
}

function openCollection(fileName: string, doc: Obj): ParsedArtifact {
  const a = base(fileName, 'opencollection', 'OpenCollection');
  a.role = 'SECONDARY';
  a.protocol = 'REST';
  a.name = doc.info?.name;
  a.version = doc.info?.version !== undefined ? String(doc.info.version) : undefined;
  const walk = (items: Obj[]) => {
    for (const it of items) {
      if (Array.isArray(it.items)) walk(it.items);
      else if (it.http) {
        const url = String(it.http.url ?? '').replace(/^\{\{[^}]+\}\}/, '').split('?')[0];
        a.operations.push({ name: it.info?.name ?? url, method: String(it.http.method ?? 'GET').toUpperCase(), resource: url });
      }
    }
  };
  walk(doc.items ?? []);
  return a;
}

function har(fileName: string, doc: Obj): ParsedArtifact {
  const a = base(fileName, 'har', 'HAR capture');
  a.role = 'SECONDARY';
  a.protocol = 'REST';
  const id = String(doc.log?.comment ?? '').match(/microcksId:\s*(.+?)\s*:\s*([^\s]+)/);
  const prefix = String(doc.log?.comment ?? '').match(/apiPrefix:\s*(\S+)/)?.[1] ?? '';
  a.name = id?.[1];
  a.version = id?.[2];
  const seen = new Set<string>();
  for (const e of doc.log?.entries ?? []) {
    const url = new URL(e.request.url);
    const path = decodeURIComponent(url.pathname).replace(prefix.replace(/\+/g, ' '), '') || '/';
    const key = `${e.request.method} ${path}`;
    if (!seen.has(key)) {
      seen.add(key);
      a.operations.push({ name: key, method: e.request.method, resource: path });
    }
  }
  if (!id) a.warnings.push('No microcksId in the HAR comment: it cannot be attached to a service.');
  return a;
}

function metadata(fileName: string, doc: Obj, content: string): ParsedArtifact {
  const a = base(fileName, 'metadata', 'APIMetadata');
  a.role = 'SECONDARY';
  a.name = doc.metadata?.name;
  // Read the version from the raw text: YAML would turn 1.0 into the number 1.
  a.version = content.match(/^\s+version:\s*['"]?([^'"\s]+)/m)?.[1];
  for (const [k, v] of Object.entries<unknown>(doc.metadata?.labels ?? {})) a.labels[k] = String(v);
  for (const op of Object.keys(doc.operations ?? {})) a.operations.push({ name: op, method: 'RULES', resource: op });
  return a;
}

/** Recognises an artifact from its file name and content, as the Microcks importers do. */
/** An Arazzo document: its workflows are listed as the "operations" of the preview (workflow id, step count). */
function arazzo(fileName: string, doc: Obj): ParsedArtifact {
  const a = base(fileName, 'arazzo', `Arazzo ${doc.arazzo}`);
  a.name = doc.info?.title;
  a.version = doc.info?.version !== undefined ? String(doc.info.version) : undefined;
  for (const wf of Array.isArray(doc.workflows) ? (doc.workflows as Obj[]) : []) {
    const steps = Array.isArray(wf.steps) ? wf.steps.length : 0;
    a.operations.push({ name: String(wf.workflowId), method: 'WORKFLOW', resource: `${steps} step${steps === 1 ? '' : 's'}` });
  }
  if (!a.name || !a.version) a.warnings.push('An Arazzo document needs info.title and info.version: Microcks will refuse it.');
  return a;
}

/** An ArazzoMetadata: it links the source descriptions of an Arazzo document to Microcks APIs. */
function arazzoMetadata(fileName: string, doc: Obj): ParsedArtifact {
  const a = base(fileName, 'arazzo-metadata', 'ArazzoMetadata');
  a.role = 'SECONDARY';
  a.name = doc.metadata?.name;
  a.version = doc.metadata?.version !== undefined ? String(doc.metadata.version) : undefined;
  for (const [source, api] of Object.entries<Obj>(isObj(doc.sources) ? doc.sources : {})) {
    a.operations.push({ name: source, method: 'LINK', resource: `${api?.name ?? '?'} ${api?.version ?? ''}`.trim() });
  }
  return a;
}

/** An ArazzoExamples: it records examples of the workflows of an Arazzo document, which must be imported first. */
function arazzoExamples(fileName: string, doc: Obj): ParsedArtifact {
  const a = base(fileName, 'arazzo-examples', 'ArazzoExamples');
  a.role = 'SECONDARY';
  a.name = doc.metadata?.name;
  a.version = doc.metadata?.version !== undefined ? String(doc.metadata.version) : undefined;
  for (const [workflowId, examples] of Object.entries<Obj>(isObj(doc.workflows) ? doc.workflows : {})) {
    const count = isObj(examples) ? Object.keys(examples).length : 0;
    a.operations.push({ name: workflowId, method: 'EXAMPLES', resource: `${count} example${count === 1 ? '' : 's'}` });
  }
  return a;
}

/** Arazzo documents, ArazzoMetadata and ArazzoExamples are imported as workflows, not as APIs. */
export const isWorkflowArtifact = (a: ParsedArtifact) =>
  a.kind === 'arazzo' || a.kind === 'arazzo-metadata' || a.kind === 'arazzo-examples';

export function parseArtifact(fileName: string, content: string): ParsedArtifact {
  const lower = fileName.toLowerCase();
  if (lower.endsWith('.proto')) return proto(fileName, content);
  if (lower.endsWith('.graphql') || lower.endsWith('.graphqls')) return graphql(fileName, content);
  if (lower.endsWith('.wsdl')) return wsdl(fileName, content);
  if (lower.endsWith('.xml') || lower.includes('soapui')) {
    if (content.includes('soapui-project')) return soapUi(fileName, content);
    if (content.includes('definitions')) return wsdl(fileName, content);
  }
  if (lower.endsWith('.har')) {
    const doc = tryJson(content);
    if (isObj(doc) && isObj(doc.log)) return har(fileName, doc);
  }
  const doc = lower.endsWith('.json') ? tryJson(content) : tryYaml(content) ?? tryJson(content);
  if (isObj(doc)) {
    if (doc.arazzo) return arazzo(fileName, doc);
    if (doc.kind === 'ArazzoMetadata') return arazzoMetadata(fileName, doc);
    if (doc.kind === 'ArazzoExamples') return arazzoExamples(fileName, doc);
    if (doc.openapi || doc.swagger) return openApi(fileName, doc);
    if (doc.asyncapi) return asyncApi(fileName, doc);
    if (isObj(doc.info) && String(doc.info.schema ?? '').includes('postman')) return postman(fileName, doc);
    if (doc.opencollection) return openCollection(fileName, doc);
    if (doc.kind === 'APIMetadata') return metadata(fileName, doc, content);
  }
  const a = base(fileName, 'unknown', 'Unknown');
  a.warnings.push('Microcks could not recognise this artifact.');
  return a;
}
