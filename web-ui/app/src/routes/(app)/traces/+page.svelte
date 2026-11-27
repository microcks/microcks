<script lang="ts">
  import { page } from '$app/state';
  import { ApiBadge, Button, Callout, EmptyState, Field, Icon, LiveIndicator, Select } from '@microcks/deep-ink';
  import { onMount } from 'svelte';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import CallDetail from '#lib/components/observe/CallDetail.svelte';
  import CallGraph from '#lib/components/observe/CallGraph.svelte';
  import {
    arazzoStep,
    arazzoTitle,
    clockTime,
    flowCounts,
    isMiss,
    matchesTraceFilter,
    parseRunParam,
    statusLabel,
    type TraceCall,
    type TraceFilter,
    type TraceKind
  } from '#lib/components/observe/traces.js';
  import { fmt, pluralize } from '#lib/format.js';

  let { data } = $props();

  const MAX_CALLS = 60;
  const MAX_ROWS = 12;
  const FLASH_MS = 1400;
  const DESCRIPTION =
    'Real-time calls hitting your mocks, from REST and Kafka endpoints to MCP tool calls made by AI agents. Each call shows who called, which operation answered, which example the dispatcher picked and, for calls of an Arazzo run or session, the workflow step it belongs to.';

  type KindFilter = '' | Extract<TraceKind, 'REST' | 'MCP' | 'EVENT'>;

  const topology = $derived(data.topology);
  const initialRun = parseRunParam(page.url.searchParams.get('run'));

  let runId = $state<string | undefined>(initialRun);
  let serviceId = $state(page.url.searchParams.get('service') ?? '');
  let kind = $state<KindFilter>('');
  let status = $state<'' | 'ok' | 'error'>('');
  let arazzoOnly = $state(Boolean(initialRun));
  let focus = $state('');
  let selectedId = $state<string | undefined>('call-4');
  let clearedAt = $state('');
  let replayed = $state<TraceCall[]>([]);
  let replayError = $state('');
  let streamed = $state<TraceCall[]>([]);
  let pending = $state<TraceCall[]>([]);
  let paused = $state(false);
  let flashId = $state('');
  let announcement = $state('');
  let lastAnnounce = 0;

  function receive(call: TraceCall) {
    if (paused) {
      pending = [call, ...pending].slice(0, MAX_CALLS);
      return;
    }
    streamed = [call, ...streamed].slice(0, MAX_CALLS);
    flashId = call.callId;
    const now = Date.now();
    if (now - lastAnnounce > 5000) {
      lastAnnounce = now;
      announcement = `Live traces: latest ${call.method} ${call.path}, ${statusLabel(call)}.`;
    }
  }

  function togglePause() {
    if (paused && pending.length) {
      streamed = [...pending, ...streamed].slice(0, MAX_CALLS);
      pending = [];
    }
    paused = !paused;
  }

  onMount(() => {
    if (!topology) return;
    const source = new EventSource('/api/traces/stream');
    source.addEventListener('call', (e) => receive(JSON.parse((e as MessageEvent<string>).data) as TraceCall));
    return () => source.close();
  });

  $effect(() => {
    if (!flashId) return;
    const timer = setTimeout(() => (flashId = ''), FLASH_MS);
    return () => clearTimeout(timer);
  });

  const pool = $derived.by(() => {
    const byId = new Map<string, TraceCall>();
    for (const c of [...replayed, ...streamed, ...(topology?.history ?? [])]) if (!byId.has(c.callId)) byId.set(c.callId, c);
    return [...byId.values()].sort((a, b) => b.at.localeCompare(a.at) || b.seq - a.seq);
  });
  const replayedIds = $derived(new Set(replayed.map((c) => c.callId)));
  const visible = $derived(pool.filter((c) => !clearedAt || c.at > clearedAt));

  const filter = $derived<TraceFilter>({
    serviceId: serviceId || undefined,
    kind: kind || undefined,
    status: status || undefined,
    arazzoOnly: arazzoOnly || undefined,
    runId
  });
  const hasFilters = $derived(Boolean(serviceId || kind || status || arazzoOnly || runId));
  const matching = $derived(
    visible.filter((c) => matchesTraceFilter(c, filter) && (!focus || c.clientId === focus || c.endpointId === focus || c.exampleId === focus))
  );
  const shown = $derived(matching.slice(0, MAX_ROWS));
  const selected = $derived(selectedId ? pool.find((c) => c.callId === selectedId) : undefined);

  const counts = $derived(topology ? flowCounts(topology, [...replayed, ...streamed]) : { clientEdge: {}, exampleEdge: {} });
  const totalCalls = $derived(Object.values(counts.clientEdge).reduce((a, b) => a + b, 0));
  const noMatch = $derived(
    topology
      ? topology.exampleEdges
          .filter((e) => !topology.examples.find((x) => x.id === e.to)?.ok)
          .reduce((a, e) => a + (counts.exampleEdge[`${e.from}>${e.to}`] ?? 0), 0)
      : 0
  );
  const mcpCalls = $derived(
    topology ? topology.clientEdges.filter((e) => e.kind === 'MCP').reduce((a, e) => a + (counts.clientEdge[`${e.from}>${e.to}`] ?? 0), 0) : 0
  );
  const arazzoCount = $derived(visible.filter((c) => c.baggage || c.sessionId).length);
  const flashCall = $derived(flashId ? pool.find((c) => c.callId === flashId) : undefined);

  const serviceOptions = $derived.by(() => {
    const seen = new Map<string, string>();
    for (const e of topology?.endpoints ?? []) {
      const sample = pool.find((c) => c.serviceId === e.serviceId);
      seen.set(e.serviceId, sample?.serviceName ?? e.detail.split(' · ')[0]);
    }
    return [...seen];
  });
  const focusNode = $derived([...(topology?.clients ?? []), ...(topology?.endpoints ?? []), ...(topology?.examples ?? [])].find((n) => n.id === focus));
  const focusLabel = $derived(focusNode ? ('method' in focusNode ? `${focusNode.method} ${focusNode.label}` : focusNode.label) : '');

  function resetFilters() {
    serviceId = '';
    kind = '';
    status = '';
    arazzoOnly = false;
    runId = undefined;
  }

  function clearList() {
    clearedAt = pool[0]?.at ?? '';
    selectedId = undefined;
  }

  async function replay(call: TraceCall) {
    replayError = '';
    try {
      const res = await fetch(`/api/traces/${encodeURIComponent(call.callId)}/replay`, { method: 'POST' });
      if (!res.ok) throw new Error(((await res.json().catch(() => ({}))) as { message?: string }).message ?? 'The call could not be replayed.');
      const again = (await res.json()) as TraceCall;
      replayed = [again, ...replayed];
      selectedId = again.callId;
      flashId = again.callId;
    } catch (e) {
      replayError = e instanceof Error ? e.message : 'The call could not be replayed.';
    }
  }

  const methodTone = (c: TraceCall) => (c.kind === 'EVENT' ? 'event' : c.kind === 'MCP' ? 'mcp' : ['PATCH', 'PUT', 'POST'].includes(c.method) ? 'event' : 'rest');
  const listMeta = $derived(`newest first · ${matching.length > MAX_ROWS ? `${MAX_ROWS} of ${matching.length} shown` : pluralize(matching.length, 'call')}`);
  const newCalls = $derived(pluralize(pending.length, 'new call'));
