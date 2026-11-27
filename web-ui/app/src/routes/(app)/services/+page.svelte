<script lang="ts">
  import { page } from '$app/state';
  import {
    ApiBadge,
    Button,
    Chip,
    ConformanceBand,
    DataTable,
    EmptyState,
    FilterBar,
    FilterReset,
    FilterRow,
    FilterSearch,
    Icon,
    Segmented,
    StatusBadge,
    type DataTableColumn
  } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import { facets, matches, serviceLabels, type ServiceFilter } from '#lib/catalog.js';
  import { PROTOCOL_ORDER, formatRelative, operationUnit, pluralize } from '#lib/format.js';
  import type { Protocol, Service } from '#lib/types.js';

  let { data } = $props();
  const all: Service[] = $derived(data.services);

  let filter = $state<ServiceFilter>({ query: page.url.searchParams.get('q') ?? '', protocols: [], labels: [] });
  let testStatus = $state('all');
  let view = $state('table');

  // No test has run in this iteration: every service is "never tested", and nothing is invented.
  const visible = $derived(all.filter((s) => matches(s, filter)).filter(() => testStatus === 'all' || testStatus === 'never'));
  const counts = $derived(facets(all, filter));
  const operationsTotal = $derived(all.reduce((n, s) => n + s.operations.length, 0));
  const labelNames = $derived([...new Set(all.flatMap(serviceLabels))].sort());
  const activeCount = $derived(
    (filter.query.trim() ? 1 : 0) + filter.protocols.length + filter.labels.length + (testStatus !== 'all' ? 1 : 0)
  );

  function toggle(key: 'protocols' | 'labels', value: string) {
    const cur = filter[key] as string[];
    (filter[key] as string[]) = cur.includes(value) ? cur.filter((x) => x !== value) : [...cur, value];
  }
  function reset() {
    filter = { query: '', protocols: [], labels: [] };
    testStatus = 'all';
  }

  const columns: DataTableColumn<Service>[] = [
    { key: 'name', header: 'API / service' },
    { key: 'ops', header: 'Ops', align: 'end', mono: true },
    { key: 'labels', header: 'Labels' },
    { key: 'test', header: 'Last test', nowrap: true },
    { key: 'conformance', header: 'Conformance' },
    { key: 'calls', header: 'Mock 24 h', align: 'end', mono: true, nowrap: true },
    { key: 'updated', header: 'Updated', nowrap: true },
    { key: 'open', header: 'Open', headerHidden: true, align: 'end' }
  ];
  const callsText = (s: Service) => `— ${s.protocol === 'EVENT' ? 'msg' : 'req'}`;
  const bandOf = (s: Service) => s.operations.map(() => 'untested' as const);
  const activeDesc = $derived([
    ...(filter.query.trim() ? [`“${filter.query.trim()}”`] : []),
    ...filter.protocols,
    ...filter.labels,
    ...(testStatus !== 'all' ? ['Conformant / Failing'] : [])
  ]);
  const key = (l: string) => l.split('=')[0];
  const val = (l: string) => l.slice(l.indexOf('=') + 1);
</script>

