<script lang="ts">
  import { goto, invalidate } from '$app/navigation';
  import { page } from '$app/state';
  import {
    ApiBadge,
    Button,
    CopyButton,
    FactRow,
    Facts,
    FilterRow,
    FilterSearch,
    Icon,
    LiveIndicator,
    Chip,
    Segmented,
    Switch,
    SummaryStrip,
    SummaryStripSep,
    type Fact
  } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import EditLabelsDialog from '#lib/components/catalog/EditLabelsDialog.svelte';
  import EditSourceDialog from '#lib/components/sources/EditSourceDialog.svelte';
  import { api } from '#lib/api.js';
  import { formatDay, formatHourMinute, formatRelative, formatShortClock } from '#lib/format.js';
  import {
    ROLE_WORD,
    SOURCE_ORIGINS,
    diffStats,
    filterSources,
    originCounts,
    originLabel,
    sourceStatus,
    statusCounts,
    type SourceFilter
  } from '#lib/sources.js';
  import type { Service, Source, SourceStatus } from '#lib/types.js';

  let { data } = $props();
  const sources: Source[] = $derived(data.sources);
  const schedule = $derived(data.schedule);
  const servicesById = $derived(new Map<string, Service>(data.services.map((s: Service) => [s.id, s])));

  let filter = $state<SourceFilter>({ status: 'all', origin: 'all', query: '' });
  let confirm = $state<string>();
  let undoing = $state<Source>();
  let importing = $state(new Set<string>());
  let flash = $state<string>();
  /** The source being edited, and which part of it. */
  let editing = $state<{ source: Source; what: 'details' | 'labels' }>();

  const nameOf = (id: string) => {
    const s = servicesById.get(id);
    return s ? [s.name, s.version] : [];
  };
  const counts = $derived(statusCounts(sources));
  const origins = $derived(originCounts(sources));
  const shown = $derived(filterSources(sources, filter, nameOf));
  const selected = $derived(shown.find((s) => s.id === page.params.id) ?? shown[0]);
  const minutesSince = $derived(Math.max(0, Math.round((Date.now() - new Date(schedule.lastScanAt).getTime()) / 60000)));
  const minutesUntil = $derived(Math.max(0, Math.round((new Date(schedule.nextScanAt).getTime() - Date.now()) / 60000)));

  const STATUS_WORD: Record<SourceStatus, string> = {
    imported: 'Imported',
    warning: 'Imported · 1 warning',
    failing: 'Failed',
    inactive: 'Inactive'
  };
  const STATUS_ICON = { imported: 'check', warning: 'warning', failing: 'cross', inactive: 'pause-narrow' } as const;
  const SHIELD = {
    imported: { word: 'imported', status: 'success' },
    warning: { word: '1 warning', status: 'warning' },
    failing: { word: 'failed', status: 'danger' },
    inactive: { word: 'inactive', status: 'neutral' }
  } as const;

  const refresh = () => invalidate('app:sources');

  async function toggleActive(s: Source, active: boolean) {
    await api(`/api/sources/${s.id}`, { method: 'PATCH', json: { active } });
    flash = `${s.name} ${active ? 'will be scanned on schedule' : 'is paused'}.`;
    await refresh();
  }

  async function importNow(s: Source) {
    importing = new Set(importing).add(s.id);
    try {
      await api(`/api/sources/${s.id}/import`, { method: 'POST' });
      flash = 'Import finished.';
    } catch (e) {
      flash = e instanceof Error ? e.message : 'Import failed.';
    } finally {
      importing = new Set([...importing].filter((x) => x !== s.id));
      await refresh();
    }
  }

  async function doDelete(s: Source) {
    await api(`/api/sources/${s.id}`, { method: 'DELETE' });
    confirm = undefined;
    undoing = s;
    if (page.params.id === s.id) await goto('/sources', { replaceState: true });
    flash = `${s.name} deleted. Undo is available.`;
    await refresh();
    queueMicrotask(() => document.getElementById('source-undo')?.focus());
  }

  async function undo() {
    if (!undoing) return;
    const back = undoing;
    await api(`/api/sources/${back.id}/undo`, { method: 'POST' });
    undoing = undefined;
    flash = `${back.name} restored.`;
    await refresh();
    queueMicrotask(() => document.getElementById(`source-name-${back.id}`)?.focus());
  }

  function facts(s: Source): Fact[] {
    const up = s.origin === 'upload';
    return [
      { term: 'Origin', value: SOURCE_ORIGINS.find((o) => o.id === s.origin)?.fact ?? s.origin, sub: up ? 'Re-upload a new version to update its Services' : undefined },
      {
        term: 'Artifact',
        value: `${ROLE_WORD[s.role]} · ${s.artifact}`,
        mono: true,
        sub: s.role === 'PRIMARY' ? 'Creates or updates Services from this contract' : 'Merges examples into existing Services only'
      },
      { term: 'Secret', value: up ? 'None, uploaded file' : (s.secret ?? 'None, public resource'), mono: Boolean(s.secret) },
      { term: 'TLS validation', value: up ? 'Not applicable' : s.tlsValidation ? 'Enabled' : 'Disabled for this source' },
      up
        ? { term: 'Schedule', value: 'None · one-off upload' }
        : s.active
          ? { term: 'Schedule', value: `Global hourly scan · next ${formatHourMinute(schedule.nextScanAt)}`, sub: 'Also on demand with Import now' }
          : { term: 'Schedule', value: 'Paused, not scanned', sub: 'Turn the switch on to resume' },
      { term: 'Created', value: formatDay(s.createdAt) }
    ];
  }

  const resultOf = (k: string) => (k === 'failure' ? 'failed' : 'imported');
  const RESULT_ICON = { imported: 'check', failed: 'cross' } as const;
  const RESULT_WORD = { imported: 'Imported', failed: 'Failed' } as const;
  const TRIGGER = { import: 'Initial import', reimport: 'Import now', failure: 'Import' } as const;
  const when = (at: string) =>
    Date.now() - new Date(at).getTime() > 7 * 86_400_000 ? formatShortClock(at).split(',')[0] : formatRelative(at);
  const DIFF_SIGN = { add: '+', del: '−', ctx: '' } as const;
  const DIFF_WORD = { add: 'added', del: 'removed' } as const;
  const activeRevision = $derived(selected?.timeline.find((e) => e.revision)?.revision);
