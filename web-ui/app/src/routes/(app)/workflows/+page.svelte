<script lang="ts">
  import { invalidate } from '$app/navigation';
  import {
    ApiBadge,
    Button,
    Callout,
    Chip,
    EmptyState,
    FilterBar,
    FilterReset,
    FilterRow,
    FilterSearch,
    Icon,
    Segmented
  } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import DemoFlag from '#lib/components/workflows/DemoFlag.svelte';
  import DocumentGroup from '#lib/components/workflows/DocumentGroup.svelte';
  import { callsOneOf, distinctSources, sourceKey } from '#lib/workflows/source-key.js';
  import LinkSourceDialog from '#lib/components/workflows/LinkSourceDialog.svelte';
  import { isAvailable } from '#lib/workflows/availability.js';
  import { listWorkflows, type ResultFilter } from '#lib/workflows/list.js';
  import type { ArazzoDocument, RunKindFilter, SourceDescription } from '#lib/workflows/types.js';

  let { data } = $props();

  const RESULTS: { id: ResultFilter; label: string; dot: 'neutral' | 'success' | 'danger' | 'ring' }[] = [
    { id: 'all', label: 'All', dot: 'neutral' },
    { id: 'passed', label: 'Passed', dot: 'success' },
    { id: 'failed', label: 'Failed', dot: 'danger' },
    { id: 'never', label: 'Never run', dot: 'ring' }
  ];
  const TARGET_LABEL: Record<Exclude<RunKindFilter, 'any'>, string> = {
    mocks: 'Microcks mocks',
    implementations: 'Implementations',
    'mock-url': 'Mock URL',
    session: 'Session'
  };
  const TARGETS: { id: RunKindFilter; label: string; icon?: 'plus-square' | 'server' }[] = [
    { id: 'any', label: 'Any target' },
    { id: 'mocks', label: TARGET_LABEL.mocks, icon: 'plus-square' },
    { id: 'implementations', label: TARGET_LABEL.implementations, icon: 'server' },
    { id: 'mock-url', label: TARGET_LABEL['mock-url'] },
    { id: 'session', label: TARGET_LABEL.session }
  ];
  const RESULT_LABEL = { passed: 'Passed', failed: 'Failed', never: 'Never run' } as const;

  const SAMPLE_YAML = `arazzo: 1.1.0
info:
  title: Pastry workflows
  version: 1.2.0
sourceDescriptions:
  - name: apiPastry
    url: ./apipastry-2.0-openapi.yaml
    type: openapi
workflows:
  - workflowId: restock-pastry
    steps:
      - stepId: listPastries
        operationId: GetPastries
        successCriteria:
          - condition: $statusCode == 200
        outputs:
          firstName: $response.body#/0/name`;

  const ONBOARDING = [
    { title: 'Import an Arazzo document', text: 'Upload a file or import it from a URL in Importers. Link each source description to a Microcks API, in the dialog or with an ArazzoSources overlay.' },
    { title: 'Play it on the mocks', text: 'Run it on the Microcks mocks, call its Mock URL, or open a verification session for the application under test.' },
    { title: 'Run it on your implementations', text: 'Give a base URL per source; each step is checked on its successCriteria and, optionally, against its OpenAPI schema.' }
  ];

  let q = $state('');
  let result = $state<ResultFilter>('all');
  let runKind = $state<RunKindFilter>('any');
  let sources = $state<string[]>([]);
  let demo = $state<'loading' | 'empty' | null>(null);
  let linking = $state<{ document: ArazzoDocument; source: SourceDescription } | null>(null);
  let linked = $state<string | null>(null);

  const mockUrlOn = $derived(isAvailable('mock-url', data.demo));
  const documents = $derived<ArazzoDocument[]>(data.documents);
  const run = (f: Parameters<typeof listWorkflows>[3]) => listWorkflows(data.documents, data.workflows, data.runs, f);

  // The counts of a filter are computed with that filter left out (as in the prototype).
  const byResult = $derived(Object.fromEntries(RESULTS.map((r) => [r.id, run({ query: q, sources, runKind, result: r.id })])));
  const items = $derived(byResult[result] ?? []);
  const targetCounts = $derived(
    Object.fromEntries(TARGETS.map((t) => [t.id, run({ query: q, sources, runKind: t.id }).filter((w) => t.id === 'any' || w.lastRun).length]))
  );
  const bySource = $derived(run({ query: q, runKind, result }));
  const allWorkflows = $derived(run({}));
  const allSources = $derived(distinctSources(documents));
  const sourceCount = $derived(documents.reduce((n, d) => n + d.sources.length, 0));
  const unlinkedTotal = $derived(documents.flatMap((d) => d.sources).filter((s) => s.link.status === 'not-linked').length);
  const totalWorkflows = $derived(documents.reduce((n, d) => n + d.workflowIds.length, 0));

  const activeDesc = $derived([
    ...(q.trim() ? [`“${q.trim()}”`] : []),
    ...(result !== 'all' ? [RESULT_LABEL[result]] : []),
    ...(runKind !== 'any' ? [TARGET_LABEL[runKind]] : []),
    ...sources.map((k) => k.split('|')[0] ?? k)
  ]);
  const showOnboarding = $derived(demo === 'empty' || documents.length === 0);
  const loading = $derived(demo === 'loading');
  const ready = $derived(!loading && !showOnboarding);
  const targetPhrase = $derived(runKind === 'any' ? 'any target' : TARGET_LABEL[runKind]);
  const groups = $derived(
    documents.map((d) => ({ document: d, rows: items.filter((i) => i.document.id === d.id) })).filter((g) => g.rows.length > 0)
  );
  const countLine = $derived(
    showOnboarding
      ? '0 workflows · 0 Arazzo documents'
      : loading
        ? 'Loading…'
        : `${totalWorkflows} workflows · ${documents.length} Arazzo documents · ${sourceCount} source descriptions (${unlinkedTotal} not linked)`
  );
  const serviceOf = (id: string) => data.services.find((s) => s.id === id);
  const usesSource = (w: (typeof items)[number], key: string) => callsOneOf(w.workflow, w.document, [key]);

  function reset() {
    q = '';
    result = 'all';
    runKind = 'any';
    sources = [];
  }
  const toggleSource = (name: string) => (sources = sources.includes(name) ? sources.filter((n) => n !== name) : [...sources, name]);
