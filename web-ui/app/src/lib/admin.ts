// Client-safe types and pure helpers of the Administration area (ported from the Deep Ink demo domain).

/** Role held in a group. Managers also hold the user role. */
export type MemberRole = 'user' | 'manager';

export interface AdminMember {
  /** Membership id. */
  id: string;
  role: MemberRole;
  /** OIDC subject; absent while the membership is pending. */
  subject?: string;
  /** Email of a pending membership, resolved to a subject at first sign-in. */
  email?: string;
  pending: boolean;
}

/** A Microcks group; its name is the value of the master label (`domain`) of the Services it owns. */
export interface AdminGroup {
  name: string;
  description: string;
  members: AdminMember[];
  /** Names of the Services governed by the group (value of the master label). */
  services: string[];
}

/**
 * A person of the Users tab: either an account created at first sign-in (`userId` set) or an invitation by email that
 * nobody has claimed yet (`pending`). The identity provider stays the source of truth for identity and roles.
 */
export interface AdminUser {
  /** Username (token subject), or the email while pending. */
  id: string;
  /** Id of the stored user; absent for a pending invitation. */
  userId?: string;
  name: string;
  email?: string;
  emailVerified: boolean;
  /** Global roles asserted by the identity provider at last sign-in. */
  roles: string[];
  lastLoginAt?: string;
  disabled: boolean;
  pending: boolean;
  initials: string;
  memberships: { group: string; role: MemberRole }[];
}

export const memberLabel = (m: AdminMember) => m.subject ?? m.email ?? m.id;

export const initialsOf = (label: string) => label.replace(/[^\p{L}\p{N}]/gu, '').slice(0, 2).toUpperCase() || '?';

export interface FeatureFlag {
  id: string;
  label: string;
  description: string;
  enabled: boolean;
  value?: string;
  key?: string;
}

/** Basic credentials or a token (with an optional header); either may carry a CA certificate. */
export type SecretKind = 'basic' | 'token';

/** A named secret. The credential value is never sent back to the browser. */
export interface SecretSummary {
  id: string;
  name: string;
  kind: SecretKind;
  description: string;
  /** What the credentials are, never their value: "header PRIVATE-TOKEN · token ••••••••". */
  detail: string;
  /** A custom CA certificate is stored with the credentials. */
  hasCa: boolean;
  /** Number of sources that reference the secret by name. */
  usedBy: number;
}

export interface SecretInput {
  name: string;
  kind: SecretKind;
  description: string;
  /** Password or token. */
  secret: string;
  /** Basic username or token header. */
  user: string;
  /** Optional PEM CA certificate or chain; empty keeps the stored one when editing. */
  ca?: string;
  /** Remove the stored CA certificate. */
  clearCa?: boolean;
}

export interface SnapshotServiceRef {
  id: string;
  name: string;
  version: string;
  protocol: import('./types.js').Protocol;
  operationCount: number;
}

export interface SnapshotPreviewItem {
  name: string;
  protocol: import('./types.js').Protocol;
  effect: string;
  kind: 'new' | 'replace';
}

export const SECRET_KINDS: SecretKind[] = ['basic', 'token'];
export const SECRET_KIND_LABEL: Record<SecretKind, string> = { basic: 'Password', token: 'Token' };
export const SECRET_KIND_HINT: Record<SecretKind, string> = {
  basic: 'HTTP Basic authentication with a username and a password.',
  token: 'Bearer token, or a custom header such as PRIVATE-TOKEN.'
};
export const SECRET_NAME_PATTERN = /^[a-z0-9][a-z0-9-]{1,62}$/;

export interface SecretFormErrors {
  name?: string;
  secret?: string;
}

/** When `editing`, the name is fixed and an empty credential keeps the stored one. */
export function validateSecretForm(values: Pick<SecretInput, 'name' | 'secret'>, existingNames: readonly string[], editing = false): SecretFormErrors {
  const errors: SecretFormErrors = {};
  if (!editing) {
    if (!values.name) errors.name = 'Name required';
    else if (!SECRET_NAME_PATTERN.test(values.name)) errors.name = 'Lowercase letters, digits and dashes';
    else if (existingNames.includes(values.name)) errors.name = 'This name already exists';
    if (!values.secret) errors.secret = 'Credential required';
  }
  return errors;
}

/** First problem of the form, shown in its footer; empty when it can be saved. */
export const secretFormHint = (errors: SecretFormErrors) => errors.name ?? errors.secret ?? '';

/** What the credentials are, never their value. */
export function secretCredentials(v: Pick<SecretInput, 'kind' | 'secret' | 'user'> & { ca?: string }): string {
  const mask = v.secret ? '••••••••' : '';
  const main = v.kind === 'basic' ? `user ${v.user || '—'} · password ${mask}` : `${v.user ? `header ${v.user}` : 'Authorization: Bearer'} · token ${mask}`;
  if (!v.ca) return main;
  const n = (v.ca.match(/-----BEGIN CERTIFICATE-----/g) ?? []).length || 1;
  return `${main} · CA ${n} ${n === 1 ? 'certificate' : 'certificates'}`;
}

export const plural = (n: number, word: string, many = `${word}s`) => `${n} ${n === 1 ? word : many}`;

/** Gravatar URL for an email (SHA-256 of the trimmed, lowercased address); d=404 lets callers fall back to initials. */
export async function gravatarUrl(email: string | null | undefined): Promise<string | null> {
  if (!email || !globalThis.crypto?.subtle) return null;
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(email.trim().toLowerCase()));
  const hash = Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
  return `https://gravatar.com/avatar/${hash}?s=72&d=404`;
}
