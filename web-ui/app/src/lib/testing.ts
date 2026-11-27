import { microcksBaseUrl } from '#lib/base-url.js';
import type { Service } from '#lib/types.js';

export type TestRunner =
  | 'HTTP'
  | 'SOAP_HTTP'
  | 'SOAP_UI'
  | 'POSTMAN'
  | 'OPEN_API_SCHEMA'
  | 'ASYNC_API_SCHEMA'
  | 'GRPC_PROTOBUF'
  | 'GRAPHQL_SCHEMA';

export type TestTargetType = 'REST' | 'EVENT' | 'GRPC' | 'GRAPHQL' | 'SOAP_HTTP';

export const TEST_RUNNERS: Record<TestRunner, { description: string }> = {
  HTTP: { description: 'Smoke test: each example request gets a 2xx (or the expected 404) from the endpoint.' },
  OPEN_API_SCHEMA: { description: 'Replays the examples, checks the expected status and validates every payload against the OpenAPI schemas.' },
  SOAP_HTTP: { description: 'HTTP check, plus response validation against the WSDL and XSD schemas.' },
  SOAP_UI: { description: 'Checks the assertions written in the SoapUI test cases.' },
  POSTMAN: { description: 'Runs the test scripts defined in the Postman collection.' },
  ASYNC_API_SCHEMA: { description: 'Connects to the broker, consumes messages and validates them against the AsyncAPI schema.' },
  GRPC_PROTOBUF: { description: 'Replays the examples and validates responses against the Protobuf schema.' },
  GRAPHQL_SCHEMA: { description: 'Replays the examples and validates responses against the GraphQL schema.' }
};

export interface TestTargetOperation {
  id: string;
  verb: string;
  name: string;
  path: string;
}

export interface TestTarget {
  serviceId: string;
  type: TestTargetType;
  /** Contract artifacts, e.g. "OpenAPI 3.0.2 + Postman collection". */
  artifacts: string;
  endpointHelp: string;
  endpointPlaceholder: string;
  runners: { id: TestRunner; recommended?: boolean; unavailableReason?: string }[];
  operations: TestTargetOperation[];
}

const REST_HELP = 'HTTP(S) base URL of the implementation. Microcks appends each operation path to it.';

function targetType(s: Service): TestTargetType {
  switch (s.protocol) {
    case 'EVENT':
      return 'EVENT';
    case 'gRPC':
      return 'GRPC';
    case 'GRAPHQL':
      return 'GRAPHQL';
    case 'SOAP':
      return 'SOAP_HTTP';
    case 'DIRECT':
      return s.operations.some((o) => o.method === 'RECEIVE') ? 'EVENT' : 'REST';
    default:
      return 'REST';
  }
}

/** What a service offers to the "Run a test" form, derived from the artifacts it was built from. */
export function targetFor(s: Service): TestTarget {
  const type = targetType(s);
  const kinds = new Set(s.artifacts.map((a) => a.kind));
  const has = (...k: string[]) => k.some((x) => kinds.has(x));
  const need = (ok: boolean, what: string) => (ok ? undefined : `Needs ${what}: none imported`);
  let runners: TestTarget['runners'];
  let prefer: TestRunner[];
  switch (type) {
    case 'EVENT':
      runners = [{ id: 'ASYNC_API_SCHEMA', unavailableReason: need(has('asyncapi'), 'an AsyncAPI contract') }];
      prefer = ['ASYNC_API_SCHEMA'];
      break;
    case 'GRPC':
      runners = [{ id: 'GRPC_PROTOBUF', unavailableReason: need(has('protobuf'), 'a Protobuf contract') }];
      prefer = ['GRPC_PROTOBUF'];
      break;
    case 'GRAPHQL':
      runners = [
        { id: 'HTTP' },
        { id: 'GRAPHQL_SCHEMA', unavailableReason: need(has('graphql'), 'a GraphQL schema') },
        { id: 'POSTMAN', unavailableReason: need(has('postman'), 'a Postman collection') }
      ];
      prefer = ['GRAPHQL_SCHEMA', 'POSTMAN', 'HTTP'];
      break;
    case 'SOAP_HTTP':
      runners = [
        { id: 'HTTP' },
        { id: 'SOAP_HTTP', unavailableReason: need(has('wsdl'), 'a WSDL contract') },
        { id: 'SOAP_UI', unavailableReason: need(has('soapui'), 'a SoapUI project') }
      ];
      prefer = ['SOAP_HTTP', 'SOAP_UI', 'HTTP'];
      break;
    default:
      runners = [
        { id: 'HTTP' },
        { id: 'OPEN_API_SCHEMA', unavailableReason: need(has('openapi', 'swagger'), 'an OpenAPI contract') },
        { id: 'POSTMAN', unavailableReason: need(has('postman'), 'a Postman collection') },
        { id: 'SOAP_UI', unavailableReason: need(has('soapui'), 'a SoapUI project') }
      ];
      prefer = ['OPEN_API_SCHEMA', 'POSTMAN', 'HTTP'];
  }
  const best = prefer.find((id) => runners.some((r) => r.id === id && !r.unavailableReason));
  const help: Record<TestTargetType, [string, string]> = {
    REST: [REST_HELP, 'https://my-api.example.com/v1'],
    EVENT: ['Broker URL and topic or channel, e.g. kafka://host:9092/topic. Options go after a ?, separated by &.', 'kafka://broker.example.com:9092/topic'],
    GRPC: ['gRPC server host and port, e.g. grpc://host:9000.', 'grpc://my-api.example.com:9000'],
    GRAPHQL: ['URL of the GraphQL endpoint of the implementation.', 'https://my-api.example.com/graphql'],
    SOAP_HTTP: ['URL of the deployed WebService endpoint.', 'https://my-api.example.com/Service']
  };
  return {
    serviceId: s.id,
    type,
    artifacts: s.artifacts.map((a) => a.format).join(' + ') || s.format,
    endpointHelp: help[type][0],
    endpointPlaceholder: help[type][1],
    runners: runners.map((r) => ({ ...r, recommended: r.id === best })),
    operations: s.operations.map((o) => ({ id: o.name, verb: o.method, name: o.name, path: o.resource ?? o.name }))
  };
}

