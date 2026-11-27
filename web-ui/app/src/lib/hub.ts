import type { Protocol } from '@microcks/deep-ink';

export type { Protocol };
export type InstallMode = 'sync' | 'one-shot';
export type ArtifactRole = 'main' | 'secondary';
export type InstallStatus = '' | 'installing' | 'installed';

export interface HubContract {
  type: string;
  url: string;
}

/** One version of an API, with its contracts: what is installed. */
export interface HubApiVersion {
  id: string;
  /** Hub name of the version, e.g. `train-travel.1.0`. */
  name: string;
  displayName: string;
  version: string;
  capabilityLevel?: string;
  contracts: HubContract[];
}

export interface HubApi {
  id: string;
  name: string;
  protocol: Protocol;
  /** All the versions the Hub lists for this API, as {name, version}. */
  versions: { name: string; version: string }[];
  currentVersion: string;
  /** Versions already fetched from the Hub. */
  details: Record<string, HubApiVersion>;
}

export interface HubPackage {
  id: string;
  name: string;
  category: string;
  description: string;
  longDescription: string;
  updated: string;
  maturity: string;
  apis: HubApi[];
}

interface RawPackage {
  name: string;
  displayName: string;
  categories?: string[];
  updatedAt: string;
  description: string;
  longDescription?: string;
  maturity?: string;
  apis: { name: string; currentVersion: string; versions: { name: string; version: string }[] }[];
}

const TIMEOUT_MS = 15_000;
const CACHE_MS = 5 * 60_000;

async function hubGet<T>(url: string): Promise<T> {
  let res: Response;
  try {
    res = await fetch(url, { headers: { accept: 'application/json' }, signal: AbortSignal.timeout(TIMEOUT_MS) });
  } catch (e) {
    throw new Error(`The Hub could not be reached (${e instanceof Error ? e.message : e}).`);
  }
  if (!res.ok) throw new Error(`The Hub answered ${res.status}.`);
  return (await res.json()) as T;
}

export function protocolOf(api: Pick<HubApiVersion, 'id' | 'contracts'>): Protocol {
  const types = api.contracts.map((c) => c.type);
  if (types.includes('asyncapi')) return 'EVENT';
  if (types.includes('grpc')) return 'gRPC';
  if (types.includes('graphql')) return 'GRAPHQL';
  if (types.includes('soapui')) return /rest/i.test(api.id) ? 'REST' : 'SOAP';
  return 'REST';
}

const PRIMARY = new Set(['openapi', 'asyncapi', 'grpc', 'graphql', 'soapui', 'postman']);
const LABELS: Record<string, string> = {
  openapi: 'OpenAPI',
  asyncapi: 'AsyncAPI',
  grpc: 'Protobuf',
  graphql: 'GraphQL schema',
  soapui: 'SoapUI project',
  postman: 'Postman collection',
  'openapi-metadata': 'metadata',
  'microcks-metadata': 'metadata'
};

/** Primary contracts first; a Postman collection is secondary only when another contract carries the API. */
export function splitContracts(contracts: HubContract[]): { primary: HubContract[]; secondary: HubContract[] } {
  const hasContract = contracts.some((c) => c.type !== 'postman' && PRIMARY.has(c.type));
  const isPrimary = (c: HubContract) => PRIMARY.has(c.type) && (c.type !== 'postman' || !hasContract);
  return { primary: contracts.filter(isPrimary), secondary: contracts.filter((c) => !isPrimary(c)) };
}

export function contractSummary(v: HubApiVersion): string {
  const { primary, secondary } = splitContracts(v.contracts);
  return [...new Set([...primary, ...secondary].map((c) => LABELS[c.type] ?? c.type))].join(' · ');
}

function dateOf(value: string): string {
  const d = new Date(value.replace(' ', 'T'));
  return Number.isNaN(d.getTime())
    ? ''
    : `updated ${d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}`;
}

let cache: { at: number; endpoint: string; packages: HubPackage[] } | undefined;

/** Reads the catalogue straight from the Hub, like the v1 SPA: packages, then the latest version of each API. */
export async function loadCatalogue(endpoint: string): Promise<HubPackage[]> {
  if (cache && cache.endpoint === endpoint && Date.now() - cache.at < CACHE_MS) return cache.packages;
  const raw = await hubGet<RawPackage[]>(`${endpoint}/mocks`);
  const packages = await Promise.all(
    raw.map(async (p): Promise<HubPackage> => {
      const latest = await hubGet<HubApiVersion[]>(`${endpoint}/mocks/${encodeURIComponent(p.name)}/apis`);
      const byId = new Map(latest.map((v) => [v.id, v]));
      return {
        id: p.name,
        name: p.displayName,
        category: p.categories?.[0] ?? 'Other',
        description: p.description,
        longDescription: p.longDescription ?? '',
        updated: dateOf(p.updatedAt),
        maturity: p.maturity ?? '',
        apis: p.apis.map((a) => {
          const v = byId.get(a.name);
          return {
            id: a.name,
            name: v?.displayName ?? a.name,
            protocol: v ? protocolOf(v) : 'REST',
            versions: a.versions,
            currentVersion: a.currentVersion,
            details: v ? { [v.version]: v } : {}
          };
        })
      };
    })
  );
  cache = { at: Date.now(), endpoint, packages };
  return packages;
}

