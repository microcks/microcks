<script lang="ts">
  import { ApiBadge, Button, Callout, Choice, Icon, LiveIndicator } from '@microcks/deep-ink';
  import { pluralize } from '#lib/format.js';
  import {
    contractSummary,
    isInCatalogue,
    loadApiVersion,
    previewInstall,
    type ArtifactRole,
    type HubApi,
    type HubApiVersion,
    type HubPackage,
    type InstallMode,
    type InstallStatus
  } from '#lib/hub.js';
  import type { Service, Source } from '#lib/types.js';

  let {
    pkg,
    endpoint,
    sources,
    services,
    onInstalled
  }: {
    pkg: HubPackage;
    endpoint: string;
    sources: Source[];
    services: Service[];
    onInstalled: () => void | Promise<void>;
  } = $props();

  const MODES: { value: InstallMode; label: string; description: string }[] = [
    { value: 'sync', label: 'Keep in sync', description: 'Creates one importer per artifact in Sources, rescanned with the global schedule.' },
    { value: 'one-shot', label: 'Import once', description: 'The backend downloads the artifacts once. Later Hub updates are not applied.' }
  ];
  const ROLES: { value: ArtifactRole; label: string; description: string }[] = [
    { value: 'main', label: 'Main artifact', description: 'Creates the Service, its operations and examples from the contracts.' },
    { value: 'secondary', label: 'Secondary artifact', description: 'Only merges examples into a Service that already exists here.' }
  ];

  let mode = $state<InstallMode>('sync');
  let role = $state<ArtifactRole>('main');
  /** Version chosen per API id; the current version by default. */
  let picked = $state<Record<string, string>>({});
  /** Versions fetched on demand, keyed `apiId@version`. */
  let fetched = $state<Record<string, HubApiVersion>>({});
  let failed = $state<Record<string, string>>({});
  let status = $state<Record<string, InstallStatus>>({});
  let errors = $state<Record<string, string>>({});
  let how = $state<Record<string, InstallMode>>({});

  const versionOf = (a: HubApi) => picked[a.id] ?? a.currentVersion;
  const keyOf = (a: HubApi, version = versionOf(a)) => `${a.id}@${version}`;
  const detailOf = (a: HubApi, version = versionOf(a)): HubApiVersion | undefined => a.details[version] ?? fetched[keyOf(a, version)];

  async function pick(a: HubApi, version: string) {
    picked = { ...picked, [a.id]: version };
    if (detailOf(a, version) || failed[keyOf(a, version)]) return;
    const name = a.versions.find((v) => v.version === version)?.name ?? `${a.id}.${version}`;
    try {
      fetched = { ...fetched, [keyOf(a, version)]: await loadApiVersion(endpoint, pkg.id, name) };
    } catch (e) {
      failed = { ...failed, [keyOf(a, version)]: e instanceof Error ? e.message : 'The version could not be loaded.' };
    }
  }

  async function install(a: HubApi) {
    const v = detailOf(a);
    if (!v) return;
    const key = keyOf(a);
    errors = { ...errors, [key]: '' };
    status = { ...status, [key]: 'installing' };
    how = { ...how, [key]: mode };
    try {
      const res = await fetch('/api/hub/install', {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ label: `${v.displayName} ${v.version}`, mode, role, contracts: v.contracts })
      });
      if (!res.ok) throw new Error(((await res.json().catch(() => ({}))) as { message?: string }).message ?? 'The install failed.');
      status = { ...status, [key]: 'installed' };
      await onInstalled();
    } catch (e) {
      status = { ...status, [key]: '' };
      errors = { ...errors, [key]: e instanceof Error ? e.message : 'The install failed.' };
    }
  }

  const signTone = (s: string) => (s === '✓' ? 'success' : s === '~' ? 'warning' : s === '+' || s === '→' || s === '↑' ? 'link' : undefined);
  const meta = $derived([pluralize(pkg.apis.length, 'API'), pkg.maturity, pkg.updated].filter(Boolean).join(' · '));
</script>

