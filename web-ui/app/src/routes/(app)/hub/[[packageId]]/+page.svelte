<script lang="ts">
  import { goto, invalidate } from '$app/navigation';
  import { page } from '$app/state';
  import { ApiBadge, Chip, EmptyState, FilterSearch, Icon } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import HubDetail from '#lib/components/observe/HubDetail.svelte';
  import { isInCatalogue, type HubPackage } from '#lib/hub.js';
  import { pluralize } from '#lib/format.js';

  let { data } = $props();

  let search = $state('');
  let category = $state('');

  const q = $derived(search.trim().toLowerCase());
  const byQuery = $derived(
    data.packages.filter(
      (p) => !q || [p.name, p.description, p.category, ...p.apis.flatMap((a) => [a.name, a.id, a.protocol])].some((t) => t.toLowerCase().includes(q))
    )
  );
  const shown = $derived(byQuery.filter((p) => !category || p.category === category));
  const selected = $derived(shown.find((p) => p.id === page.params.packageId) ?? shown[0]);
  const apiTotal = $derived(shown.reduce((n, p) => n + p.apis.length, 0));
  const categories = $derived(
    [...new Set(data.packages.map((p) => p.category))].sort((a, b) => a.localeCompare(b)).map((name) => ({ name }))
  );

  /** APIs of a package whose current version is already in this catalogue. */
  const installedCount = (p: HubPackage) =>
    p.apis.filter((a) => {
      const v = a.details[a.currentVersion];
      return v && isInCatalogue(v, data.sources, data.services);
    }).length;
  const metaOf = (p: HubPackage) => [pluralize(p.apis.length, 'API'), p.maturity, p.updated].filter(Boolean).join(' · ');
  const select = (id: string) => {
    if (id !== page.params.packageId) void goto(`/hub/${id}`);
  };
</script>

<div data-main-pad="roomy">
  <PageHeader
    title="Hub"
    descriptionWidth={760}
    description="Community packages of API mocks and conformance tests. Install any version of an API to get its Service, examples and tests in this instance, once or kept in sync."
  >
    {#snippet actions()}
      <span class="hb-connected"><Icon name="check" size={14} />Connected to<span class="hb-connected-url">{data.endpoint}</span></span>
    {/snippet}
  </PageHeader>

  {#if data.error}
    <div style="margin-top: 24px">
      <EmptyState title="Hub unavailable" headingLevel={2} icon="search-x"><p>{data.error}</p></EmptyState>
    </div>
  {:else}
    <div class="hb-filters">
      <div class="hb-search-row">
        <FilterSearch size="lg" label="Search packages and APIs" placeholder="Search packages, APIs, protocols…" bind:value={search} />
        <span role="status" class="hb-count">{pluralize(shown.length, 'package')} · {pluralize(apiTotal, 'API')}</span>
      </div>
      <div role="group" aria-label="Categories" class="hb-cats">
        <Chip size="lg" pressed={category === ''} count={byQuery.length} onclick={() => (category = '')}>All</Chip>
        {#each categories as c (c.name)}
          <Chip size="lg" pressed={category === c.name} count={byQuery.filter((p) => p.category === c.name).length} onclick={() => (category = c.name)}>{c.name}</Chip>
        {/each}
      </div>
    </div>

    <div class="split-pane" data-keep-side-max style="--split-side: 400px; --split-side-max: 560px; --split-main: 560px">
      <div class="split-pane-main">
        <ul class="tile-grid" aria-label="Packages">
          {#each shown as p (p.id)}
                        <li>
              <article class="tile" data-interactive data-selected={p.id === selected?.id ? '' : undefined}>
                <div class="tile-head">
                  <div class="tile-heading">
                    <h2 class="tile-title">
                      <button type="button" class="tile-action" aria-pressed={p.id === selected?.id} onclick={() => select(p.id)}>{p.name}</button>
                    </h2>
                    <span class="tile-meta">maintained by <b>community</b> · {metaOf(p)}</span>
                  </div>
                  <span class="tile-aside"><span class="tile-tag">{p.category}</span></span>
                </div>
                <div class="tile-body">
                  <p>{p.description}</p>
                  <div class="tile-badges">
                    {#each p.apis as a (a.id)}
                      <ApiBadge size="sm" protocol={a.protocol} name={a.name} version={a.currentVersion} />
                    {/each}
                  </div>
                </div>
                <div class="tile-footer">
                  {#if installedCount(p) > 0}
                    <span class="hb-installed"><Icon name="check" size={15} />{installedCount(p)} of {p.apis.length} in your catalogue</span>
                  {:else}
                    <span class="hb-hint">Pick an API version to install</span>
                  {/if}
                </div>
              </article>
            </li>
          {/each}
        </ul>
        {#if shown.length === 0}
          <div class="hb-empty">
            No package matches this search.
            <button type="button" class="hb-empty-reset" onclick={() => { search = ''; category = ''; }}>Clear search and category</button>
          </div>
        {/if}
      </div>

      {#if selected}
        <div class="split-pane-side">
          {#key selected.id}
            <HubDetail
              pkg={selected}
              endpoint={data.endpoint}
              sources={data.sources}
              services={data.services}
              onInstalled={() => invalidate('app:hub')}
            />
          {/key}
        </div>
      {/if}
    </div>
  {/if}
</div>
