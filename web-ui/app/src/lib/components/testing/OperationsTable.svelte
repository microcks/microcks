<script lang="ts">
  import { ApiBadge, LiveIndicator, MethodTag } from '@microcks/deep-ink';
  import { formatCount, formatTrend, type UsageView } from './usage.js';

  let { view }: { view: UsageView } = $props();

  const table = $derived(view.table);
  const titleId = $props.id();
  const one = $derived(view.scope !== 'all');
  const max = $derived(Math.max(1, ...table.rows.map((r) => r.calls)));
  const trendDir = (pct: number) => (pct > 0 ? 'up' : pct < 0 ? 'down' : 'flat');

  function splitVersion(name: string): [string, string | undefined] {
    const at = name.lastIndexOf(' ');
    return at > 0 ? [name.slice(0, at), name.slice(at + 1)] : [name, undefined];
  }
</script>

<section class="panel us-table-card" data-pad="lg" aria-labelledby={titleId}>
<div class="panel-body">
  <div class="us-table-head">
    <h2 id={titleId}>{table.title}</h2>
    <span>{table.subtitle}</span>
  </div>
  <div class="table-wrap us-table-wrap">
    <table class="table us-ops" aria-labelledby={titleId}>
      <thead>
        <tr>
          <th scope="col">{table.firstColumn}</th>
          <th scope="col">{table.secondColumn}</th>
          <th scope="col" data-align="end">Invocations</th>
          <th scope="col">Share</th>
          <th scope="col" data-align="end">vs previous</th>
          <th scope="col">Last call</th>
        </tr>
      </thead>
      <tbody>
        {#each table.rows as r (r.id)}
          <tr>
            <th scope="row">
              {#if one}
                <a class="us-ops-op" href="/services/{view.scope}/operations/{encodeURIComponent(r.id)}">
                  {#if r.method}<MethodTag method={r.method} protocol={r.protocol ?? 'REST'} />{/if}
                  <span class="us-ops-path">{r.name}</span>
                </a>
              {:else}
                {@const [name, version] = splitVersion(r.name)}
                <a class="us-ops-svc" href="/services/{r.id}"><ApiBadge protocol={r.protocol ?? 'REST'} {name} {version} size="sm" /></a>
              {/if}
            </th>
            <td class="us-ops-detail">{r.detail}</td>
            <td class="us-ops-calls" data-align="end">{formatCount(r.calls)}</td>
            <td>
              <span class="us-ops-share">
                <span class="us-ops-share-track" aria-hidden="true"><span data-max={r.calls === max ? '' : undefined} style:width="{r.width}%"></span></span>
                <span class="us-ops-share-pct">{r.share}%</span>
              </span>
            </td>
            <td class="us-ops-trend" data-align="end" data-direction={trendDir(r.trend)}>{formatTrend(r.trend)}</td>
            <td class="us-ops-last" data-live={r.live ? '' : undefined}>
              {#if r.live}<LiveIndicator class="us-ops-live" variant="dot" />{/if}
              {r.last}
              {#if r.live}<span class="sr-only">, live</span>{/if}
            </td>
          </tr>
        {/each}
      </tbody>
      <tfoot>
        <tr>
          <th scope="row">Total</th>
          <td></td>
          <td class="us-ops-calls" data-align="end">{formatCount(table.total)}</td>
          <td class="us-ops-share-pct">100%</td>
          <td class="us-ops-trend" data-align="end" data-direction={trendDir(table.trend)}>{formatTrend(table.trend)}</td>
          <td></td>
        </tr>
      </tfoot>
    </table>
  </div>
  {#if table.note}<p class="us-table-note">{table.note}</p>{/if}
</div>
</section>
