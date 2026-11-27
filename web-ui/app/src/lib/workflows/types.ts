/**
 * Pure, client-safe types of the Arazzo / workflow domain (ported from the Deep Ink demo `domain/arazzo`).
 * Owned by the `workflows` domain; the workflow-run pages read these and may only add to them.
 * The data lives in `#lib/placeholders/workflows.js` (Microcks has no Arazzo API yet).
 */

export type SourceProtocol = 'REST' | 'SOAP' | 'EVENT' | 'gRPC' | 'GRAPHQL' | 'DIRECT' | 'A2A';

/** Why an Arazzo feature outside the POC subset was refused at import. */
export type ImportIssueCode =
  | 'goto'
  | 'jsonpath-criteria'
  | 'xpath-criteria'
  | 'workflow-id-step'
  | 'operation-path-step'
  | 'workflow-depends-on'
  | 'workflow-success-actions'
  | 'workflow-failure-actions'
  | 'components-action'
  | 'cookie-parameter';

export interface ImportIssue {
  code: ImportIssueCode;
  /** JSON pointer-like location, e.g. `workflows[0].steps[2].onFailure[0]`. */
  path: string;
  message: string;
}

/** A source description, linked (matched) or not (no API imported yet) to a Microcks API. */
export type SourceLink =
  | { status: 'linked'; serviceId: string; protocol: SourceProtocol; operationsFound: string }
  | { status: 'not-linked'; expectedFile: string };

export interface SourceDescription {
  name: string;
  type: 'openapi' | 'asyncapi';
  url: string;
  link: SourceLink;
}

export interface ArazzoDocument {
  /** `pastry` | `beer` in the placeholder. */
  id: string;
  /** Identifier used in the API snippet (hex). */
  apiId: string;
  file: string;
  title: string;
  arazzoVersion: string;
  version: string;
  /** Where the document comes from, e.g. "Synced 12 min ago from GitHub". */
  origin: string;
  /** The importer (source job) that keeps the document in sync, when one does. */
  job?: { id: string; name: string };
  /** Name of the ArazzoSources overlay file, when there is one. */
  overlay?: string;
  sources: SourceDescription[];
  workflowIds: string[];
  /** Features refused at import (empty for a clean document). */
  importIssues: ImportIssue[];
}

export type Criterion =
  | { type: 'simple'; condition: string }
  | { type: 'regex'; context: string; condition: string };

/** Supported actions: `retry` and `end` only. There is NO `goto` in the POC. */
export type Action =
  | { type: 'end'; name: string; criteria?: Criterion[] }
  | { type: 'retry'; name: string; retryAfter: number; retryLimit: number; criteria?: Criterion[] };

export interface StepParameter {
  name: string;
  in: 'path' | 'query' | 'header';
  /** Runtime expression or literal, e.g. `$inputs.size`. */
  value: string;
}

export interface NamedExpression {
  name: string;
  expression: string;
}

export interface Step {
  stepId: string;
  /** Name of the source description the operation belongs to. */
  sourceName: string;
  operationId: string;
  method: string;
  path: string;
  description: string;
  parameters: StepParameter[];
  requestBody?: string;
  successCriteria: Criterion[];
  onSuccess: Action[];
  onFailure: Action[];
  outputs: NamedExpression[];
  /** Step-level dependsOn (Arazzo 1.1): parallel branches that join. */
  dependsOn?: string[];
}

export interface WorkflowInput {
  name: string;
  type: 'string' | 'number' | 'integer' | 'boolean';
  example?: string;
}

export type Runnability =
  | { runnable: true }
  | { runnable: false; reason: 'asyncapi-steps' | 'source-not-linked'; message: string };

export interface Workflow {
  workflowId: string;
  documentId: string;
  summary: string;
  /** Longer description of the workflow (the Workflow page lead). */
  description?: string;
  inputs: WorkflowInput[];
  steps: Step[];
  outputs: NamedExpression[];
  /** Mock URL (POST) that plays the workflow on the mocks. */
  mockUrl: string;
  runnability: Runnability;
}

/** A recorded example from the ArazzoSources overlay. */
export interface WorkflowExample {
  id: string;
  workflowId: string;
  description: string;
  inputs: Record<string, string | number>;
  steps: { stepId: string; status: number; outputs?: Record<string, string> }[];
  /** Step at which the example stops, when it ends early. */
  stopsAt?: string;
  outputs: Record<string, string | number>;
  /** Last judgement of a run that played the example, when that run is no longer in the run list. */
  lastJudged?: { runNumber: number; at: string; matched: boolean };
}

