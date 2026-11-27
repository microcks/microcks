<script lang="ts">
  import { Icon, type IconName } from '@microcks/deep-ink';
  import SplitPane from '#lib/components/catalog/SplitPane.svelte';
  import { BAND_TEXT, conformanceBand, gapList, treemapRows, type ConformanceItem, type DomainConformance, type UsageView } from './usage.js';

  let { domains, view, selected }: { domains: DomainConformance[]; view: UsageView | undefined; selected: string } = $props();

  const PROTO_ATTR: Record<ConformanceItem['protocol'], string> = { REST: 'rest', SOAP: 'soap', EVENT: 'event', gRPC: 'grpc', GRAPHQL: 'graphql', DIRECT: 'direct', A2A: 'a2a' };
  const BAND_ICON: Partial<Record<ReturnType<typeof conformanceBand>, IconName>> = { ok: 'check', partial: 'warning', risk: 'close' };
  const TREND_ICON = { up: 'trend-up', down: 'trend-down', stable: 'trend-flat', mixed: 'trend-flat' } as const;
  const ops = (n: number) => `${n} ${n === 1 ? 'op' : 'ops'}`;

  const rows = $derived(treemapRows(domains));
  const gaps = $derived(gapList(domains));
  const uid = $props.id();
  const treemapId = `${uid}-treemap`;
  const indexId = `${uid}-index`;
  const c = $derived(view?.conformance);
  const band = $derived(conformanceBand(c?.score ?? null));
  const index = $derived(c?.index ?? 0);
</script>