</script>

{#snippet serviceLinks(s: Source, cls: string)}
  <span class={cls}>
    {#each s.serviceIds as id (id)}
      {@const svc = servicesById.get(id)}
      {#if svc}
        <a href={`/services?q=${encodeURIComponent(svc.name)}`}><ApiBadge protocol={svc.protocol} name={svc.name} version={svc.version} size="sm" /></a>
      {/if}
    {/each}
  </span>
{/snippet}

<div class="sources-page">
  <div class="sources-head">
    <PageHeader
      title="Sources"
      description="Everything Microcks imports is a source: files you upload once and URLs or repositories it keeps in sync."
      descriptionTone="secondary"
      descriptionWidth={760}
    >
      {#snippet actions()}<Button variant="primary" icon="plus" href="/sources/new">Add source</Button>{/snippet}
    </PageHeader>

    <SummaryStrip variant="canvas" class="scan-strip" role="group" aria-label="Scan schedule">
      <span class="scan-strip-group">
        <Icon name="clock" size={15} />
        <span class="muted">Scan schedule</span>
        <span class="scan-strip-cron">{schedule.cron}</span>
        <span class="secondary">{schedule.description}</span>
        <span class="scan-strip-env">{schedule.envVar}</span>
      </span>
      <SummaryStripSep />
      <span class="scan-strip-pair"><span class="muted">Last scan</span><span class="mono">{formatHourMinute(schedule.lastScanAt)}</span><span class="muted">{minutesSince} min ago</span></span>
      <span class="scan-strip-pair"><span class="muted">Next scan</span><span class="mono">{formatHourMinute(schedule.nextScanAt)}</span><span class="muted">in {minutesUntil} min</span></span>
      <span class="scan-strip-counts">
        <ApiBadge label="sources" value={String(counts.all)} />
        {#if counts.failing}<ApiBadge label="failing" value={String(counts.failing)} status="danger" />{/if}
      </span>
    </SummaryStrip>
  </div>

  <section aria-label="Filter sources">
    <FilterRow>
      <FilterSearch label="Filter sources by name, URL, secret or Service" placeholder="Filter by name, URL, secret, Service…" autocomplete="off" bind:value={filter.query} />
      <Segmented
        label="Status"
        pressedFill="selected"
        iconSize={13}
        value={filter.status}
        onchange={(id) => (filter.status = id as SourceFilter['status'])}
        options={[
          { id: 'all', label: 'All', count: counts.all },
          { id: 'imported', label: 'Imported', icon: 'check', iconTone: 'success', count: counts.imported },
          { id: 'warning', label: 'Warnings', icon: 'warning', iconTone: 'warning', count: counts.warning },
          { id: 'failing', label: 'Failing', icon: 'cross', iconTone: 'danger', count: counts.failing },
          { id: 'inactive', label: 'Inactive', icon: 'pause-narrow', iconTone: 'muted', count: counts.inactive }
        ]}
      />
      <Segmented
        label="Origin"
        pressedFill="selected"
        value={filter.origin}
        onchange={(id) => (filter.origin = id as SourceFilter['origin'])}
        options={[{ id: 'all', label: 'All', count: origins.all }, ...SOURCE_ORIGINS.map((o) => ({ id: o.id, label: o.label, count: origins[o.id] }))]}
      />
      <span role="status" class="filter-bar-end sources-shown">{shown.length} of {counts.all} sources</span>
    </FilterRow>
  </section>

  <p class="sr-only" role="status" aria-live="polite">{flash ?? ''}</p>

  <div class="sources-split">
    <section aria-label="Sources" class="sources-list">
      {#if undoing}
        <div role="status" class="undo-banner">
          <span class="undo-banner-text"><strong>{undoing.name}</strong> deleted. Services already discovered remain but are no longer updated.</span>
          <button type="button" id="source-undo" class="undo-banner-action" onclick={undo}>Undo</button>
        </div>
      {/if}
      {#each shown as j (j.id)}
        {@const st = sourceStatus(j)}
        {@const running = importing.has(j.id)}
        {@const upload = j.origin === 'upload'}
        {@const isSel = selected?.id === j.id}
        <article aria-label={j.name} class="source-card" data-selected={isSel ? '' : undefined}>
          <div class="source-card-head">
            {#if upload}
              <span class="source-card-upload" aria-hidden="true" title="Uploaded file, no schedule"><Icon name="upload" size={16} /></span>
            {:else}
              <Switch checked={j.active} onchange={(v) => toggleActive(j, v)}>
                {#snippet label()}<span class="sr-only">Scan {j.name} on schedule</span>{/snippet}
              </Switch>
            {/if}
            <button type="button" id={`source-name-${j.id}`} class="source-card-name" aria-pressed={isSel} onclick={() => goto(`/sources/${j.id}`)}>{j.name}</button>
            {#if running}
              <LiveIndicator label="live · importing" variant="inline" />
            {:else}
              <span class="source-status" data-status={st === 'failing' ? 'failed' : st}><Icon name={STATUS_ICON[st]} size={14} />{STATUS_WORD[st]}</span>
            {/if}
            <span class="source-card-last">
              {#if running}started just now{:else if j.lastImportAt}{upload ? 'uploaded' : 'last import'} {st === 'inactive' ? when(j.lastImportAt) : formatRelative(j.lastImportAt)}{/if}
            </span>
          </div>
          <div class="source-card-url">{j.url || j.name}</div>
          <div class="source-card-meta">
            <span class="source-role" data-role={j.role}>{j.role}</span>
            <span class="source-origin">{originLabel(j.origin)}</span>
            <span class="mono source-kind">{j.artifact}</span>
            {#if j.secret}<span class="source-secret"><Icon name="lock" size={13} /><span class="sr-only">Secret </span><span class="mono">{j.secret}</span></span>{/if}
            <span>{upload ? 'no schedule · one-off' : j.active ? `next scan ${formatHourMinute(schedule.nextScanAt)}` : 'scans paused'}</span>
            {@render serviceLinks(j, 'source-card-services')}
          </div>
          {#if j.error}
            <div role="alert" class="source-notice" data-tone="danger"><Icon name="cross" size={14} /><span class="mono source-notice-strong">{j.error}</span></div>
          {/if}
          {#if j.warning}
            <div class="source-notice" data-tone="warning"><Icon name="warning" size={14} /><span>{j.warning}</span></div>
          {/if}
          {#if confirm === j.id}
            <div
              role="alertdialog"
              tabindex="-1"
              aria-label={`Confirm deletion of ${j.name}`}
              class="delete-confirm"
              onkeydown={(e) => {
                if (e.key === 'Escape') {
                  confirm = undefined;
                  queueMicrotask(() => document.getElementById(`source-delete-${j.id}`)?.focus());
                }
              }}
            >
              <span class="delete-confirm-text">Delete this source? Services already discovered remain but will no longer be updated.</span>
              <!-- svelte-ignore a11y_autofocus -->
              <Button size="sm" variant="danger" confirm autofocus onclick={() => doDelete(j)}>Delete source</Button>
              <Button
                size="sm"
                variant="secondary"
                class="delete-confirm-cancel"
                onclick={() => {
                  confirm = undefined;
                  queueMicrotask(() => document.getElementById(`source-delete-${j.id}`)?.focus());
                }}>Cancel</Button
              >
            </div>
          {/if}
          <div class="source-card-actions">
            {#if upload}
              <Button size="sm" variant="secondary" icon="upload" href="/sources/new" aria-label={`Re-upload ${j.name}`}>Re-upload</Button>
            {:else}
              <Button size="sm" variant="secondary" icon="refresh" class="source-import" disabled={running || !j.active} title={!j.active ? 'Activate the source to import it' : undefined} onclick={() => importNow(j)}>{running ? 'Importing…' : 'Import now'}</Button>
            {/if}
            {#if !upload}
              <Button size="sm" variant="secondary" icon="edit" id={`source-edit-${j.id}`} aria-label={`Edit ${j.name}`} onclick={() => (editing = { source: j, what: 'details' })}>Edit</Button>
            {/if}
            <Button size="sm" variant="ghost" icon="trash-plain" id={`source-delete-${j.id}`} class="source-delete" aria-label={`Delete ${j.name}`} onclick={() => (confirm = j.id)}>Delete</Button>
          </div>
        </article>
      {/each}
      {#if shown.length === 0}
        <div class="sources-empty">
          No source matches these filters.
          <button type="button" class="sources-empty-action" onclick={() => (filter = { status: 'all', origin: 'all', query: '' })}>Clear filters</button>
        </div>
      {/if}
    </section>

    {#if selected}
      {@const selStatus = sourceStatus(selected)}
      {@const stats = selected.diff ? diffStats(selected.diff.lines) : undefined}
      <section aria-label="Source detail" class="sources-detail source-detail">
        <div class="source-detail-head">
          <div class="source-detail-title">
            <h2>{selected.name}</h2>
            <ApiBadge label="last import" value={SHIELD[selStatus].word} status={SHIELD[selStatus].status} />
          </div>
          <div class="source-detail-url">
            <span class="source-detail-url-text">{selected.url || selected.name}</span>
            {#if selected.url}<CopyButton value={selected.url} label="Copy URL" size="sm" />{/if}
          </div>
        </div>

        {#if selected.error}
          <div role="note" class="source-failure">
            <span class="source-failure-title"><Icon name="cross" size={14} />{selected.error}</span>
            <span>Mocks keep serving the last successfully imported version.</span>
          </div>
        {/if}

        <Facts class="source-facts" items={facts(selected)}>
          <FactRow term="Labels">
            <span class="source-labels">
              {#each Object.entries(selected.labels) as [k, v] (k)}<Chip variant="outline">{k}={v}</Chip>{:else}<span class="muted">None</span>{/each}
              {#if selected.origin !== 'upload'}
                <Button size="sm" variant="link" icon="edit" aria-label={`Edit labels of ${selected.name}`} onclick={() => (editing = { source: selected, what: 'labels' })}>Edit labels</Button>
              {/if}
            </span>
          </FactRow>
          <FactRow term="Services produced">{@render serviceLinks(selected, 'source-detail-services')}</FactRow>
        </Facts>

        <div class="source-detail-block">
          <div class="source-detail-block-head">
            <h3>Sync timeline</h3>
            <span class="muted source-detail-help">Each import, newest first.</span>
          </div>
          <ol class="sync-timeline" aria-label={`Imports of ${selected.name}`}>
            {#each selected.timeline as e (e.at + e.kind)}
              {@const res = resultOf(e.kind)}
              <li data-result={res} data-filled={e.revision ? '' : undefined}>
                <div class="sync-entry">
                  <span class="sync-entry-line">
                    <span class="mono sync-entry-clock">{formatShortClock(e.at)}</span>
                    <span class="muted sync-entry-when">{when(e.at)}</span>
                    <span class="secondary">{TRIGGER[e.kind]}</span>
                    <span class="sync-entry-result" data-result={res}><Icon name={RESULT_ICON[res]} size={12} />{RESULT_WORD[res]}</span>
                  </span>
                  <span class="sync-entry-change">
                    <span class="sync-entry-plain" data-result={res}>{e.summary}</span>
                    {#if e.revision}<span class="muted sync-entry-rev">{e.revision}</span>{/if}
                  </span>
                </div>
              </li>
            {/each}
          </ol>
        </div>

        <div class="source-detail-block">
          <div class="source-detail-block-head">
            <h3>Contract diff</h3>
            {#if stats}
              <span class="diff-stats"><span data-kind="added">+{stats.adds}</span><span data-kind="removed">−{stats.dels}</span><span class="muted">lines</span></span>
            {/if}
          </div>
          {#if selected.diff}
            <div class="diff-view">
              <div class="diff-view-head"><span class="diff-view-file">{selected.name}</span><span class="muted diff-view-rev">{activeRevision}</span></div>
              <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
              <div role="region" aria-label="Unified diff" class="diff-view-body" tabindex="0">
                {#each selected.diff.lines as l, i (i)}
                  <div class="diff-line" data-kind={l.kind === 'add' ? 'added' : l.kind === 'del' ? 'removed' : 'context'}>
                    <span class="diff-line-sign" aria-hidden="true">{DIFF_SIGN[l.kind]}</span>
                    <span class="diff-line-text">{#if l.kind !== 'ctx'}<span class="sr-only">{DIFF_WORD[l.kind]} </span>{/if}{l.text}</span>
                  </div>
                {/each}
              </div>
            </div>
          {:else}
            <div class="diff-empty">
              {selected.origin === 'upload'
                ? `Uploaded once on ${formatDay(selected.createdAt)}. Re-upload a new version of the file and the change will appear here as a diff.`
                : `The contract has not changed since its initial import (${formatDay(selected.createdAt)}). Nothing to compare yet: the next change will appear here as a diff.`}
            </div>
          {/if}
        </div>
      </section>
    {/if}
  </div>
</div>

{#if editing?.what === 'details'}
  <EditSourceDialog
    source={editing.source}
    onclose={() => {
      const id = editing?.source.id;
      editing = undefined;
      if (id) queueMicrotask(() => document.getElementById(`source-edit-${id}`)?.focus());
    }}
    onsaved={async () => {
      flash = 'Source updated.';
      await refresh();
    }}
  />
{:else if editing?.what === 'labels'}
  <EditLabelsDialog
    endpoint={`/api/sources/${encodeURIComponent(editing.source.id)}/labels`}
    resourceName={editing.source.name}
    kind="source"
    labels={editing.source.labels}
    onclose={() => (editing = undefined)}
    onsaved={async () => {
      flash = 'Labels have been updated.';
      await refresh();
    }}
  />
{/if}
