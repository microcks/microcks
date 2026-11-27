<script lang="ts">
  import { goto } from '$app/navigation';
  import { ApiBadge, Callout, Segmented, Select } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import SplitPane from '#lib/components/catalog/SplitPane.svelte';
  import ConformanceSection from '#lib/components/testing/ConformanceSection.svelte';
  import HourlyChart from '#lib/components/testing/HourlyChart.svelte';
  import OperationsTable from '#lib/components/testing/OperationsTable.svelte';
  import UsageHeatmap from '#lib/components/testing/UsageHeatmap.svelte';
  import UsageKpis from '#lib/components/testing/UsageKpis.svelte';
  import { USAGE_PERIODS, buildUsageView, type UsagePeriod } from '#lib/components/testing/usage.js';
  import type { Service } from '#lib/types.js';

  let { data } = $props();

  let period = $state<UsagePeriod>('today');
  const selectId = $props.id();
  const view = $derived(buildUsageView(data.usage, period));
  const service = $derived((data.services as Service[]).find((s) => s.id === data.scope));
  const scopes = $derived([{ id: 'all', label: 'All services' }, ...(data.services as Service[]).map((s) => ({ id: s.id, label: `${s.name} ${s.version}` }))]);
</script>

<svelte:head><title>Usage & conformance · Microcks</title></svelte:head>

<PageHeader title="Usage & conformance" size="lg">
  {#snippet children()}
    <span class="us-scope">
      {#if service}
        <a class="us-scope-badge" href="/services/{service.id}"><ApiBadge protocol={service.protocol} name={service.name} version={service.version} size="sm" /></a>
      {/if}
      <span>{view.scopeLine}</span>
    </span>
  {/snippet}
  {#snippet actions()}
    <div class="us-controls">
      <div class="us-control">
        <label class="section-label" for={selectId}>Service</label>
        <Select id={selectId} value={data.scope} onchange={(e: Event) => goto((e.currentTarget as HTMLSelectElement).value === 'all' ? '/metrics' : `/metrics/${(e.currentTarget as HTMLSelectElement).value}`)}>
          {#each scopes as o (o.id)}<option value={o.id}>{o.label}</option>{/each}
        </Select>
      </div>
      <div class="us-control">
        <span class="section-label" aria-hidden="true">Period</span>
        <Segmented label="Period" options={USAGE_PERIODS} value={period} onchange={(id: string) => (period = id as UsagePeriod)} />
      </div>
    </div>
  {/snippet}
</PageHeader>

<div class="us-page">
  {#if view.demo}
    <Callout tone="info" title="Demo data">Invocation counters are generated placeholders: Microcks keeps daily counters per service and no traffic has reached this mock yet. Scores come from the latest test runs, simulated or demo.</Callout>
  {/if}
  <UsageKpis {view} />
  {#key `${view.scope}-${view.period}`}<HourlyChart {view} />{/key}
  <SplitPane sideWidth="380px" sideMax="560px" mainWidth="480px" gap="16px" keepSideMax>
    {#snippet side()}{#key view.scope}<UsageHeatmap {view} />{/key}{/snippet}
    <OperationsTable {view} />
  </SplitPane>
  <ConformanceSection domains={data.domains} {view} selected={data.scope} />
</div>