export type RunTarget =
  | { kind: 'mocks' }
  | { kind: 'implementations'; baseUrls: Record<string, string> }
  /**
   * Some sources on their mocks, the others on their implementation (S31). `baseUrls` holds the implementations of a
   * run Microcks executed; `proposed` marks the placeholder runs of the mockup, where Mixed was beyond the POC.
   */
  | { kind: 'mixed'; perSource: Record<string, 'mock' | 'implementation'>; baseUrls?: Record<string, string>; proposed?: true };

export type RunOrigin =
  | { kind: 'ui' }
  | { kind: 'api' }
  | { kind: 'mock-url' }
  | { kind: 'session'; sessionId: string; app: string };

export interface RunOptions {
  /** Name of the recorded example played on the mocks. */
  example?: string;
  inputs: Record<string, unknown>;
  /** Secret name used for the implementations. */
  secret?: string;
  timeoutMs: number;
  /** Validate each response against its OpenAPI schema. Default `true`. */
  validateSchemas: boolean;
}

export const DEFAULT_RUN_TIMEOUT_MS = 10_000;

export type RunStatus = 'RUNNING' | 'SUCCESS' | 'FAILURE';
export type StepStatus = 'passed' | 'failed' | 'not-run';
export type SchemaState = 'pass' | 'fail' | 'off' | 'na';

export interface StepAttempt {
  n: number;
  httpStatus: number;
  ms: number;
  when: string;
  /** Time waited before this attempt (the `retryAfter` of an onFailure retry), in ms. */
  waitMs?: number;
}

export interface StepResult {
  stepId: string;
  status: StepStatus;
  attempts: StepAttempt[];
  request?: { method: string; url: string; params: Record<string, string>; body?: string };
  response?: { meta: string; lines: { text: string; bad?: boolean; note?: string }[] };
  criteria: { condition: string; kind: 'simple' | 'regex'; evaluation: string; held: boolean }[];
  schema: { state: SchemaState; ref: string; message: string };
  outputs: Record<string, unknown>;
  /** What happens after this step, in words (for the "flow note"). */
  flow: { kind: 'next' | 'end' | 'stop'; title: string; text: string };
  /** W3C baggage sent with the calls of this step. */
  baggage: string;
}

/** A mock example the run needed and did not find. */
export interface MockCoherenceIssue {
  stepId: string;
  operationId: string;
  /** e.g. "an example of GetPastryByName for name=Tarte Tatin" */
  missing: string;
  copilotHint: string;
}

export interface WorkflowRun {
  id: string;
  /** Run number (#14). */
  number: number;
  documentId: string;
  workflowId: string;
  origin: RunOrigin;
  target: RunTarget;
  options: RunOptions;
  status: RunStatus;
  startedAt: string;
  steps: StepResult[];
  outputs: Record<string, unknown>;
  coherence: MockCoherenceIssue[];
  relatedTestRunId?: string;
  /** Only on the intermediate frames of a RUNNING run: the step being called and its attempt number. */
  current?: { stepId: string; attempt: number };
}

export interface SessionCall {
  n: number;
  method: string;
  path: string;
  httpStatus: number;
  /** Matching step id, or `unexpected`. */
  stepId: string;
  contract: { state: 'pass' | 'fail'; text: string };
  dataPassing: { state: 'pass' | 'fail' | 'off'; text: string };
}

/** A verification session (`x-microcks-session` header). */
export interface Session {
  id: string;
  /** The application that calls the mocks, e.g. "checkout-web · saga tests". */
  app: string;
  documentId: string;
  workflowId: string;
  state: 'open' | 'closed';
  openedAt: string;
  closedAt?: string;
  inputs: Record<string, unknown>;
  /** Step to fail and the example that answers it, when failure injection is on. */
  failureInjection?: { stepId: string; example: string };
  calls: SessionCall[];
  verification?: { result: 'SUCCESS' | 'FAILURE'; runNumber: number; problems: { lead: string; text: string }[] };
}

/** Filter of the Workflows screen: "Any target / Mocks / Implementations / Mock URL / Session". */
export type RunKindFilter = 'any' | 'mocks' | 'implementations' | 'mock-url' | 'session';
