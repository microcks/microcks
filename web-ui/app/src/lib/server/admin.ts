import type { AdminUser, AdminGroup, AdminMember, FeatureFlag, SecretInput, SecretKind, SecretSummary } from '#lib/admin.js';
import {
  BackendError,
  addBackendMember,
  deleteBackendUser,
  fetchBackendUsers,
  setBackendUserDisabled,
  createBackendGroup,
  createBackendSecret,
  deleteBackendGroup,
  deleteBackendSecret,
  fetchBackendAuthConfig,
  fetchBackendFeatures,
  fetchBackendGroups,
  fetchBackendMembers,
  fetchBackendSecret,
  fetchBackendSecrets,
  removeBackendMember,
  saveBackendSecret,
  type BackendMembership,
  type BackendSecret
} from './backend';
import { initialsOf, memberLabel, secretCredentials, validateSecretForm } from '#lib/admin.js';
import { listServices, listSources } from './store';

// ─── Secrets: stored by the Microcks backend. Values never leave the server. ───

const kindOf = (s: BackendSecret): SecretKind => (s.token ? 'token' : 'basic');

/** What the credentials are, never their value. */
function toSummary(s: BackendSecret): SecretSummary {
  const kind = kindOf(s);
  const detail = secretCredentials({
    kind,
    secret: kind === 'token' ? (s.token ?? '') : (s.password ?? ''),
    user: kind === 'token' ? (s.tokenHeader ?? '') : (s.username ?? ''),
    ca: s.caCertPem
  });
  return {
    id: s.id ?? s.name,
    name: s.name,
    kind,
    description: s.description?.trim() || 'No description',
    detail,
    hasCa: Boolean(s.caCertPem),
    usedBy: listSources().filter((x) => x.secret === s.name).length
  };
}

/** Credential fields of the backend Secret for a form; an empty value keeps `current`. */
function toBackend(input: Omit<SecretInput, 'name'>, current?: BackendSecret): Omit<BackendSecret, 'id' | 'name'> {
  const keep = (v: string, old?: string) => v || old || undefined;
  const base = {
    description: input.description.trim() || undefined,
    caCertPem: input.clearCa ? undefined : keep(input.ca ?? '', current?.caCertPem)
  };
  switch (input.kind) {
    case 'basic':
      return { ...base, username: keep(input.user, current?.username), password: keep(input.secret, current?.password) };
    case 'token':
      return { ...base, token: keep(input.secret, current?.token), tokenHeader: input.user || current?.tokenHeader || undefined };
  }
}

let secretCache: SecretSummary[] = [];

/** Last listing, for synchronous demo code paths (workflow runs). */
export const cachedSecrets = () => secretCache;

export async function listSecrets(token?: string): Promise<SecretSummary[]> {
  secretCache = (await fetchBackendSecrets(token)).map(toSummary);
  return secretCache;
}

export async function createSecret(input: SecretInput, token?: string): Promise<SecretSummary | { error: string }> {
  const existing = await fetchBackendSecrets(token);
  const errors = validateSecretForm(input, existing.map((s) => s.name));
  const first = errors.name ?? errors.secret;
  if (first) return { error: first };
  return toSummary(await createBackendSecret({ name: input.name, ...toBackend(input) }, token));
}

/** The name is fixed; an empty credential keeps the stored one (the backend replaces the whole Secret). */
export async function updateSecret(id: string, input: Omit<SecretInput, 'name'>, token?: string): Promise<SecretSummary | { error: string; status: number }> {
  const current = await fetchBackendSecret(id, token);
  if (!current) return { error: `Secret "${id}" does not exist.`, status: 404 };
  const sameKind = kindOf(current) === input.kind;
  return toSummary(await saveBackendSecret({ id, name: current.name, ...toBackend(input, sameKind ? current : undefined) }, token));
}

export const deleteSecret = (id: string, token?: string) => deleteBackendSecret(id, token);

// ─── Configuration: what the Microcks backend reports (features, authentication). ───

