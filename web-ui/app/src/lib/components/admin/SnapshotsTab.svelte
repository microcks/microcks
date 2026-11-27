<script lang="ts">
  import { tick } from 'svelte';
  import { invalidate } from '$app/navigation';
  import { ApiBadge, Button, Callout, DropZone, Icon } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';
  import { plural, type SnapshotPreviewItem, type SnapshotServiceRef } from '#lib/admin.js';

  interface Props {
    services: SnapshotServiceRef[];
    notify: (message: string) => void;
  }
  let { services, notify }: Props = $props();

  // ─── Export ───
  let selected = $state<string[]>([]);
  let initialised = false;
  $effect(() => {
    // Preselect the two most recent Services, as the prototype does.
    if (!initialised && services.length > 0) {
      initialised = true;
      selected = services.slice(0, 2).map((s) => s.id);
    }
  });
  let exported = $state<{ fileName: string; services: number; operations: number; sizeKb: number }>();
  let exportError = $state('');

  const choose = (ids: string[]) => {
    selected = ids;
    exported = undefined;
  };

  async function doExport() {
    exportError = '';
    try {
      const snap = await api<{ services: { operations: unknown[] }[] }>('/api/admin/snapshot/export', { method: 'POST', json: { serviceIds: selected } });
      const text = JSON.stringify(snap, null, 2);
      const blob = new Blob([text], { type: 'application/json' });
      const fileName = `microcks-repository-${new Date().toISOString().slice(0, 10)}.json`;
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = fileName;
      a.click();
      URL.revokeObjectURL(a.href);
      exported = {
        fileName,
        services: snap.services.length,
        operations: snap.services.reduce((n, s) => n + s.operations.length, 0),
        sizeKb: Math.max(1, Math.round(blob.size / 1024))
      };
    } catch (e) {
      exportError = (e as Error).message;
    }
  }

  // ─── Import ───
  let section: HTMLElement | undefined = $state();
  const inputId = $props.id();
  let fileName = $state('');
  let content = $state('');
  let preview = $state<SnapshotPreviewItem[] | null>(null);
  let result = $state<{ created: number; replaced: number } | null>(null);
  let importError = $state('');

  async function pick(name: string, text: string) {
    fileName = name;
    content = text;
    result = null;
    preview = null;
    importError = '';
    await tick();
    document.getElementById(`${inputId}-change`)?.focus();
    try {
      preview = await api<SnapshotPreviewItem[]>('/api/admin/snapshot/preview', { method: 'POST', json: { content: text } });
    } catch (e) {
      importError = (e as Error).message;
    }
  }

  async function onFiles(files: File[]) {
    const f = files[0];
    if (f) await pick(f.name, await f.text());
  }

  /** A snapshot of the current repository, so the import flow can be tried without a file. */
  async function useSample() {
    const ids = services.slice(0, 2).map((s) => s.id);
    if (ids.length === 0) {
      importError = 'There is no Service to build a sample from.';
      return;
    }
    const snap = await api('/api/admin/snapshot/export', { method: 'POST', json: { serviceIds: ids } });
    await pick('microcks-repository-sample.json', JSON.stringify(snap));
  }

  async function clear() {
    fileName = '';
    content = '';
    preview = null;
    result = null;
    importError = '';
    await tick();
    section?.querySelector<HTMLElement>('.drop-zone-input')?.focus();
  }

  async function doImport() {
    importError = '';
    try {
      result = await api<{ created: number; replaced: number }>('/api/admin/snapshot/import', { method: 'POST', json: { content } });
    } catch (e) {
      importError = (e as Error).message;
      return;
    }
    notify(`Snapshot ${fileName} imported: ${plural(result.created, 'Service')} created, ${result.replaced} replaced.`);
    await invalidate('app:sources');
    await tick();
    document.getElementById(`${inputId}-open`)?.focus();
  }
</script>