/** Fetches the contracts of a given version of an API (earlier versions are not in the latest list). */
export async function loadApiVersion(endpoint: string, pkg: string, versionName: string): Promise<HubApiVersion> {
  return hubGet<HubApiVersion>(`${endpoint}/mocks/${encodeURIComponent(pkg)}/apis/${encodeURIComponent(versionName)}`);
}

const normalise = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, '');

/** A version is in the catalogue when a Source points at one of its contracts or a Service has its name and version. */
export function isInCatalogue(
  v: HubApiVersion,
  sources: { url: string }[],
  services: { name: string; version: string }[]
): boolean {
  const urls = new Set(sources.map((s) => s.url).filter(Boolean));
  if (v.contracts.some((c) => urls.has(c.url))) return true;
  const names = [normalise(v.displayName), normalise(v.id)];
  return services.some((s) => names.includes(normalise(s.name)) && (s.version === v.version || s.version.startsWith(v.version)));
}

export interface InstallPreview {
  tag: 'NEW' | 'UPDATE' | 'MERGE' | 'SKIPPED' | 'IN CATALOGUE';
  lines: { sign: '+' | '~' | '→' | '·' | '↑' | '✓'; text: string; sub: string }[];
  installLabel: string;
  /** Why the install button is disabled. */
  blocked?: string;
}

/** What installing one version of an API does, from the install mode, the artifact role and the current catalogue. */
export function previewInstall(
  v: HubApiVersion,
  exists: boolean,
  mode: InstallMode,
  role: ArtifactRole,
  status: InstallStatus,
  mockBase = ''
): InstallPreview {
  const { primary, secondary } = splitContracts(v.contracts);
  const used = role === 'main' ? [...primary, ...secondary] : secondary;
  const count = (n: number, w: string) => `${n} ${w}${n === 1 ? '' : 's'}`;
  const label = `${v.displayName} ${v.version}`;
  const lines: InstallPreview['lines'] = [];

  if (status === 'installed') {
    lines.push({ sign: '✓', text: 'Available in APIs & Services', sub: label });
    lines.push(
      mode === 'sync'
        ? { sign: '✓', text: `${count(used.length, 'importer')} kept in sync in Sources`, sub: 'Rescanned with the global schedule' }
        : { sign: '✓', text: `${count(used.length, 'artifact')} imported once`, sub: 'Hub updates are not applied automatically' }
    );
    return { tag: 'IN CATALOGUE', lines, installLabel: 'Install' };
  }

  let blocked: string | undefined;
  let installLabel = mode === 'sync' ? 'Install and keep in sync' : 'Install once';
  if (role === 'main') {
    lines.push({
      sign: exists ? '~' : '+',
      text: exists ? 'Existing Service updated' : 'Service with its operations and examples',
      sub: label
    });
    lines.push(
      mode === 'sync'
        ? { sign: '+', text: `${count(used.length, 'importer')} in Sources, one per artifact`, sub: secondary.length ? 'Primary: contract · Secondary: examples' : 'Primary: contract' }
        : { sign: '↑', text: `${count(used.length, 'artifact')} imported once, no importer created`, sub: '' }
    );
    const protocol = protocolOf(v);
    if (protocol === 'REST' || protocol === 'GRAPHQL') {
      lines.push({
        sign: '→',
        text: 'Mocks served right after import',
        sub: `${mockBase}${protocol === 'REST' ? '/rest/' : '/graphql/'}${encodeURIComponent(v.displayName).replace(/%20/g, '+')}/${v.version}`
      });
    }
  } else {
    installLabel = 'Merge examples';
    if (exists) lines.push({ sign: '~', text: 'Examples merged into the existing Service', sub: label });
    else {
      lines.push({ sign: '·', text: 'Skipped: no matching Service here', sub: label });
      blocked = 'This API is not in your catalogue yet, so a secondary import would create nothing. Choose Main artifact to create the Service.';
    }
    if (exists && !secondary.length) blocked = 'This version has no secondary artifact to merge.';
  }
  const tag: InstallPreview['tag'] = role === 'secondary' ? (exists ? 'MERGE' : 'SKIPPED') : exists ? 'UPDATE' : 'NEW';
  return { tag, lines, installLabel, blocked };
}
