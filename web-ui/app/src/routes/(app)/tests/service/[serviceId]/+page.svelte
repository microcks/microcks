<script lang="ts">
  import { ApiBadge, Button, type ProofStatus } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import TestHistory from '#lib/components/testing/TestHistory.svelte';
  import { TEST_RESULT_WORD, runConformance, summarize, type TestResult } from '#lib/components/testing/model.js';
  import { targetFor } from '#lib/testing.js';
  import type { Service } from '#lib/types.js';

  let { data } = $props();

  const service = $derived((data.services as Service[]).find((s) => s.id === data.serviceId));
  const label = $derived(service ? `${service.name} ${service.version}`.trim() : data.serviceId);
  const runs = $derived(data.runs.map(summarize));
  const operations = $derived(service ? targetFor(service).operations.map((o) => ({ id: o.id, verb: o.verb, path: o.path })) : []);
  const latest = $derived(runs.reduce<(typeof runs)[number] | undefined>((a, r) => (!a || r.startedAt > a.startedAt ? r : a), undefined));
  const recent = $derived(runs.slice().sort((a, b) => a.startedAt.localeCompare(b.startedAt)).slice(-20));
  const passRate = $derived(recent.length ? Math.round((recent.filter((r) => r.result === 'passed').length / recent.length) * 100) : null);
  const conformance = $derived(latest ? runConformance(latest) : null);

  const tone = (pct: number): ProofStatus => (pct >= 80 ? 'success' : pct >= 50 ? 'warning' : 'danger');
  const LAST_TONE: Record<TestResult, ProofStatus> = { passed: 'success', failed: 'danger', timeout: 'warning', running: 'info' };
</script>

<PageHeader
  title="Test history"
  breadcrumb={[{ label: 'APIs & Services', href: '/services' }, { label, href: `/services/${data.serviceId}` }, { label: 'Test history' }]}
  descriptionWidth={760}
  descriptionTone="secondary"
  description={service ? `Conformance tests run against the implementations of this contract, on every endpoint. Each run replays the contract examples and checks responses against the ${service.format.split(' ')[0]} schemas.` : undefined}
  align="start"
>
  {#snippet badge()}
    {#if service}
      <a class="tests-title-badge" href="/services/{data.serviceId}" aria-label="{service.protocol} {service.name} {service.version}, open the service">
        <ApiBadge protocol={service.protocol} name={service.name} version={service.version} size="xl" />
      </a>
    {/if}
  {/snippet}
  {#snippet actions()}
    <Button variant="primary" icon="plus" href="/tests/create?service={encodeURIComponent(data.serviceId)}">New test</Button>
  {/snippet}
</PageHeader>

{#if service}
  <div class="tests-head-extra">
    <ul class="tests-kpis" aria-label="Test summary">
      {#if conformance !== null}<li><ApiBadge label="conformance" value="{conformance}%" status={tone(conformance)} /></li>{/if}
      {#if latest}<li><ApiBadge label="last test" value={TEST_RESULT_WORD[latest.result].toLowerCase()} status={LAST_TONE[latest.result]} /></li>{/if}
      {#if passRate !== null}<li><ApiBadge label="pass rate · {recent.length} runs" value="{passRate}%" status={tone(passRate)} /></li>{/if}
    </ul>
  </div>
{/if}

{#key data.serviceId}
  <TestHistory {runs} {operations} serviceId={data.serviceId} />
{/key}
