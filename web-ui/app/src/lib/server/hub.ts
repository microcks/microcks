import { BackendError, downloadArtifact, fetchBackendFeatures } from './backend.js';
import { fetchArtifact, importViaBackend } from './store.js';
import { splitContracts, type HubContract, type InstallMode, type ArtifactRole } from '#lib/hub.js';

const DEFAULT_ENDPOINT = 'https://hub.microcks.io/api';

/** The Hub endpoint comes from the backend `microcks-hub` feature, like in v1. */
export async function hubEndpoint(token?: string): Promise<string> {
  try {
    const endpoint = (await fetchBackendFeatures(token))['microcks-hub']?.endpoint;
    if (endpoint) return endpoint.replace(/\/$/, '');
  } catch {
    // Backend unreachable: the public Hub is still usable.
  }
  return DEFAULT_ENDPOINT;
}

export interface HubInstall {
  /** Display name of the API version, e.g. "Train Travel API 1.0". */
  label: string;
  mode: InstallMode;
  role: ArtifactRole;
  contracts: HubContract[];
}

const fileOf = (url: string) => decodeURIComponent(url.split('?')[0].split('/').pop() ?? 'artifact');

/**
 * Installs one version of an API. "Keep in sync" records one importer (an ImportJob) per contract; "Import once"
 * asks the backend to download each contract without keeping a Source. Main installs the primary contracts then
 * the secondary ones; secondary installs only the secondary ones.
 */
export async function installApiVersion(opts: HubInstall, token?: string): Promise<number> {
  const { primary, secondary } = splitContracts(opts.contracts);
  const steps = [...(opts.role === 'main' ? primary.map((c) => ({ c, main: true })) : []), ...secondary.map((c) => ({ c, main: false }))];
  if (!steps.length) throw new BackendError('This version has nothing to install for the chosen artifact role.', 422);
  try {
    for (const { c, main } of steps) {
      if (opts.mode === 'one-shot') {
        await downloadArtifact(c.url, main, undefined, token);
      } else {
        const { fileName, content } = await fetchArtifact(c.url);
        await importViaBackend(
          { origin: 'url', fileName, name: `${opts.label} · ${fileOf(c.url)}`, content, url: c.url, role: main ? 'PRIMARY' : 'SECONDARY' },
          token
        );
      }
    }
  } catch (e) {
    if (e instanceof BackendError) throw e;
    throw new BackendError(`${opts.label}: ${e instanceof Error ? e.message : String(e)}`, 502);
  }
  return steps.length;
}