{#snippet treemap()}
  <section class="panel treemap-card" data-pad="lg" aria-labelledby={treemapId}>
<div class="panel-body">
    <div class="card-head">
      <h3 id={treemapId}>Conformance risk by domain</h3>
      <p>Grouped by the <code>domain</code> label · area = number of operations · the lower the score, the higher the risk</p>
    </div>
    <div class="treemap">
      {#each rows as row, r (r)}
        <div class="treemap-row" style:flex="{row.operations} 1 0px">
          {#each row.domains as d (d.domain)}
            {@const b = conformanceBand(d.score)}
            <section class="treemap-domain" style:flex="{d.operations} 1 0px" aria-label={d.label}>
              <div class="treemap-domain-head">
                <span class="treemap-domain-label">{d.label}</span>
                <span class="treemap-domain-avg" data-band={b}>{d.score === null ? 'untested' : `avg ${d.score}%`} · {ops(d.operations)}</span>
              </div>
              <ul class="treemap-services">
                {#each d.items ?? [] as it (it.serviceId)}
                  {@const ib = conformanceBand(it.score)}
                  {@const icon = BAND_ICON[ib]}
                  <li style:flex="{it.operations} 1 0px">
                    <a class="treemap-rect" data-band={ib} data-narrow={it.operations === 1 ? '' : undefined} aria-current={it.serviceId === selected ? 'true' : undefined} href="/services/{it.serviceId}">
                      <span class="treemap-rect-text">
                        <span class="treemap-rect-name">{it.name}</span>
                        <span class="sr-only">, </span>
                        <span class="treemap-rect-meta">{ops(it.operations)} · {it.protocol}</span>
                      </span>
                      <span class="treemap-rect-score" data-size={it.score === null ? 'untested' : it.operations >= 3 ? 'lg' : 'md'}>
                        {#if icon}<Icon name={icon} size={13} />{/if}
                        {#if it.score === null}
                          <span class="sr-only">, </span>untested
                        {:else}
                          <span class="sr-only">, conformance </span>{it.score}%<span class="sr-only">, {BAND_TEXT[ib]}</span>
                        {/if}
                      </span>
                    </a>
                  </li>
                {/each}
              </ul>
            </section>
          {/each}
        </div>
      {/each}
    </div>
    <div class="treemap-legend">
      <span><span class="treemap-swatch" data-band="ok" aria-hidden="true"></span>≥ 80% conformant</span>
      <span><span class="treemap-swatch" data-band="partial" aria-hidden="true"></span>50–79% partial</span>
      <span><span class="treemap-swatch" data-band="risk" aria-hidden="true"></span>&lt; 50% at risk</span>
      <span><span class="treemap-swatch" data-band="untested" aria-hidden="true"></span>never tested</span>
      <span class="treemap-legend-end">Group by <code>domain</code> · set in Administration</span>
    </div>
  </div>
  </section>
{/snippet}

{#snippet indexCard()}
  {#if view && c}
    <section class="panel index-card" data-pad="lg" aria-labelledby={indexId}>
<div class="panel-body">
      <div class="card-head">
        <h3 id={indexId}>Conformance index vs current score</h3>
        <p>{c.subject}</p>
      </div>
      <dl class="index-figures">
        <div><dt>Current score</dt><dd class="index-figure" data-band={band}>{c.score === null ? '—' : `${c.score}%`}</dd></div>
        <div><dt>Conformance index</dt><dd class="index-figure">{index}%</dd></div>
        {#if c.trend}
          <div>
            <dt>Trend</dt>
            <dd class="index-trend" data-direction={c.direction ?? 'stable'}><Icon name={TREND_ICON[c.direction ?? 'stable']} size={14} />{c.trend}</dd>
          </div>
        {/if}
      </dl>
      <div class="index-bar" role="img" aria-label="{view.scope === 'all' ? 'Average current score' : 'Current score'} {c.score ?? 0} percent, {view.scope === 'all' ? 'average ' : ''}conformance index {index} percent">
        <div class="index-bar-track">
          <span class="index-bar-index" style:width="{index}%"></span>
          <span class="index-bar-score" data-band={band} style:width="{c.score ?? 0}%"></span>
        </div>
        <div class="index-bar-axis" aria-hidden="true"><span>0%</span><span>50%</span><span>100%</span></div>
      </div>
      <dl class="index-defs">
        <div>
          <dt><span class="index-swatch" data-kind="index" aria-hidden="true"></span>Conformance index:</dt>
          <dd>the maximum score your tests can reach with the examples attached to the contract. It grows with the number of examples per operation and the coverage of dispatching rules.</dd>
        </div>
        <div>
          <dt><span class="index-swatch" data-band={band} aria-hidden="true"></span>Current score:</dt>
          <dd>the score of the latest test. It equals the index only when every operation passes.</dd>
        </div>
      </dl>
      {#if c.advice}
        <div class="index-note" role="note"><Icon name="info" size={16} /><span>{c.advice}</span></div>
      {/if}
      <div class="gap-list">
        <h4>Gap per service</h4>
        <ul>
          {#each gaps as g (g.serviceId)}
            {@const b = conformanceBand(g.score)}
            <li>
              <a class="gap-row" href="/services/{g.serviceId}" aria-current={g.serviceId === selected ? 'true' : undefined}>
                <span class="gap-proto" data-protocol={PROTO_ATTR[g.protocol]}>{g.protocol}</span>
                <span class="sr-only"> </span>
                <span class="gap-name">{g.name}</span>
                <span class="gap-bar" aria-hidden="true">
                  <span class="index-bar-index" style:width="{g.index}%"></span>
                  <span class="index-bar-score" data-band={b} style:width="{g.score ?? 0}%"></span>
                </span>
                <span class="gap-pair" data-band={b}>
                  <span class="sr-only">: score </span>
                  {#if g.score === null}<span aria-hidden="true">—</span><span class="sr-only">never tested</span>{:else}{g.score}{/if}
                  <span aria-hidden="true"> / </span><span class="sr-only">, index </span>{g.index}%
                </span>
              </a>
            </li>
          {/each}
        </ul>
      </div>
    </div>
    </section>
  {/if}
{/snippet}

<div class="conf-section">
  <div class="conf-section-head">
    <h2>Conformance</h2>
    <p>How far your implementations follow their contracts, from the latest test of each service. Always computed over all services, whatever the selection above.</p>
  </div>
  {#if view}
    <SplitPane sideWidth="360px" sideMax="none" mainWidth="560px" gap="16px" side={indexCard}>{@render treemap()}</SplitPane>
  {:else}
    {@render treemap()}
  {/if}
</div>
