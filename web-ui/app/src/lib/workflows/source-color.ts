import type { ArazzoDocument } from './types.js';

const PROTO_VAR: Record<string, string> = {
  REST: 'var(--proto-rest)',
  SOAP: 'var(--proto-soap)',
  EVENT: 'var(--proto-event)',
  gRPC: 'var(--proto-grpc)',
  GRAPHQL: 'var(--proto-graphql)',
  DIRECT: 'var(--proto-direct)',
  A2A: 'var(--proto-a2a)'
};

/** Colour token of a source lane: its protocol colour when linked, the warning colour when not. */
export function sourceColor(document: ArazzoDocument, sourceName: string): { color: string; linked: boolean } {
  const source = document.sources.find((s) => s.name === sourceName);
  if (!source || source.link.status !== 'linked') return { color: 'var(--warning)', linked: false };
  return { color: PROTO_VAR[source.link.protocol] ?? 'var(--proto-rest)', linked: true };
}
