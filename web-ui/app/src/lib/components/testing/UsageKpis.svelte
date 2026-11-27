<script lang="ts">
  import { Icon, LiveIndicator } from '@microcks/deep-ink';
  import StatCard from '#lib/components/catalog/StatCard.svelte';
  import StatGrid from '#lib/components/catalog/StatGrid.svelte';
  import { formatCount, type UsageView } from './usage.js';

  let { view }: { view: UsageView } = $props();

  const kpis = $derived(view.kpis);
  const conformance = $derived(view.conformance);
  const direction = $derived(kpis.growth > 0 ? 'up' : kpis.growth < 0 ? 'down' : 'flat');
  const tone = (score: number | null) => (score === null ? 'neutral' : score >= 80 ? 'success' : score >= 50 ? 'warning' : 'danger');
</script>

<StatGrid label="Key figures">
  <StatCard title="Mock invocations · {view.periodLabel}" value={formatCount(kpis.total)}>
    <p class="kpi-trend" data-direction={direction}>
      <Icon name="trend-{direction}" size={14} />
      {kpis.trendText}
    </p>
  </StatCard>
  <StatCard title={kpis.peakTitle} value={formatCount(kpis.peak)}>
    <p>{kpis.peakWhen}</p>
  </StatCard>
  <StatCard title="Last request" value={kpis.lastCallAgo}>
    {#snippet action()}<LiveIndicator size="sm" variant="inline" />{/snippet}
    <a class="kpi-path" href={view.scope === 'all' ? '/traces' : `/traces?service=${view.scope}`}>{kpis.lastCall}</a>
  </StatCard>
  <StatCard title={conformance.title} value={conformance.score === null ? 'Never tested' : `${conformance.score}%`} tone={tone(conformance.score)}>
    <p>{conformance.subtitle}</p>
  </StatCard>
</StatGrid>
