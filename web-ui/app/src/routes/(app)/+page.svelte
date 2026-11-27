<script lang="ts">
  import { ApiBadge, Button, Chip, ConformanceBand, Icon, LiveIndicator } from '@microcks/deep-ink';
  import { onDestroy } from 'svelte';
  import CatalogPageHeader from '#lib/components/catalog/CatalogPageHeader.svelte';
  import SplitPane from '#lib/components/catalog/SplitPane.svelte';
  import StatCard from '#lib/components/catalog/StatCard.svelte';
  import StatGrid from '#lib/components/catalog/StatGrid.svelte';
  import { PROTOCOL_ATTR } from '#lib/components/catalog/types.js';
  import { PROTOCOL_ORDER, fmt, formatRelative, operationUnit, pluralize } from '#lib/format.js';
  import { DEMO_TRACE_STATUS } from '#lib/placeholders/catalog.js';
  import { TEST_RESULT_WORD, testRunHref } from '#lib/components/testing/model.js';
  import { microcksBaseUrl } from '#lib/base-url.js';
  import type { Protocol } from '#lib/types.js';

  let { data } = $props();

  const START_STEPS = [
    { id: 'import', title: 'Import a contract', desc: 'OpenAPI, AsyncAPI, gRPC Protobuf, GraphQL schema, Postman collection or SoapUI project. Drop a file or point to a Git URL.', cta: 'Import a contract', to: '/sources/new', code: '' },
    { id: 'call', title: 'Call the mock', desc: 'Every operation is served right after the import, at a URL derived from the service name and version.', cta: 'Browse APIs', to: '/services', code: `curl ${microcksBaseUrl()}/rest/API+Pastry/2.0/pastries/Millefeuille` },
    { id: 'test', title: 'Run a conformance test', desc: 'Point Microcks at your implementation. It replays the examples and checks responses against the contract.', cta: 'Run a test', to: '/tests/create', code: '' }
  ];

  let empty = $state(false);
  let done = $state<Record<string, boolean>>({});
  let protos = $state<Protocol[]>([]);
  let paused = $state(false);
  let ticks = $state(0);
  let status = $state('');

  const c = $derived(data.cockpit);
  const wall = $derived(empty || !c ? [] : c.wall);
  const callsOf = (id: string) => wall.find((s) => s.id === id)?.callsToday ?? 0;
  const hourly = $derived(c?.invocations.hourly ?? []);
  const countOf = (p: Protocol) => wall.filter((s) => s.protocol === p).length;
  const groups = $derived(
    PROTOCOL_ORDER.filter((p) => protos.length === 0 || protos.includes(p)).map((p) => ({
      p,
      list: wall.filter((s) => s.protocol === p).sort((a, b) => callsOf(b.id) - callsOf(a.id))
    }))
  );
  const shown = $derived(groups.reduce((n, g) => n + g.list.length, 0));
  const top = $derived(wall.filter((s) => s.protocol !== 'EVENT' && callsOf(s.id) > 0).sort((a, b) => callsOf(b.id) - callsOf(a.id)).slice(0, 5));
  const tMax = $derived(Math.max(1, top[0] ? callsOf(top[0].id) : 1));
  const attention = $derived(empty || !c ? [] : c.attention);
  const stepsDone = $derived(START_STEPS.filter((s) => done[s.id]).length);
  const firstOpen = $derived(START_STEPS.findIndex((s) => !done[s.id]));
  const hMax = $derived(Math.max(1, ...hourly));
  const passed = $derived(empty ? 0 : c?.tests.last7d.passed ?? 0);
  const failed = $derived(empty ? 0 : c?.tests.last7d.failed ?? 0);
  const recentTests = $derived(empty || !c ? [] : c.tests.recent);
  const syncCalls = $derived(c?.invocations.total ?? 0);
  const level = (v: number | null) => (v === null ? 'none' : v >= 80 ? 'high' : v >= 50 ? 'mid' : 'low');
  const avg = $derived(empty ? null : (c?.tests.averageConformance ?? null));
  const TONE = { high: 'success', mid: 'warning', low: 'danger', none: 'neutral' } as const;
  const dateText = new Date().toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric' });

  const headerText = $derived(
    `${microcksBaseUrl().replace(/^https?:\/\//, '')} · ${dateText} · ${empty ? 'fresh install, nothing imported yet' : `${pluralize(c?.services ?? 0, 'service')} mocked, ${pluralize(attention.length, 'item')} need attention`}`
  );

  const traceRows = $derived.by(() => {
    const src = wall.filter((s) => s.protocol !== 'EVENT');
    if (!src.length) return [];
    return Array.from({ length: 5 }, (_, i) => {
      const s = src[(i + ticks) % src.length];
      const when = new Date(Date.now() - (i + 1) * 7000);
      const method = (i + ticks) % 3 === 0 ? 'POST' : 'GET';
      return { id: `${ticks}-${i}`, time: when.toISOString().slice(11, 19), method, protocol: s.protocol, path: `/${s.name.replace(/\s+/g, '+')}/${s.version}`, status: DEMO_TRACE_STATUS[(i + ticks) % DEMO_TRACE_STATUS.length], ms: 6 + ((i + ticks) * 7) % 30 };
    });
  });

  const timer = setInterval(() => {
    if (!paused && !empty) ticks++;
  }, 4000);
  onDestroy(() => clearInterval(timer));

  function toggleProto(p: Protocol) {
    protos = protos.includes(p) ? protos.filter((x) => x !== p) : [...protos, p];
  }