export const recommendedRunner = (t: TestTarget): TestRunner | '' => t.runners.find((r) => r.recommended)?.id ?? '';

export interface CiHeader {
  /** `globals` or an operation name. */
  scope: string;
  name: string;
  values: string;
}

export interface TestFormValues {
  endpoint: string;
  runner: string;
  /** Raw text of the timeout field (milliseconds). */
  timeout: string;
  selectedOperations: string[];
  oauth: boolean;
  clientSecret: string;
}

export interface TestFormErrors {
  endpoint?: string;
  runner?: string;
  operations?: string;
  timeout?: string;
  clientSecret?: string;
}

export const TIMEOUT_MIN_MS = 1000;
export const TIMEOUT_MAX_MS = 30000;

export function parseTimeout(text: string): number | null {
  if (!/^\s*\d+\s*$/.test(text)) return null;
  const ms = parseInt(text, 10);
  return ms >= TIMEOUT_MIN_MS && ms <= TIMEOUT_MAX_MS ? ms : null;
}

export function validateEndpoint(type: TestTargetType, endpoint: string): string | undefined {
  const ep = endpoint.trim();
  if (!ep) return 'A test endpoint is required.';
  if (type === 'EVENT' && !/^(kafka|mqtt|amqp|nats|ws|wss|googlepubsub|sqs|sns):\/\/.+/.test(ep)) {
    return 'Use a broker URL such as kafka://host:9092/topic.';
  }
  if (type === 'GRPC' && !/^(grpc|grpcs|https?):\/\/.+/.test(ep)) return 'Use a gRPC endpoint such as grpc://host:9000.';
  if (type !== 'EVENT' && type !== 'GRPC' && !/^https?:\/\/\S+$/.test(ep)) return 'Use an http:// or https:// URL.';
  return undefined;
}

export function validateTestForm(target: TestTarget, v: TestFormValues): TestFormErrors {
  const errors: TestFormErrors = {};
  const endpoint = validateEndpoint(target.type, v.endpoint);
  if (endpoint) errors.endpoint = endpoint;
  const runner = target.runners.find((r) => r.id === v.runner);
  if (!runner || runner.unavailableReason) errors.runner = 'No test runner is available for this service.';
  if (v.selectedOperations.length === 0) errors.operations = 'Select at least one operation to test.';
  if (parseTimeout(v.timeout) === null) errors.timeout = 'Enter a value between 1,000 and 30,000 ms.';
  if (v.oauth && !v.clientSecret) errors.clientSecret = 'The OAuth2 client secret is required.';
  return errors;
}

export function summarizeSelection(selected: number, runner: string, waitSec: number): string {
  return `${selected} ${selected === 1 ? 'operation' : 'operations'} · ${runner || 'no runner'} · up to ${waitSec} s`;
}

export function timeoutHelp(text: string, isEvent: boolean): string {
  const ms = parseTimeout(text);
  if (ms === null) return 'Enter a value between 1,000 and 30,000 ms.';
  return `Waits up to ${Math.round(ms / 1000)} s for the results${isEvent ? ', listening for messages.' : '.'} Between 1,000 and 30,000 ms.`;
}

export type CiFormat = 'cli' | 'gha' | 'tekton';

export interface CiSnippetInput {
  /** `API Pastry:2.0`. */
  apiRef: string;
  apiName: string;
  endpoint: string;
  runner: string;
  waitSec: number;
  secret: string;
  /** When true, no operation filter is written. */
  allOperations: boolean;
  selectedOperations: string[];
  headers: CiHeader[];
  isEvent: boolean;
}


const SECRET_REF = (name: string) => `$${'{{'} secrets.${name} ${'}}'}`;

export const CI_FORMATS: { id: CiFormat; label: string }[] = [
  { id: 'cli', label: 'microcks CLI' },
  { id: 'gha', label: 'GitHub Actions' },
  { id: 'tekton', label: 'Tekton' }
];