</script>

<div data-main-pad="roomy">
  <PageHeader title="Live traces" description={DESCRIPTION} descriptionTone="secondary" descriptionWidth={780} align="start">
    {#snippet meta()}
      <LiveIndicator label={paused && pending.length ? `live · ${newCalls} waiting` : 'live · ~19 calls/min'} {paused} />
    {/snippet}
    {#snippet actions()}
      <Button icon={paused ? 'play' : 'pause'} aria-pressed={paused} onclick={togglePause}>{paused ? 'Resume' : 'Pause'}</Button>
      <Button icon="trash" variant="link" class="tr2-clear" onclick={clearList}>Clear list</Button>
    {/snippet}
  </PageHeader>

  {#if !topology}
    <div style="margin-top: 24px">
      <EmptyState title="Live traces are unavailable" headingLevel={2} icon="search-x">
        <p>{data.error}</p>
        {#snippet actions()}<Button variant="primary" href="/sources/new">Add a source</Button>{/snippet}
      </EmptyState>
    </div>
  {:else}
    <div class="stack tr2-page">
      <p class="tr2-demo-note tr2-muted" role="note">Demo traffic: Microcks has no per-call trace API yet, so these calls are generated for the services of this catalogue.</p>
      <div class="tr2-filters" role="group" aria-label="Trace filters">
        <Field label="Service" class="tr2-filter-service">
          {#snippet children(c)}
            <Select {...c} bind:value={serviceId}>
              <option value="">All services</option>
              {#each serviceOptions as [id, name] (id)}<option value={id}>{name}</option>{/each}
            </Select>
          {/snippet}
        </Field>
        <Field label="Protocol" class="tr2-filter-narrow">
          {#snippet children(c)}
            <Select {...c} bind:value={kind}>
              <option value="">All protocols</option>
              <option value="REST">REST (HTTP)</option>
              <option value="MCP">MCP tool calls</option>
              <option value="EVENT">Event (Kafka)</option>
            </Select>
          {/snippet}
        </Field>
        <Field label="Status" class="tr2-filter-narrow">
          {#snippet children(c)}
            <Select {...c} bind:value={status}>
              <option value="">All statuses</option>
              <option value="ok">2xx and delivered</option>
              <option value="error">4xx no match</option>
            </Select>
          {/snippet}
        </Field>
        <button
          type="button"
          class="tr2-toggle"
          aria-pressed={arazzoOnly}
          title={runId ? `Calls of the Arazzo run ${runId}` : 'Calls carrying microcks-arazzo-* baggage entries or an x-microcks-session header'}
          onclick={() => {
            if (arazzoOnly) runId = undefined;
            arazzoOnly = !arazzoOnly;
          }}
        >
          <Icon name="graph" size={14} />
          From Arazzo runs
          <span class="tr2-toggle-count">{arazzoCount}</span>
          {#if runId}<span class="sr-only">, run {runId}</span>{/if}
        </button>
        {#if focus}
          <span class="tr2-focus">
            Focused on <span class="demo-mono">{focusLabel}</span>
            <button type="button" aria-label="Remove node focus" onclick={() => (focus = '')}><Icon name="close" size={12} /></button>
          </span>
        {/if}
        {#if hasFilters}<button type="button" class="tr2-reset" onclick={resetFilters}>Reset filters</button>{/if}
        <div class="tr2-stats">
          <ApiBadge label="calls · 1 h" value={fmt(totalCalls)} status="action" />
          <ApiBadge label="no match" value={fmt(noMatch)} status="warning" />
          <ApiBadge label="mcp" value={fmt(mcpCalls)} status="info" />
        </div>
      </div>

      <CallGraph {topology} {counts} filter={{ serviceId, kind, status }} {focus} onFocus={(id) => (focus = focus === id ? '' : id)} flash={flashCall} />

      <div class="split-pane" style="--split-side: 400px; --split-side-max: none; --split-main: 560px">
        <section class="split-pane-main tr2-list" aria-labelledby="tr2-list-title">
          <div class="tr2-list-head">
            <div class="tr2-list-title">
              <h2 id="tr2-list-title">Latest calls</h2>
              <span class="tr2-muted">{listMeta}</span>
            </div>
            {#if paused && pending.length > 0}
              <button type="button" class="tr2-show-new" onclick={togglePause}><Icon name="arrow-down" size={12} />Show {newCalls}</button>
            {/if}
          </div>
          {#if replayError}
            <Callout tone="danger" title="Replay failed" live="assertive"><p>{replayError}</p></Callout>
          {/if}
          <div class="tr2-list-scroll">
            <div class="tr2-list-inner">
              <div class="tr2-row tr2-row-head" aria-hidden="true">
                <span>Time</span><span>Client</span><span>Request</span><span>Dispatch criteria</span><span>Matched example</span><span>Status</span><span class="tr2-end">ms</span>
              </div>
              {#if shown.length === 0}
                <div class="tr2-empty">
                  <span>{clearedAt && !hasFilters && !focus ? 'List cleared. Waiting for the next call…' : 'No call matches the current filters.'}</span>
                  <span class="tr2-muted">New calls appear here as soon as a client hits a mock.</span>
                </div>
              {:else}
                <ul class="tr2-rows">
                  {#each shown as c (c.callId)}
                    {@const step = arazzoStep(c)}
                    <li>
                      <button type="button" class="tr2-row" aria-pressed={c.callId === selectedId} data-flash={c.callId === flashId ? '' : undefined} onclick={() => (selectedId = c.callId)}>
                        <span class="tr2-time">{clockTime(c.at)}</span>
                        <span class="tr2-client"><span class="tr2-ellipsis">{c.clientName}</span> <span class="tr2-ip">{c.clientIp}</span></span>
                        <span class="tr2-request">
                          <span class="tr2-method" data-tone={methodTone(c)}>{c.method}</span>
                          <span class="tr2-path">
                            <span class="tr2-ellipsis">{c.path}</span>
                            {#if c.mcpTool}<span class="tr2-tool">tools/call · {c.mcpTool}</span>{/if}
                          </span>
                          {#if replayedIds.has(c.callId)}<span class="tr2-tag" data-tag="replay">replay</span>{/if}
                          {#if step}<span class="tr2-arazzo" title={arazzoTitle(c)}><span>ARAZZO</span> <span>{step}</span></span>{/if}
                          {#if c.sessionId}<span class="tr2-tag" data-tag="session" title={`x-microcks-session: ${c.sessionId}`}>session</span>{/if}
                        </span>
                        <span class="tr2-crit tr2-ellipsis">{c.criteria}</span>
                        <span class="tr2-example tr2-ellipsis" data-miss={isMiss(c) ? '' : undefined}>{c.exampleName}</span>
                        <span class="tr2-status" data-miss={isMiss(c) ? '' : undefined}>
                          <Icon name={isMiss(c) ? 'warning' : 'check'} size={13} />
                          <span class="demo-mono">{statusLabel(c)}</span>
                        </span>
                        <span class="tr2-ms">{c.ms}</span>
                        <span class="sr-only">ms{c.baggage ? `, ${arazzoTitle(c)}` : ''}{c.sessionId ? `, session ${c.sessionId}` : ''}</span>
                      </button>
                    </li>
                  {/each}
                </ul>
              {/if}
            </div>
          </div>
        </section>

        {#if selected}
          <CallDetail
            call={selected}
            replayNote={replayedIds.has(selected.callId) ? `Replayed at ${clockTime(selected.at)} · new call at the top of the list` : ''}
            onClose={() => (selectedId = undefined)}
            onReplay={() => replay(selected)}
          />
        {:else}
          <aside aria-label="Call detail" class="split-pane-side tr2-noselect">
            <span class="tr2-noselect-title">Select a call</span>
            <span>See its request and response, the dispatch criteria Microcks computed and the example it served. You can replay it from there.</span>
          </aside>
        {/if}
      </div>
      <p class="sr-only" role="status">{announcement}</p>
    </div>
  {/if}
</div>