</script>

<div class="wf-page" data-main-pad="roomy">
  <PageHeader
    title="Workflows"
    size="lg"
    description={`Multi-step API journeys described with Arazzo. Each step calls an operation of an API already in Microcks; play them against the mocks or against your implementations${mockUrlOn ? ', or call them through their Mock URL' : ''}.`}
  >
    {#snippet meta()}{countLine}{#if data.demo} <DemoFlag />{/if}{/snippet}
    {#snippet actions()}
      <Button href="/sources" icon="git-branch">Sources</Button>
      <Button href="/sources/new" variant="primary" icon="download">Import Arazzo document</Button>
    {/snippet}
  </PageHeader>

  {#if linked}
    <Callout tone="success" title="{linked} linked" live="polite">The workflows that use it can run now.</Callout>
  {/if}

  {#if !showOnboarding}
    <FilterBar label="Workflow filters">
      <FilterRow>
        <FilterSearch label="Filter workflows" placeholder="Filter by name, workflowId or stepId…" autocomplete="off" bind:value={q} />
        <Segmented
          label="Last run result"
          options={RESULTS.map((r) => ({ ...r, count: byResult[r.id]?.length }))}
          value={result}
          onchange={(id) => (result = id as ResultFilter)}
        />
        <Segmented
          label="Run target"
          options={TARGETS.map((t) => ({ ...t, count: targetCounts[t.id] }))}
          value={runKind}
          onchange={(id) => (runKind = id as RunKindFilter)}
        />
      </FilterRow>
      <FilterRow title="Sources">
        {#each allSources as source (sourceKey(source))}
          {@const svc = source.link.status === 'linked' ? serviceOf(source.link.serviceId) : undefined}
          {@const key = sourceKey(source)}
          {@const count = bySource.filter((w) => usesSource(w, key)).length}
          {#if source.link.status === 'not-linked'}
            <Chip class="wf-src-unlinked" pressed={sources.includes(key)} {count} onclick={() => toggleSource(key)}>
              <Icon name="warning" size={13} />{source.name} · not linked
            </Chip>
          {:else}
            <Chip protocol={source.link.protocol} pressed={sources.includes(key)} {count} onclick={() => toggleSource(key)}>
              {svc ? `${svc.name} ${svc.version}` : source.name}
            </Chip>
          {/if}
        {/each}
        {#if activeDesc.length > 0}<FilterReset count={activeDesc.length} onclick={reset} />{/if}
      </FilterRow>
    </FilterBar>
  {/if}

  {#if loading}
    <section aria-busy="true" aria-label="Loading workflows" class="wf-doc wf-skel">
      <span class="sr-only" role="status">Loading workflows…</span>
      <div aria-hidden="true" class="wf-skel-head">
        <div class="wf-skel-line">
          <span class="wf-skel-bar" data-fill="raised" style="width:240px;max-width:60%;height:14px"></span>
          <span class="wf-skel-bar" style="width:90px;height:14px"></span>
          <span class="wf-skel-bar" style="width:90px;height:14px"></span>
        </div>
        <div class="wf-skel-line">
          {#each [170, 200, 150] as w (w)}<span class="wf-skel-bar" data-shape="chip" style="width:{w}px;height:22px"></span>{/each}
        </div>
      </div>
      {#each [0, 1, 2, 3] as i (i)}
        <div aria-hidden="true" class="wf-skel-row" style="opacity:{1 - i * 0.18}">
          <span class="wf-skel-stack">
            <span class="wf-skel-bar" data-fill="raised" data-shape="chip" style="width:{62 + ((i * 13) % 30)}%;height:22px"></span>
            <span class="wf-skel-bar" style="width:40%;height:10px;border-radius:3px"></span>
          </span>
          <span class="wf-skel-bar" data-shape="pill" style="width:88px;height:10px"></span>
          <span class="wf-skel-bar wf-skel-flex" style="max-width:{180 + ((i * 47) % 120)}px;height:12px"></span>
          <span class="wf-skel-bar wf-skel-end" style="width:64px;height:30px"></span>
        </div>
      {/each}
    </section>
  {/if}

  {#if showOnboarding && !loading}
    <section aria-labelledby="wf-onb-title" class="wf-onb">
      <div class="wf-onb-main">
        <div class="wf-onb-head">
          <ApiBadge arazzo name="no workflow yet" class="wf-onb-badge" />
          <h2 id="wf-onb-title">Bring your API workflows into Microcks</h2>
          <p class="wf-onb-lead">
            Arazzo is the OpenAPI Initiative specification for describing multi-step API workflows, where the output of one call feeds the next. Import an
            Arazzo document and link each of its source descriptions to an API already in Microcks: the whole journey can then be played on the mocks and
            run end to end against your implementations.
          </p>
        </div>
        <ol class="wf-onb-steps">
          {#each ONBOARDING as s, i (s.title)}
            <li>
              <span aria-hidden="true" class="wf-onb-n">{i + 1}</span>
              <span class="wf-onb-text"><span class="wf-onb-title">{s.title}</span><span class="wf-onb-desc">{s.text}</span></span>
            </li>
          {/each}
        </ol>
        <div class="wf-row">
          <Button href="/sources/new" variant="primary" icon="download">Import Arazzo document</Button>
          <Button href="/sources">Sync from a Git repository</Button>
        </div>
      </div>
      <figure class="wf-onb-figure">
        <figcaption>A minimal Arazzo document</figcaption>
        <pre>{SAMPLE_YAML}</pre>
      </figure>
    </section>
  {/if}

  {#if ready && items.length > 0}
    <div class="wf-result-line">
      <p role="status" aria-live="off"><span class="wf-result-count">{items.length}</span> of {totalWorkflows} workflows · last run on {targetPhrase}</p>
      <p class="wf-legend">
        <span class="wf-legend-lead">Steps: one dot per step, one lane per source</span>
        <span class="wf-legend-item"><span aria-hidden="true" class="wf-loom-dot" data-state="ok" data-legend=""></span>passed</span>
        <span class="wf-legend-item"><span aria-hidden="true" class="wf-loom-dot" data-state="ko" data-legend=""></span>failed</span>
        <span class="wf-legend-item"><span aria-hidden="true" class="wf-loom-dot" data-state="skip" data-legend=""></span>not reached</span>
        <span class="wf-legend-item"><span aria-hidden="true" class="wf-loom-dot" data-state="never" data-legend=""></span>never run</span>
        <span class="wf-legend-item"><span aria-hidden="true" class="wf-legend-bars"><span></span><span></span></span>parallel branches (step dependsOn)</span>
      </p>
    </div>

    <div class="wf-groups">
      {#each groups as { document, rows } (document.id)}
        <DocumentGroup
          {document}
          {rows}
          docWorkflows={allWorkflows.filter((w) => w.document.id === document.id)}
          total={document.workflowIds.length}
          services={data.services}
          {runKind}
          onlink={(source) => (linking = { document, source })}
        />
      {/each}
    </div>
  {/if}

  {#if ready && items.length === 0}
    <EmptyState title="No workflow matches these filters" icon="search-x" headingLevel={2}>
      <p>None of the {totalWorkflows} workflows matches: {activeDesc.join(' · ')}. Remove a filter or reset the selection.</p>
      {#snippet actions()}<Button variant="primary" icon="reset" onclick={reset}>Reset filters</Button>{/snippet}
    </EmptyState>
  {/if}

  {#if linking}
    <LinkSourceDialog
      document={linking.document}
      source={linking.source}
      services={data.services}
      onclose={() => (linking = null)}
      onlinked={async (service) => {
        linked = `${linking?.source.name} → ${service.name} ${service.version}`;
        linking = null;
        await invalidate('app:workflows');
      }}
    />
  {/if}

  {#if data.demo}
  <section aria-label="Prototype states" class="wf-demo">
    <span>Prototype states</span>
    <button type="button" class="wf-demo-btn" aria-pressed={demo === 'loading'} onclick={() => (demo = demo === 'loading' ? null : 'loading')}>Show loading</button>
    <button type="button" class="wf-demo-btn" aria-pressed={demo === 'empty'} onclick={() => (demo = demo === 'empty' ? null : 'empty')}>Show empty state</button>
  </section>
  {/if}
</div>