{#snippet openLink(s: Service)}
  <span class="catalogue-actions">
    <a href={`/tests/create?service=${encodeURIComponent(s.id)}`} class="catalogue-open" aria-label={`Run a test on ${s.name} ${s.version}`} title="Run a test"><Icon name="play" size={14} /></a>
    <a href={`/services/${s.id}`} class="catalogue-open" aria-label={`Open ${s.name} ${s.version}`}><Icon name="chevron" size={14} /></a>
  </span>
{/snippet}

{#snippet labelChips(s: Service)}
  <span class="catalogue-labels">
    {#each Object.entries(s.labels) as [k, v] (k)}
      <Chip size="sm" labelKey={k}>{v}</Chip>
    {/each}
  </span>
{/snippet}

{#snippet cell(s: Service, c: DataTableColumn<Service>)}
  {#if c.key === 'name'}
    <span class="catalogue-service">
      <a class="catalogue-badge-link" href={`/services/${s.id}`}><ApiBadge protocol={s.protocol} name={s.name} version={s.version} /></a>
      <span class="catalogue-format">{s.format}</span>
    </span>
  {:else if c.key === 'ops'}
    {s.operations.length}
  {:else if c.key === 'labels'}
    {@render labelChips(s)}
  {:else if c.key === 'test'}
    <span class="catalogue-test">
      <StatusBadge status="never" label="Never tested" />
      <span class="catalogue-test-meta">no test run yet</span>
    </span>
  {:else if c.key === 'conformance'}
    <span class="catalogue-conformance">
      <span class="catalogue-score" data-level="neutral">—</span>
      <ConformanceBand size="mini" outcomes={bandOf(s)} unit={operationUnit(s.protocol)} />
    </span>
  {:else if c.key === 'calls'}
    {callsText(s)}
  {:else if c.key === 'updated'}
    <span class="ex-muted">{formatRelative(s.updatedAt)}</span>
  {:else}
    {@render openLink(s)}
  {/if}
{/snippet}

<div class="catalogue" data-main-pad="roomy">
  <PageHeader
    title="APIs & Services"
    size="lg"
    description="Every imported contract becomes a live mock and a test target. Filter by protocol, by label or by last test result."
  >
    {#snippet meta()}{all.length} services · {operationsTotal} operations · <a href="/workflows">used by 0 workflows</a>{/snippet}
    {#snippet actions()}<Button variant="secondary" icon="plus" href="/sources/new">Direct API</Button>{/snippet}
  </PageHeader>

  <FilterBar label="Catalogue filters">
    <FilterRow>
      <FilterSearch label="Filter by name" placeholder="Filter by name or version…" autocomplete="off" bind:value={filter.query} />
      <Segmented
        label="Last test result"
        value={testStatus}
        onchange={(id) => (testStatus = id)}
        options={[
          { id: 'all', label: 'All', dot: 'neutral', count: all.length },
          { id: 'passed', label: 'Conformant', dot: 'success', count: 0 },
          { id: 'failed', label: 'Failing', dot: 'danger', count: 0 },
          { id: 'never', label: 'Never tested', dot: 'muted', count: all.length }
        ]}
      />
      <Segmented
        class="filter-bar-end"
        label="View mode"
        value={view}
        onchange={(id) => (view = id)}
        options={[
          { id: 'table', label: 'Table', icon: 'list' },
          { id: 'wall', label: 'Badge wall', icon: 'badge-wall' }
        ]}
      />
    </FilterRow>
    <FilterRow title="Protocol">
      {#each PROTOCOL_ORDER as p (p)}
        <Chip protocol={p as Protocol} count={counts.protocols[p] ?? 0} pressed={filter.protocols.includes(p)} onclick={() => toggle('protocols', p)}>{p}</Chip>
      {/each}
    </FilterRow>
    <FilterRow title="Labels">
      {#each labelNames as l (l)}
        <Chip labelKey={key(l)} count={counts.labels[l] ?? 0} pressed={filter.labels.includes(l)} onclick={() => toggle('labels', l)}>{val(l)}</Chip>
      {/each}
      {#if activeCount > 0}<FilterReset count={activeCount} onclick={reset} />{/if}
    </FilterRow>
  </FilterBar>

  <div class="catalogue-result">
    <p role="status" aria-live="polite"><strong>{visible.length}</strong> of {all.length} APIs &amp; services</p>
    <span class="catalogue-sort">sort: recently updated</span>
  </div>

  {#if visible.length === 0}
    <EmptyState title="No API matches these filters" headingLevel={2} icon="search-x">
      <p>None of the {all.length} services matches: {activeDesc.join(' · ')}. Remove a filter or reset the selection.</p>
      {#snippet actions()}<Button variant="primary" icon="reset" onclick={reset}>Reset filters</Button>{/snippet}
    </EmptyState>
  {:else if view === 'table'}
    <DataTable class="catalogue-table" caption="APIs and services" captionHidden {columns} rows={visible} rowKey={(s) => s.id} {cell} />
  {:else}
    <div class="catalogue-wall">
      {#each visible as s (s.id)}
        <article class="catalogue-card">
          <div class="catalogue-card-head">
            <a class="catalogue-badge-link" href={`/services/${s.id}`}><ApiBadge protocol={s.protocol} name={s.name} version={s.version} size="card" /></a>
            <span class="catalogue-format">{s.format} · {pluralize(s.operations.length, operationUnit(s.protocol))}</span>
          </div>
          <div class="catalogue-shields">
            <ApiBadge label="conformance" value="untested" status="neutral" />
            <ApiBadge label="mock" value={s.mockUrl.startsWith('/') ? s.mockUrl.split('/')[1] : s.protocol} status="info" />
          </div>
          {@render labelChips(s)}
          <div class="catalogue-card-foot">
            <span>{callsText(s)} / 24 h · {formatRelative(s.updatedAt)}</span>
            <Button size="sm" variant="secondary" icon="play" href={`/tests/create?service=${encodeURIComponent(s.id)}`}>
              Run a test<span class="sr-only"> on {s.name} {s.version}</span>
            </Button>
            <Button size="sm" variant="secondary" href={`/services/${s.id}`}>
              Open service<span class="sr-only"> {s.name} {s.version}</span><Icon name="chevron" size={14} />
            </Button>
          </div>
        </article>
      {/each}
    </div>
  {/if}
  <footer class="catalogue-pager">
    <span>{visible.length} of {all.length} · page 1 of 1</span>
    <nav aria-label="Pagination" class="catalogue-pager-nav">
      <span class="catalogue-pager-size">20 per page</span>
      <Button size="sm" variant="secondary" icon="chevron-left" iconOnly aria-label="Previous page" disabled />
      <span class="catalogue-pager-page" aria-current="page">1</span>
      <Button size="sm" variant="secondary" icon="chevron" iconOnly aria-label="Next page" disabled />
    </nav>
  </footer>
</div>
