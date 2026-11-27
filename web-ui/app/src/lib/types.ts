import type { Protocol } from '@microcks/deep-ink';

export type { Protocol };

export type ArtifactRole = 'PRIMARY' | 'SECONDARY';
export type SourceOrigin = 'upload' | 'url' | 'git' | 'hub';
export type SourceStatus = 'imported' | 'warning' | 'failing' | 'inactive';

export interface Operation {
  name: string;
  /** HTTP verb for REST, action for events, kind for GraphQL/gRPC/SOAP. */
  method: string;
  resource?: string;
}

/** What the parser understood from one artifact file. */
export interface ParsedArtifact {
  fileName: string;
  kind: string;
  /** Short label shown in mono, e.g. "OpenAPI 3.0". */
  format: string;
  role: ArtifactRole;
  protocol?: Protocol;
  name?: string;
  version?: string;
  operations: Operation[];
  labels: Record<string, string>;
  warnings: string[];
}

export interface Service {
  id: string;
  name: string;
  version: string;
  protocol: Protocol;
  format: string;
  operations: Operation[];
  /** Artifacts merged into this service: parser kind and display format. */
  artifacts: { kind: string; format: string }[];
  labels: Record<string, string>;
  sourceIds: string[];
  createdAt: string;
  updatedAt: string;
  /** Mock endpoint, as Microcks exposes it. */
  mockUrl: string;
}

export interface TimelineEntry {
  at: string;
  kind: 'import' | 'reimport' | 'failure';
  summary: string;
  revision?: string;
}

export interface DiffLine {
  kind: 'add' | 'del' | 'ctx';
  text: string;
}

export interface Source {
  id: string;
  name: string;
  origin: SourceOrigin;
  /** URL or repository file URL; empty for uploads. */
  url: string;
  role: ArtifactRole;
  artifact: string;
  secret?: string;
  tlsValidation: boolean;
  labels: Record<string, string>;
  active: boolean;
  createdAt: string;
  lastImportAt?: string;
  error?: string;
  warning?: string;
  serviceIds: string[];
  /** The Arazzo document this importer produced (instead of Services), when it imports one. */
  arazzo?: { documentId: string; title: string; version: string; unlinkedSources: string[] };
  timeline: TimelineEntry[];
  diff?: { revision: string; lines: DiffLine[] };
}

export interface Preview {
  artifact: ParsedArtifact;
  /** Distinct services this artifact would create or update. */
  service: { name: string; version: string; protocol: Protocol; exists: boolean };
  /** Original content, re-sent on import so nothing is fetched twice. */
  content: string;
  origin: SourceOrigin;
  url: string;
}

export interface ScanSchedule {
  cron: string;
  description: string;
  envVar: string;
  lastScanAt: string;
  nextScanAt: string;
  intervalMinutes: number;
}
