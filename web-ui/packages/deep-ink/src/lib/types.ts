export type IconPath = string | { readonly d: string; readonly dash: string };

export type Protocol = 'REST' | 'SOAP' | 'EVENT' | 'gRPC' | 'GRAPHQL' | 'DIRECT' | 'A2A';

export const PROTOCOL_ATTR: Record<Protocol, string> = {
  REST: 'rest',
  SOAP: 'soap',
  EVENT: 'event',
  gRPC: 'grpc',
  GRAPHQL: 'graphql',
  DIRECT: 'direct',
  A2A: 'a2a'
};

export type ProofStatus =
  | 'success'
  | 'danger'
  | 'warning'
  | 'info'
  | 'action'
  | 'neutral'
  | 'muted'
  | 'text'
  | 'plain'
  | 'positive'
  | 'protocol';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'link' | 'dashed' | 'danger';
export type ButtonSize = 'sm' | 'compact' | 'tile' | 'md' | 'lg';

export type OperationResult = 'pass' | 'fail' | 'untested';
export type Outcome = 'conformant' | 'failed' | 'partial' | 'running' | 'never';

export interface SegmentedOption {
  id: string;
  label: string;
  count?: number;
  dot?: 'neutral' | 'success' | 'danger' | 'warning' | 'muted' | 'ring';
  icon?: import('./icon-paths.js').IconName;
  iconTone?: 'success' | 'warning' | 'danger' | 'muted';
}
