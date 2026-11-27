<script lang="ts">
  import BarChart from './BarChart.svelte';
  import { formatCount, type UsageView } from './usage.js';

  let { view }: { view: UsageView } = $props();

  const chart = $derived(view.chart);
  const titleId = $props.id();
  let picked = $state<number | undefined>();
  const sel = $derived(picked ?? chart.defaultSelected);
  const point = $derived(chart.points[sel]);
  const inProgress = $derived(view.period === 'today' && sel === chart.defaultSelected);
  const compact = $derived(chart.points.length > 24);
</script>

<section class="panel us-chart-card" data-pad="lg" aria-labelledby={titleId}>
<div class="panel-body">
  <div class="us-chart-head">
    <div class="us-chart-title">
      <h2 id={titleId}>{chart.title}</h2>
      <span>{chart.subtitle}</span>
    </div>
    <span class="us-chart-selection" aria-live="polite">
      {#if point}
        <span class="us-chart-selection-label">{point.name}{inProgress ? ' (in progress)' : ''}</span>
        <span class="us-chart-selection-value">{formatCount(point.value)} invocations</span>
      {/if}
    </span>
  </div>
  <BarChart
    label="{chart.summary} One bar per {chart.unit}, use the arrow keys to move between bars."
    bars={chart.points.map((p, i) => ({ id: String(i), value: p.value, description: `${p.name}: ${formatCount(p.value)} invocations`, label: p.tick, future: p.future }))}
    max={chart.max}
    selectedId={String(sel)}
    onSelect={(id) => (picked = Number(id))}
    selection="fill"
    height={200}
    gap={compact ? 3 : 6}
    minHeight={1}
    ticks={[formatCount(chart.max), formatCount(Math.round(chart.max / 2)), '0']}
    grid={[0, 0.5]}
    table={{ caption: chart.title, columns: [chart.unit === 'hour' ? 'Hour' : 'Day', 'Invocations'], rows: chart.points.map((p) => [p.name, p.future ? 'not yet' : formatCount(p.value)]) }}
  />
</div>
</section>
