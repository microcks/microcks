/** Formats the parser really recognises (see server/parser.ts). */
export const RECOGNIZED_FORMATS = [
  'OpenAPI',
  'AsyncAPI',
  'gRPC/Protobuf',
  'GraphQL',
  'WSDL',
  'Postman',
  'SoapUI',
  'HAR',
  'APIMetadata',
  'OpenCollection'
] as const;

export const ACCEPTED_EXTENSIONS = '.yaml,.yml,.json,.xml,.wsdl,.proto,.graphql,.har';

/** Default address of a local Microcks: this UI does not serve mocks itself. */
export const MICROCKS_BASE = 'http://localhost:8080';

export const SAMPLE_URL = 'https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml';
export const GIT_DEFAULTS = {
  name: 'API Pastry — main',
  url: 'https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml'
};
export const DIRECT_DEFAULTS = {
  name: 'Order API',
  version: '1.0',
  resource: 'order',
  payload: '{ "id": "ord-1042", "customer": "Laurent", "status": "CREATED" }'
};

export type DirectKind = 'rest' | 'event';
export interface DirectApiInput {
  kind: DirectKind;
  name: string;
  version: string;
  resource: string;
  payload: string;
}

/** An empty field takes the value its placeholder shows. */
export const or = (value: string, fallback: string) => value.trim() || fallback;

export function validateArtifactUrl(url: string): string | undefined {
  const text = url.trim();
  if (!text) return 'Enter the URL of the artifact.';
  try {
    const parsed = new URL(text);
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return 'Use an http or https URL.';
  } catch {
    return 'Enter an absolute URL, for example https://raw.githubusercontent.com/acme/contracts/main/openapi.yaml.';
  }
  return undefined;
}

export function validateGitForm(form: { name: string; url: string }) {
  const errors: { name?: string; url?: string } = {};
  if (!form.name.trim()) errors.name = 'Name the source, for example API Pastry — main.';
  const url = validateArtifactUrl(form.url);
  if (url) errors.url = url;
  return errors;
}

export function validateDirectApi(input: DirectApiInput) {
  const errors: Partial<Record<'name' | 'version' | 'resource' | 'payload', string>> = {};
  if (!input.name.trim()) errors.name = 'Name the API, for example Order API.';
  if (!input.version.trim()) errors.version = 'Give a version, for example 1.0.';
  const resource = input.resource.trim();
  if (!resource) errors.resource = 'Name the resource, for example order.';
  else if (!/^[A-Za-z][\w-]*$/.test(resource)) errors.resource = 'Use letters, digits, dashes and underscores, starting with a letter.';
  const payload = input.payload.trim();
  if (!payload && input.kind === 'event') errors.payload = 'An event API needs a reference JSON payload.';
  else if (payload) {
    try {
      JSON.parse(payload);
    } catch (e) {
      errors.payload = `Invalid JSON: ${(e as Error).message}`;
    }
  }
  return errors;
}

/** Where a mock answers and a command to try it. `base` is the Microcks origin, for example http://localhost:8080. */
export function mockEndpoint(opts: { protocol: string; mockUrl: string; name: string; base: string }) {
  const { protocol, mockUrl, base } = opts;
  if (protocol === 'REST' || protocol === 'DIRECT') {
    const url = `${base}${mockUrl}`;
    return {
      label: 'Mock base URL',
      value: url,
      tryIt: protocol === 'DIRECT' ? `curl -X POST ${url} \\\n  -H 'Content-Type: application/json' \\\n  -d '{"customer":"Laurent","status":"CREATED"}'` : `curl ${url}`
    };
  }
  if (protocol === 'SOAP' || protocol === 'GRAPHQL') return { label: 'Mock endpoint', value: `${base}${mockUrl}`, tryIt: undefined };
  if (protocol === 'EVENT') return { label: 'Mock topic pattern', value: mockUrl, tryIt: undefined };
  return { label: 'Mock endpoint', value: mockUrl, tryIt: undefined };
}
