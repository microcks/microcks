import type { Protocol, Service } from '#lib/types.js';

export type DispatcherKind = 'NONE' | 'URI_PARTS' | 'URI_PARAMS' | 'QUERY_ARGS' | 'JSON_BODY' | 'SCRIPT' | 'FALLBACK' | 'PROXY_FALLBACK';

export interface CatalogExample {
  id: string;
  name: string;
  request: { params: Record<string, string>; body?: string };
  response: { status: number; contentType: string; body: string };
  aiGenerated?: boolean;
  acceptedBy?: string;
}

export interface Behavior {
  dispatcher: DispatcherKind;
  rule: string;
  delayMs: number;
  delayStrategy: 'fixed' | 'random';
  fallback?: string;
  proxyUrl?: string;
  baseDispatcher: 'URI_PARTS' | 'URI_PARAMS';
  stateful: boolean;
  constraints: ParameterConstraint[];
  scriptLanguage?: 'GROOVY' | 'JS';
  script?: string;
}

export interface ParameterConstraint {
  name: string;
  in: 'header' | 'query';
  required: boolean;
  recopy: boolean;
  mustMatchRegexp?: string;
}

export interface CatalogOperation {
  id: string;
  name: string;
  method: string;
  path: string;
  summary: string;
  /** True when the dispatcher/delay come from the contract (x-microcks-operation) rather than a default. */
  behaviorFromContract: boolean;
  behavior: Behavior;
  examples: CatalogExample[];
}

export interface ServiceDetail {
  service: Service;
  description: string;
  /** Server relative path of the mock (`/rest/...`); the UI prefixes it with `microcksBaseUrl()`. */
  mockBase: string;
  operations: CatalogOperation[];
  artifacts: { kind: string; format: string; file: string }[];
  /** Invocations of the service per day over the last 7 days, oldest first (the backend has no per-operation counter). */
  calls: number[];
}

export interface CockpitData {
  services: number;
  sources: number;
  operations: number;
  protocols: { protocol: Protocol; count: number }[];
  wall: { id: string; name: string; version: string; protocol: Protocol; operations: number; callsToday: number; conformance: { score: number; outcomes: ('pass' | 'fail' | 'untested')[] } | null }[];
  /** Mock invocations today: total and per hour since midnight (server time). */
  invocations: { total: number; hourly: number[] };
  /** Real test results from the backend: runs of the last 7 days, latest score per tested service, newest runs. */
  tests: {
    last7d: { passed: number; failed: number };
    averageConformance: number | null;
    recent: { id: string; serviceId: string; serviceName: string; version: string; protocol: Protocol; runner: string; result: 'passed' | 'failed' | 'timeout' | 'running'; conformance: number; at: string }[];
  };
  attention: { id: string; tone: 'danger' | 'warning' | 'info'; title: string; detail: string; href: string; cta: string }[];
}

export interface Proposal {
  id: string;
  name: string;
  request: { params: Record<string, string>; body?: string };
  response: { status: number; contentType: string; body: string };
}

export const PROTOCOL_ATTR: Record<string, string> = {
  REST: 'rest',
  SOAP: 'soap',
  EVENT: 'event',
  gRPC: 'grpc',
  GRAPHQL: 'graphql',
  DIRECT: 'direct',
  A2A: 'a2a'
};