export async function listFlags(token?: string): Promise<FeatureFlag[]> {
  const [features, auth] = await Promise.all([fetchBackendFeatures(token), fetchBackendAuthConfig()]);
  const f = (name: string, prop: string) => features[name]?.[prop];
  const on = (name: string) => f(name, 'enabled') === 'true';
  const key = (name: string) => `features.feature.${name}.enabled=${f(name, 'enabled') ?? 'unset'}`;
  const bindings = f('async-api', 'default-binding') ?? 'KAFKA';
  const endpoint = f('async-api', `endpoint-${bindings}`);
  return [
    { id: 'async-api', label: 'Async API mocking', description: 'Event mocks published on brokers', enabled: on('async-api'), value: `${bindings} ${endpoint ?? ''}`.trim(), key: key('async-api') },
    { id: 'ai-copilot', label: 'AI Copilot', description: 'Example generation from the contract', enabled: on('ai-copilot'), value: '—', key: key('ai-copilot') },
    {
      id: 'auth',
      label: 'Authentication',
      description: 'Sign-in, roles and groups. Off by default; any OIDC provider can be connected',
      enabled: auth.enabled,
      value: auth.enabled ? (auth.oidcEnabled ? 'OIDC provider' : 'built-in accounts') : 'anonymous access',
      key: `microcks.auth.enabled=${auth.enabled}`
    },
    { id: 'tenancy', label: 'Repository tenancy', description: 'Manager groups per master label value', enabled: on('repository-tenancy'), value: 'master label domain', key: key('repository-tenancy') },
    { id: 'hub', label: 'Microcks Hub', description: 'Browse and install API mocks from the Hub', enabled: on('microcks-hub'), value: f('microcks-hub', 'endpoint') ?? '—', key: key('microcks-hub') },
    {
      id: 'filter',
      label: 'Repository filter',
      description: 'Filter the catalogue by a label',
      enabled: on('repository-filter'),
      value: f('repository-filter', 'label-key') ? `label ${f('repository-filter', 'label-key')}` : '—',
      key: key('repository-filter')
    }
  ];
}

// ─── Groups and memberships: internal to the Microcks backend (M2-003), not read from the identity provider. ───

const MEMBER_ROLES: readonly string[] = ['user', 'manager'];

const toMember = (m: BackendMembership): AdminMember => ({
  id: m.id,
  role: m.role === 'manager' ? 'manager' : 'user',
  subject: m.subject ?? undefined,
  email: m.email ?? undefined,
  pending: !m.subject
});

/** Services are attached to a group through their `domain` label (the master label of repository tenancy). */
export async function listGroups(token?: string): Promise<AdminGroup[]> {
  const groups = await fetchBackendGroups(token);
  const members = await Promise.all(groups.map((g) => fetchBackendMembers(g.name, token)));
  return groups.map((g, i) => ({
    name: g.name,
    description: g.description?.trim() ?? '',
    members: (members[i] ?? []).map(toMember),
    services: listServices()
      .filter((s) => s.labels.domain === g.name)
      .map((s) => `${s.name} ${s.version}`)
  }));
}

export async function createGroup(name: string, description: string, token?: string): Promise<AdminGroup | { error: string }> {
  if (!/^[^/\s]+$/.test(name)) return { error: 'A group name has no space and no slash.' };
  try {
    await createBackendGroup({ name, description: description.trim() || undefined }, token);
  } catch (e) {
    if (e instanceof BackendError && e.status === 409) return { error: `Group "${name}" already exists.` };
    throw e;
  }
  return { name, description: description.trim(), members: [], services: [] };
}

export const deleteGroup = (name: string, token?: string) => deleteBackendGroup(name, token);

/** `who` is an email when it contains an @, an OIDC subject otherwise. */
export async function addMember(group: string, who: string, role: string, token?: string): Promise<AdminMember | { error: string }> {
  const target = who.trim();
  if (!target) return { error: 'Enter a username or an email.' };
  if (!MEMBER_ROLES.includes(role)) return { error: 'Role must be user or manager.' };
  try {
    return toMember(await addBackendMember(group, { role, ...(target.includes('@') ? { email: target } : { subject: target }) }, token));
  } catch (e) {
    if (e instanceof BackendError && e.status === 400) return { error: 'The backend rejected this member.' };
    throw e;
  }
}

export const removeMember = (group: string, membershipId: string, token?: string) => removeBackendMember(group, membershipId, token);

// ─── Users: created by the backend at first sign-in, joined here with the memberships. ───

export async function listUsers(token?: string): Promise<AdminUser[]> {
  const [users, groups] = await Promise.all([fetchBackendUsers(token), listGroups(token)]);
  const byKey = new Map<string, AdminUser>();
  for (const u of users) {
    byKey.set(u.username, {
      id: u.username,
      userId: u.id,
      name: u.name?.trim() || u.username,
      email: u.email ?? undefined,
      emailVerified: u.emailVerified === true,
      roles: u.roles ?? [],
      lastLoginAt: u.lastLoginAt ? new Date(u.lastLoginAt).toISOString() : undefined,
      disabled: u.disabled === true,
      pending: false,
      initials: initialsOf(u.name?.trim() || u.username),
      memberships: []
    });
  }
  for (const g of groups) {
    for (const m of g.members) {
      const key = memberLabel(m);
      let u = byKey.get(key);
      if (!u) {
        u = {
          id: key,
          name: key,
          email: m.email,
          emailVerified: false,
          roles: [],
          disabled: false,
          pending: m.pending,
          initials: initialsOf(key),
          memberships: []
        };
        byKey.set(key, u);
      }
      u.memberships.push({ group: g.name, role: m.role });
    }
  }
  return [...byKey.values()];
}

export const setUserDisabled = (id: string, disabled: boolean, token?: string) =>
  setBackendUserDisabled(id, disabled, token);

export const deleteUser = (id: string, token?: string) => deleteBackendUser(id, token);