<div class="ad-tab">
  <p class="ad-intro">A Snapshot holds Services &amp; APIs definitions: operations, labels, examples and resources. Sources, test runs and usage metrics are not included.</p>
  <div class="split-pane" style="--split-main: 520px; --split-side: 380px; --split-side-max: none">
    <section class="split-pane-main ad-snap-card" aria-labelledby="ad-export-title">
      <div class="ad-snap-head">
        <h2 id="ad-export-title">Export</h2>
        <div class="ad-snap-select">
          <span class="ad-meta">{selected.length} of {services.length} selected</span>
          <button type="button" class="ad-link-button" onclick={() => choose(services.map((s) => s.id))}>Select all</button>
          <button type="button" class="ad-link-button" onclick={() => choose([])}>None</button>
        </div>
      </div>
      {#if services.length === 0}
        <p class="ad-note">No Service to export yet. Import a contract first.</p>
      {:else}
        <fieldset class="ad-snap-grid">
          <legend class="sr-only">Services to export</legend>
          {#each services as s (s.id)}
            {@const on = selected.includes(s.id)}
            <label class="ad-snap-service" data-selected={on ? '' : undefined}>
              <input type="checkbox" checked={on} onchange={(e) => choose(e.currentTarget.checked ? [...selected, s.id] : selected.filter((x) => x !== s.id))} />
              <ApiBadge protocol={s.protocol} name={`${s.name} ${s.version}`} size="sm" class="ad-snap-badge" />
              <span class="ad-snap-ops">{plural(s.operationCount, 'op')}</span>
            </label>
          {/each}
        </fieldset>
      {/if}
      <div class="ad-snap-actions">
        <Button variant="primary" icon="download" disabled={selected.length === 0} onclick={doExport}>Export snapshot</Button>
        <span role="status" class="ad-snap-status">
          {#if exported}
            <Icon name="check" size={14} class="ad-ok" />
            <span class="ad-snap-file">{exported.fileName}</span>
            <span>downloaded · {plural(exported.services, 'service')}, {plural(exported.operations, 'operation')} · {exported.sizeKb} KB</span>
          {/if}
        </span>
      </div>
      {#if exportError}<Callout tone="danger" title="Export failed" live="assertive">{exportError}</Callout>{/if}
    </section>

    <div class="split-pane-side">
      <section bind:this={section} class="ad-snap-card" aria-labelledby="ad-import-title">
        <h2 id="ad-import-title">Import</h2>
        {#if !fileName}
          {#snippet hint()}A <code class="ad-mono">microcks-repository.json</code> exported from a Microcks v2 instance{/snippet}
          <DropZone size="sm" heading="Drop a snapshot here" {hint} accept="application/json,.json" multiple={false} chooseLabel="Choose a file" {onFiles}>
            <button type="button" class="ad-link-button ad-drop-sample" onclick={useSample}>Use a sample snapshot</button>
          </DropZone>
        {:else}
          <div class="ad-snap-preview">
            <div class="ad-snap-filerow">
              <Icon name="file-plain" size={15} />
              <span class="ad-snap-filename">{fileName}</span>
              {#if !result}<button type="button" id="{inputId}-change" class="ad-link-button" aria-label={`Change file ${fileName}`} onclick={clear}>Change</button>{/if}
            </div>
            {#if preview}
              <h3 class="section-label" data-size="sm" data-line-height="normal">Preview · {plural(preview.length, 'service')} detected</h3>
              <ul class="ad-snap-items">
                {#each preview as p (p.name)}
                  <li>
                    <ApiBadge protocol={p.protocol} name={p.name} size="sm" />
                    <span class="ad-snap-effect" data-kind={p.kind}>{p.effect}</span>
                  </li>
                {/each}
              </ul>
            {:else if !importError}
              <p class="ad-note" role="status">Reading the snapshot…</p>
            {/if}
            {#if importError}<Callout tone="danger" title="Snapshot not usable" live="assertive">{importError}</Callout>{/if}
            {#if result}
              <div role="status" class="ad-snap-done">
                <Icon name="check" size={14} class="ad-ok" />
                <span class="ad-snap-done-text">Imported · {result.created} created, {result.replaced} replaced</span>
                <a href="/services" id="{inputId}-open" class="ad-snap-done-link">Open APIs &amp; Services</a>
                <button type="button" class="ad-link-button" onclick={clear}>Import another</button>
              </div>
            {:else}
              <div class="ad-snap-actions">
                <Button variant="primary" icon="upload" disabled={!preview} onclick={doImport}>Import snapshot</Button>
                <span class="ad-note">Services with the same name and version are replaced.</span>
              </div>
            {/if}
          </div>
        {/if}
      </section>
    </div>
  </div>
</div>