<section class="panel" data-pad="lg" aria-label="Package detail">
<div class="panel-body hb-detail">
  <div class="hb-detail-head">
    <div class="hb-detail-title">
      <h2>{pkg.name}</h2>
      <span class="tile-tag">{pkg.category}</span>
    </div>
    <p class="hb-detail-desc">{pkg.description}</p>
    <span class="hb-detail-meta">maintained by community · {meta} · <span class="hb-mono">hub.microcks.io/#/package/{pkg.id}</span></span>
  </div>

  <div class="hb-options">
    <fieldset class="fieldset hb-fieldset" data-variant="card">
      <legend class="section-label" data-size="sm">How to install</legend>
      {#each MODES as m (m.value)}
        <Choice kind="radio" variant="card" name="hub-mode" value={m.value} bind:group={mode}>
          {#snippet label()}{m.label}{/snippet}
          {#snippet description()}{m.description}{/snippet}
        </Choice>
      {/each}
    </fieldset>
    <fieldset class="fieldset hb-fieldset" data-variant="card">
      <legend class="section-label" data-size="sm">Artifact role</legend>
      {#each ROLES as r (r.value)}
        <Choice kind="radio" variant="card" name="hub-role" value={r.value} bind:group={role}>
          {#snippet label()}{r.label}{/snippet}
          {#snippet description()}{r.description}{/snippet}
        </Choice>
      {/each}
    </fieldset>
  </div>

  <div class="hb-block">
    <h3 class="section-label" data-size="sm">{pluralize(pkg.apis.length, 'API in this package', 'APIs in this package')}</h3>
    <p class="hb-hint">Each API version is installed on its own, with the options above.</p>
    {#each pkg.apis as a (a.id)}
      {@const v = detailOf(a)}
      {@const key = keyOf(a)}
      {@const st = status[key] ?? ''}
      {@const exists = v ? isInCatalogue(v, sources, services) : false}
      {@const effective = (st || (exists ? 'installed' : '')) as InstallStatus}
      {@const preview = v ? previewInstall(v, exists && st === '', st ? (how[key] ?? mode) : mode, role, effective) : undefined}
      <div class="hb-api" aria-busy={st === 'installing'}>
        <div class="hb-api-head">
          <ApiBadge size="compact" protocol={a.protocol} name={a.name} />
          <div role="group" aria-label={`Version of ${a.name}`} class="hb-versions">
            {#each a.versions as x (x.version)}
              <button type="button" aria-pressed={x.version === versionOf(a)} onclick={() => pick(a, x.version)}>{x.version}</button>
            {/each}
          </div>
        </div>
        {#if failed[key]}
          <Callout tone="danger" variant="outline"><p>{failed[key]}</p></Callout>
        {:else if !v}
          <span class="hb-hint">Loading this version…</span>
        {:else if preview}
          <div class="hb-api-facts">
            {#if v.capabilityLevel}<span>{v.capabilityLevel}</span>{/if}
            <span class="hb-mono">{contractSummary(v)}</span>
            {#if v.version !== a.currentVersion}<span>earlier version</span>{/if}
          </div>
          <div class="hb-preview-row">
            <span class="hb-tag" data-tag={preview.tag.toLowerCase().replace(' ', '-')}>{preview.tag}</span>
            <ApiBadge size="compact" protocol={a.protocol} name={v.displayName} version={v.version} />
          </div>
          <ul class="hb-created">
            {#each preview.lines as l, i (i)}
              <li>
                <span class="hb-sign" data-tone={signTone(l.sign)} aria-hidden="true">{l.sign}</span>
                <span class="hb-created-text">
                  <span>{l.text}</span>
                  {#if l.sub}<span class="hb-created-sub">{l.sub}</span>{/if}
                </span>
              </li>
            {/each}
          </ul>
          {#if preview.blocked && effective === ''}
            <Callout tone="warning" variant="outline"><p>{preview.blocked}</p></Callout>
          {/if}
          {#if errors[key]}
            <Callout tone="danger" title="Install failed" live="assertive"><p>{errors[key]}</p></Callout>
          {/if}
          <div class="hb-actions">
            {#if effective === ''}
              <Button variant="primary" icon="download" id={`hb-install-${a.id}`} disabled={Boolean(preview.blocked)} onclick={() => install(a)}>
                {preview.installLabel}
              </Button>
            {:else if effective === 'installing'}
              <LiveIndicator variant="box" label="live · fetching and importing artifacts" />
            {:else}
              <span class="hb-installed"><Icon name="check" size={15} />In your catalogue</span>
              <Button href="/services">View in catalogue<Icon name="arrow-right" size={14} /></Button>
              {#if (how[key] ?? mode) === 'sync' && st === 'installed'}<Button href="/sources">See importers in Sources</Button>{/if}
            {/if}
          </div>
        {/if}
      </div>
    {/each}
  </div>
</div>
</section>