</script>

{#if !c}
  <CatalogPageHeader titleText="Cockpit" />
  <div role="alert" class="cockpit-panel-empty">{data.error}</div>
{:else}
  <div class="cockpit">
    <CatalogPageHeader titleText="Cockpit" size="lg" description={headerText}>
      {#snippet actions()}
        <Button
          variant="dashed"
          icon="window"
          aria-pressed={empty}
          onclick={() => {
            empty = !empty;
            status = empty ? 'Showing a fresh install with nothing imported.' : 'Back to the running instance.';
          }}
        >
          {empty ? 'Back to running instance' : 'Show empty install'}
        </Button>
        <Button variant="secondary" icon="download" href="/sources/new">Import a contract</Button>
        <Button variant="primary" icon="play" href="/tests/create">Run a test</Button>
      {/snippet}
    </CatalogPageHeader>
    <p class="sr-only" role="status">{status}</p>

    <StatGrid label="Key figures" class="cockpit-stats">
      <StatCard title="APIs & services" value={empty ? 0 : c.services} unit={empty ? 'no contract imported yet' : `${pluralize(c.operations, 'operation')} · ${pluralize(c.sources, 'source')}`}>
        {#snippet action()}<a href="/services">Catalogue</a>{/snippet}
        <ul class="kpi-protocols" aria-label="Services per protocol">
          {#each PROTOCOL_ORDER as p (p)}
            <li><ApiBadge protocol={p} name={String(countOf(p))} size="sm" /></li>
          {/each}
        </ul>
      </StatCard>
      <StatCard title="Mock invocations today" value={fmt(empty ? 0 : syncCalls)} unit="requests">
        <div class="kpi-bars" role="img" aria-label={empty ? 'No mock invocation yet today' : `Mock requests per hour today, 00 h to ${String(hourly.length - 1).padStart(2, '0')} h: ${hourly.join(', ')}`}>
          {#each Array.from({ length: 24 }, (_, h) => h) as h (h)}
            {@const future = h >= hourly.length}
            <span data-future={future ? '' : undefined} data-current={h === hourly.length - 1 ? '' : undefined} style:height={future || empty ? '2px' : `${Math.max(2, Math.round(((hourly[h] ?? 0) / hMax) * 100))}%`}></span>
          {/each}
        </div>
        <span class="cockpit-stat-foot">{empty ? 'Mocks appear as soon as a contract is imported.' : 'Counted by the Microcks backend, server time.'}</span>
      </StatCard>
      <StatCard title="Tests · last 7 days" value={passed + failed} unit="runs">
        <div class="kpi-meter" role="img" aria-label={passed + failed === 0 ? 'No test run in the last 7 days' : `${passed} passed, ${failed} failed over the last 7 days`}>
          <span style:flex={`${passed} 1 0`} style:background="var(--success)"></span>
          <span style:flex={`${failed} 1 0`} style:background="var(--danger)"></span>
        </div>
        <div class="kpi-counts">
          <span class="tone-success"><Icon name="check-circle" size={14} /> {passed} passed</span>
          <span class="tone-danger"><Icon name="x-circle" size={14} /> {failed} failed</span>
        </div>
        <span class="kpi-workflows"><span>{passed + failed === 0 ? 'No finished test in the last 7 days.' : 'Finished runs, all services.'}</span></span>
      </StatCard>
      <StatCard title="Average conformance" value={avg === null ? '—' : `${avg}%`} unit={avg === null ? 'no test yet' : 'current score'} tone={TONE[level(avg)]}>
        <div class="kpi-gauge" data-level={level(avg)} aria-hidden="true"><span style:width={`${avg ?? 0}%`}></span></div>
        <span class="cockpit-stat-foot">{avg === null ? 'Run a first test to get a conformance score.' : 'Mean of the latest finished test of each tested service.'}</span>
      </StatCard>
    </StatGrid>

    {#if empty || c.services === 0}
      <section aria-labelledby="start-title" class="start">
        <SplitPane mainWidth="460px" mainClass="start-main" side={startOther}>
          <div>
            <div class="start-intro">
              <div class="start-heading">
                <h2 id="start-title" class="start-title">Getting started</h2>
                <span class="start-progress">{stepsDone} of 3 done</span>
              </div>
              <p class="start-lead">This Microcks instance has no API yet. Three steps take you from a contract to a live mock and a conformance proof.</p>
            </div>
            <ol class="start-steps">
              {#each START_STEPS as step, i (step.id)}
                {@const isDone = Boolean(done[step.id])}
                <li class="start-step" data-current={i === firstOpen ? '' : undefined} data-done={isDone ? '' : undefined}>
                  <button type="button" class="start-step-toggle" aria-pressed={isDone} aria-label={`Mark step ${i + 1} as ${isDone ? 'not done' : 'done'}`} onclick={() => (done = { ...done, [step.id]: !isDone })}>
                    {#if isDone}<Icon name="check" size={14} />{:else}{i + 1}{/if}
                  </button>
                  <div class="start-step-text">
                    <span class="start-step-title">{step.title}</span>
                    <span class="start-step-desc">{step.desc}</span>
                    {#if step.code}<pre class="start-step-code">{step.code}</pre>{/if}
                  </div>
                  <a class="start-step-cta" href={step.to}>{step.cta}</a>
                </li>
              {/each}
            </ol>
          </div>
        </SplitPane>
      </section>
    {:else}
      <section aria-labelledby="wall-title" class="wall">
        <div class="wall-head">
          <div class="wall-heading">
            <h2 id="wall-title" class="wall-title">API wall</h2>
            <span class="wall-count" role="status">{protos.length ? `${shown} of ${wall.length} services` : `${wall.length} services`}</span>
          </div>
          <div class="wall-filters" role="group" aria-label="Filter by protocol">
            {#each PROTOCOL_ORDER as p (p)}
              <Chip protocol={p} count={countOf(p)} pressed={protos.includes(p)} onclick={() => toggleProto(p)}>{p}</Chip>
            {/each}
            {#if protos.length}<button type="button" class="wall-show-all" onclick={() => (protos = [])}>Show all</button>{/if}
          </div>
        </div>
        <div class="wall-groups">
          {#each groups.filter((g) => g.list.length) as { p, list } (p)}
            <section aria-label={`${p} services, ${list.length}`} class="wall-group" data-protocol={PROTOCOL_ATTR[p]} style:flex={`${list.length} 1 ${list.length * 232}px`}>
              <div class="wall-group-head" aria-hidden="true"><span class="wall-swatch"></span>{p}<span class="wall-group-count">{list.length}</span></div>
              <div class="wall-tiles">
                {#each list as s (s.id)}
                  <a class="wall-tile" href={`/services/${s.id}`}>
                    <span class="wall-tile-head"><ApiBadge protocol={s.protocol} name={s.name} version={s.version} class="wall-tile-badge" /></span>
                    <span class="wall-tile-score">
                      <ConformanceBand size="mini" outcomes={s.conformance?.outcomes ?? Array.from({ length: s.operations }, () => 'untested' as const)} unit={operationUnit(s.protocol)} />
                      <span class="wall-tile-pct" data-level={level(s.conformance?.score ?? null)}>{s.conformance ? `${s.conformance.score}%` : 'untested'}</span>
                    </span>
                    <span class="wall-tile-usage">{pluralize(s.operations, operationUnit(s.protocol))}</span>
                  </a>
                {/each}
              </div>
            </section>
          {/each}
        </div>
        <ul class="wall-legend" aria-label="Legend">
          <li><span aria-hidden="true" class="wall-legend-swatch" data-kind="pass"></span>operation conformant</li>
          <li><span aria-hidden="true" class="wall-legend-swatch" data-kind="fail"></span>operation failing</li>
          <li><span aria-hidden="true" class="wall-legend-swatch" data-kind="untested"></span>never tested</li>
        </ul>
      </section>

      <div class="cockpit-panels">
        <section aria-labelledby="attn-title" class="cockpit-panel">
          <div class="cockpit-panel-head">
            <h2 id="attn-title" class="cockpit-panel-title">Needs attention</h2>
            <span class="cockpit-panel-meta">{pluralize(attention.length, 'item')}</span>
          </div>
          {#if attention.length === 0}<p class="cockpit-panel-empty">Nothing needs your attention.</p>{/if}
          {#each attention as a (a.id)}
            <div class="attn" data-tone={a.tone}>
              <Icon class="attn-icon" name={a.tone === 'danger' ? 'x-circle' : a.tone === 'warning' ? 'warning' : 'never-tested'} size={18} />
              <div class="attn-text">
                <span class="attn-title">{a.title}</span>
                <span class="attn-detail">{a.detail}</span>
              </div>
              <a class="attn-cta" href={a.href}>{a.cta}<Icon name="chevron" size={13} /></a>
            </div>
          {/each}
        </section>

        <section aria-labelledby="tests-title" class="cockpit-panel" data-gap="tight">
          <div class="cockpit-panel-head" data-pad="">
            <h2 id="tests-title" class="cockpit-panel-title">Recent tests</h2>
          </div>
          {#each recentTests as r (r.id)}
            {@const ok = r.result === 'passed'}
            <a class="recent-test" data-ok={ok ? '' : undefined} href={testRunHref(r)}>
              <span class="recent-test-result"><Icon name={ok ? 'check-circle' : 'x-circle'} size={15} />{TEST_RESULT_WORD[r.result]}</span>
              <span class="recent-test-what"><ApiBadge protocol={r.protocol} name={r.serviceName} version={r.version} size="sm" /><span class="recent-test-runner">{r.runner}</span></span>
              <span class="recent-test-figures"><span>{r.conformance}%</span><span class="recent-test-when">{formatRelative(r.at)}</span></span>
            </a>
          {:else}
            <p class="cockpit-stat-foot">No test run yet.</p>
          {/each}
        </section>

        <section aria-labelledby="top-title" class="cockpit-panel">
          <div class="cockpit-panel-head">
            <h2 id="top-title" class="cockpit-panel-title">Most called mocks today</h2>
          </div>
          <ol class="top-list">
            {#each top as s, i (s.id)}
              <li>
                <a href={`/services/${s.id}`} class="top-row" data-protocol={PROTOCOL_ATTR[s.protocol]}>
                  <span class="top-row-line">
                    <span class="top-rank">{i + 1}</span>
                    <span class="top-name"><span class="top-proto">{s.protocol}</span><span class="top-label">{s.name} <span class="top-version">{s.version}</span></span></span>
                    <span class="top-count">{fmt(callsOf(s.id))}</span>
                  </span>
                  <span class="top-bar" aria-hidden="true"><span data-first={i === 0 ? '' : undefined} style:width={`${Math.max(2, Math.round((callsOf(s.id) / tMax) * 100))}%`}></span></span>
                </a>
              </li>
            {:else}
              <li class="cockpit-stat-foot">No mock invoked yet today.</li>
            {/each}
          </ol>
          <div class="cockpit-panel-foot"><span>Event APIs publish on their own schedule and are counted apart.</span></div>
        </section>

        <section aria-labelledby="traces-title" class="cockpit-panel">
          <div class="cockpit-panel-head" data-align="center">
            <div class="cockpit-panel-heading">
              <h2 id="traces-title" class="cockpit-panel-title">Live traces</h2>
              <LiveIndicator size="sm" {paused} />
              <Chip size="sm" tone="info">Demo data</Chip>
            </div>
            <button type="button" class="traces-pause" aria-pressed={paused} onclick={() => { paused = !paused; status = paused ? 'Live traces paused.' : 'Live traces resumed.'; }}>{paused ? 'Resume' : 'Pause'}</button>
          </div>
          <div role="log" aria-live="off" aria-label="Latest mock calls (demo data)" class="trace-log">
            {#each traceRows as t, i (t.id)}
              <div class="trace-row" data-new={i === 0 && !paused ? '' : undefined}>
                <span class="trace-time">{t.time}</span>
                <span class="trace-verb" data-protocol={PROTOCOL_ATTR[t.protocol]}>{t.method}</span>
                <span class="trace-path">{t.path}</span>
                <span class="trace-status" data-ok={t.status < 400 ? '' : undefined}>{t.status} · {t.ms} ms</span>
              </div>
            {/each}
          </div>
          <div class="cockpit-panel-foot"><span>Simulated calls. Real mock traffic is not traced yet.</span><a href="/traces">Open live traces<Icon name="chevron" size={13} /></a></div>
        </section>
      </div>
    {/if}
  </div>
{/if}

{#snippet startOther()}
  <aside aria-labelledby="start-other-title" class="start-other">
    <h3 id="start-other-title" class="start-other-title">Other ways to start</h3>
    <a href="/hub" class="start-other-link"><span class="start-other-name">Browse the Hub</span><span class="start-other-desc">Install a ready-made API from the community catalogue.</span></a>
    <div aria-hidden="true" class="start-other-rule"></div>
    <a href="/sources" class="start-other-link"><span class="start-other-name">Connect a Git repository</span><span class="start-other-desc">A scheduled Importer keeps mocks in sync with your contracts.</span></a>
    <div aria-hidden="true" class="start-other-rule"></div>
    <a href="https://microcks.io/documentation/tutorials/getting-started/" target="_blank" rel="noreferrer" class="start-other-link"><span class="start-other-name">Read the getting-started tutorial</span><span class="start-other-desc">microcks.io documentation, opens in a new tab.</span></a>
  </aside>
{/snippet}