export const CI_STEPS: Record<CiFormat, string[]> = {
  cli: [
    'Install the microcks CLI (Homebrew, binary or the quay.io/microcks/microcks-cli image).',
    'Export MICROCKS_SA_SECRET with the secret of a Keycloak service account.',
    'The command exits non-zero when the test fails, so the build fails with it.'
  ],
  gha: [
    'Store the service account as MICROCKS_SERVICE_ACCOUNT and MICROCKS_SERVICE_ACCOUNT_SECRET repository secrets.',
    `Add the step after your deployment step; ${microcksBaseUrl()} must be reachable from the runner.`
  ],
  tekton: [
    'Make sure the microcks-test Task is installed in the pipeline namespace.',
    'Keep the service account credentials in a Kubernetes Secret referenced by the Task.'
  ]
};

function headersJson(input: CiSnippetInput): string {
  if (input.isEvent) return '';
  const grouped: Record<string, { name: string; values: string }[]> = {};
  for (const h of input.headers) {
    if (!h.name.trim()) continue;
    (grouped[h.scope] ??= []).push({ name: h.name.trim(), values: h.values });
  }
  return Object.keys(grouped).length ? JSON.stringify(grouped) : '';
}

/** One line per array entry. Every form choice changes a line of the snippet. */
export function buildCiSnippet(input: CiSnippetInput, format: CiFormat): string[] {
  const ep = input.endpoint.trim() || '<test-endpoint>';
  const runner = input.runner || '<runner>';
  const wait = `${input.waitSec}sec`;
  const filtered = input.allOperations ? '' : JSON.stringify(input.selectedOperations);
  const headers = headersJson(input);
  const lines: string[] = [];
  if (format === 'cli') {
    lines.push(`microcks test '${input.apiRef}' ${ep} ${runner} \\`);
    lines.push(`  --microcksURL=${microcksBaseUrl()}/api/ \\`);
    lines.push('  --keycloakClientId=microcks-serviceaccount \\');
    lines.push('  --keycloakClientSecret="$MICROCKS_SA_SECRET" \\');
    if (input.secret) lines.push(`  --secretName=${input.secret} \\`);
    if (filtered) lines.push(`  --filteredOperations='${filtered}' \\`);
    if (headers) lines.push(`  --operationsHeaders='${headers}' \\`);
    lines.push(`  --waitFor=${wait}`);
  } else if (format === 'gha') {
    lines.push(`- name: Contract test ${input.apiRef.replace(/:([^:]*)$/, ' $1')}`);
    lines.push('  uses: microcks/test-github-action@v1');
    lines.push('  with:');
    lines.push(`    apiNameAndVersion: '${input.apiRef}'`);
    lines.push(`    testEndpoint: '${ep}'`);
    lines.push(`    runner: ${runner}`);
    lines.push(`    microcksURL: '${microcksBaseUrl()}/api/'`);
    lines.push(`    keycloakClientId: ${SECRET_REF('MICROCKS_SERVICE_ACCOUNT')}`);
    lines.push(`    keycloakClientSecret: ${SECRET_REF('MICROCKS_SERVICE_ACCOUNT_SECRET')}`);
    if (input.secret) lines.push(`    secretName: '${input.secret}'`);
    if (filtered) lines.push(`    filteredOperations: '${filtered}'`);
    if (headers) lines.push(`    operationsHeaders: '${headers}'`);
    lines.push(`    waitFor: '${wait}'`);
  } else {
    lines.push(`- name: contract-test-${input.apiName.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`);
    lines.push('  taskRef:');
    lines.push('    name: microcks-test');
    lines.push('  params:');
    lines.push('    - name: apiNameAndVersion');
    lines.push(`      value: "${input.apiRef}"`);
    lines.push('    - name: testEndpoint');
    lines.push(`      value: ${ep}`);
    lines.push('    - name: runner');
    lines.push(`      value: ${runner}`);
    lines.push('    - name: microcksURL');
    lines.push(`      value: ${microcksBaseUrl()}/api/`);
    if (input.secret) {
      lines.push('    - name: secretName');
      lines.push(`      value: ${input.secret}`);
    }
    if (headers) {
      lines.push('    - name: operationsHeaders');
      lines.push(`      value: '${headers}'`);
    }
    if (filtered) {
      lines.push('    - name: filteredOperations');
      lines.push(`      value: '${filtered}'`);
    }
    lines.push('    - name: waitFor');
    lines.push(`      value: ${wait}`);
  }
  return lines;
}

export interface CiLine {
  text: string;
  /** The line differs from the default configuration of the service. */
  changed: boolean;
}

export function diffCiLines(lines: string[], defaults: string[]): CiLine[] {
  return lines.map((text) => ({ text, changed: !defaults.includes(text) }));
}

export function ciDiffLegend(changed: number, serviceLabel: string): string {
  return changed === 0
    ? `Default configuration for ${serviceLabel}. Change the form: the lines you affect light up.`
    : `${changed} ${changed === 1 ? 'line differs' : 'lines differ'} from the default configuration.`;
}

export interface CreateTestRequest {
  serviceId: string;
  endpoint: string;
  runner: string;
  timeoutMs: number;
  secretName?: string;
  operations: string[];
  headers: { scope?: string; name: string; value: string }[];
}
