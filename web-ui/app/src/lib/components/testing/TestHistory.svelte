<script lang="ts">
  import { Button, DataTable, EmptyState, Icon, Segmented, Select, type DataTableColumn } from '@microcks/deep-ink';
  import { formatRelative } from '#lib/format.js';
  import TestTimeline from './TestTimeline.svelte';
  import { TEST_RESULT_ICON, TEST_RESULT_WORD, filterRuns, fmtDuration, shortDate, testRunHref, type TestRunSummary } from './model.js';

  interface Props {
    runs: TestRunSummary[];
    operations: { id: string; verb: string; path: string }[];
    serviceId: string;
  }

  let { runs, operations, serviceId }: Props = $props();

  const LIMIT = 12;
  const OP_WORD = { pass: 'passed', fail: 'failed', none: 'no result' } as const;

  let result = $state('all');
  let runner = $state('all');
  let environment = $state('all');
  let showAll = $state(false);
  let picked = $state<string>();

  const runners = $derived([...new Set(runs.map((r) => r.runner))]);
  const environments = $derived.by(() => {
    const seen = new Map<string, string>();
    for (const r of runs) if (!seen.has(r.environment)) seen.set(r.environment, r.endpoint);
    return [...seen];
  });
  const isFiltered = $derived(result !== 'all' || runner !== 'all' || environment !== 'all');
  const filtered = $derived(filterRuns(runs, { result, runner, environment }));
  const matchIds = $derived(new Set(filtered.map((r) => r.id)));
  const visible = $derived(showAll || isFiltered ? filtered : filtered.slice(0, LIMIT));
  const sel = $derived(picked ?? runs[0]?.id);
  const passedCount = $derived(runs.filter((r) => r.result === 'passed').length);
  const oldest = $derived(runs.reduce((min, r) => Math.min(min, r.number), Number.POSITIVE_INFINITY));
  const hasDemo = $derived(runs.some((r) => r.demo));
  const hasSimulated = $derived(runs.some((r) => r.simulated));

  function reset() {
    result = 'all';
    runner = 'all';
    environment = 'all';
    showAll = false;
  }

  const columns: DataTableColumn<TestRunSummary>[] = [
    { key: 'id', header: 'Run' },
    { key: 'startedAt', header: 'When', nowrap: true },
    { key: 'endpoint', header: 'Endpoint' },
    { key: 'runner', header: 'Runner', nowrap: true },
    { key: 'durationMs', header: 'Duration', align: 'end', nowrap: true },
    { key: 'result', header: 'Result' },
    { key: 'operations', header: 'Operations' },
    { key: 'samples', header: 'Samples', align: 'end', nowrap: true },
    { key: 'actions', header: 'Actions', align: 'end', nowrap: true }
  ];
</script>

{#snippet cell(r: TestRunSummary, c: DataTableColumn<TestRunSummary>)}
  {#if c.key === 'id'}
    <button type="button" class="tests-run-id" aria-label="Show run #{r.number} in the timeline" onclick={() => (picked = r.id)}>#{r.number}</button>
    {#if r.simulated}<span class="tests-origin" title="Recorded from Run a test: no test engine is connected, the result is simulated">simulated</span>{/if}
  {:else if c.key === 'startedAt'}
    <span class="tests-when"><span>{formatRelative(r.startedAt)}</span><span class="tests-when-date">{shortDate(r.startedAt)}</span></span>
  {:else if c.key === 'endpoint'}
    <span class="tests-endpoint"><span class="tests-env" data-env={r.environment}>{r.environment}</span><span class="tests-endpoint-url">{r.endpoint}</span></span>
  {:else if c.key === 'runner'}
    <span class="tests-runner">{r.runner}</span>
  {:else if c.key === 'durationMs'}
    <span class="tests-duration" data-result={r.result}>{fmtDuration(r.durationMs)}</span>
  {:else if c.key === 'result'}
    <span class="tests-result" data-result={r.result}>
      <span class="tests-result-word"><Icon name={TEST_RESULT_ICON[r.result]} size={14} stroke-width={r.result === 'timeout' ? 2.2 : 2.6} />{TEST_RESULT_WORD[r.result]}</span>
      {#if r.reason}<span class="tests-result-reason">{r.reason}</span>{/if}
    </span>
  {:else if c.key === 'operations'}
    <span class="tests-band" role="img" aria-label={operations.map((op, i) => `${op.verb} ${op.path} ${OP_WORD[r.operations?.[i] ?? 'none']}`).join(', ')}>
      {#each operations as op, i (op.id)}
        <span data-outcome={r.operations?.[i] ?? 'none'} title="{op.verb} {op.path}: {OP_WORD[r.operations?.[i] ?? 'none']}"></span>
      {/each}
    </span>
  {:else if c.key === 'samples'}
    <span class="tests-samples">{r.passed}/{r.total}</span>
  {:else if c.key === 'actions'}
    <span class="tests-actions">
      <Button size="sm" variant="secondary" href={testRunHref(r)} aria-label="Open run #{r.number}">Open</Button>
      <Button size="sm" variant="ghost" icon="reset" iconOnly href="/tests/create?service={encodeURIComponent(serviceId)}&from={encodeURIComponent(r.id)}" aria-label="Relaunch run #{r.number}" title="Relaunch with the same configuration" />
    </span>
  {/if}
{/snippet}

{#if runs.length === 0}
  <EmptyState title="No test yet" headingLevel={2}>
    <p>Run a first test to start the conformance history of this service.</p>
  </EmptyState>
{:else}
  <div class="stack tests-history">
    <TestTimeline {runs} {operations} selectedId={sel} onSelect={(id) => (picked = id)} matches={(r) => matchIds.has(r.id)} filtered={isFiltered} />

    <div class="tests-filters" role="search" aria-label="Filter runs">
      <Segmented
        label="Result"
        variant="joined"
        value={result}
        onchange={(id) => {
          result = id;
          showAll = false;
        }}
        options={[
          { id: 'all', label: 'All', count: runs.length },
          { id: 'passed', label: 'Passed', count: passedCount },
          { id: 'failed', label: 'Failed', count: runs.length - passedCount }
        ]}
      />
      <span class="tests-filter">
        <label for="tests-runner">Runner</label>
        <Select id="tests-runner" size="sm" bind:value={runner} onchange={() => (showAll = false)}>
          <option value="all">All runners</option>
          {#each runners as r (r)}<option value={r}>{r}</option>{/each}
        </Select>
      </span>
      <span class="tests-filter">
        <label for="tests-endpoint">Endpoint</label>
        <Select id="tests-endpoint" size="sm" bind:value={environment} onchange={() => (showAll = false)}>
          <option value="all">All endpoints</option>
          {#each environments as [env, url] (env)}<option value={env}>{env} · {url.replace(/^https?:\/\//, '')}</option>{/each}
        </Select>
      </span>
      {#if isFiltered}<Button variant="link" onclick={reset}>Reset filters</Button>{/if}
      <p role="status" class="tests-count">Showing {visible.length} of {runs.length} runs{isFiltered ? ` · ${filtered.length} match the filters` : ''}</p>
    </div>

    <section class="tests-table" aria-label="Test runs">
      <DataTable caption="Test runs" captionHidden {columns} rows={visible} rowKey={(r) => r.id} selectedKey={sel} emptyText="No run matches these filters." {cell} />
      {#if visible.length === 0}
        <p class="tests-table-empty"><Button variant="link" onclick={reset}>Reset filters</Button></p>
      {/if}
      {#if !isFiltered && filtered.length > LIMIT}
        <div class="tests-table-more">
          <Button size="sm" variant="secondary" onclick={() => (showAll = !showAll)} aria-expanded={showAll}>
            {showAll ? `Show the ${LIMIT} latest only` : `Show ${filtered.length - LIMIT} older runs`}
          </Button>
        </div>
      {/if}
    </section>
    {#if Number.isFinite(oldest) && oldest > 1}
      <p class="tests-footnote">Runs are kept with their results and request/response exchanges. Test #1 to #{oldest - 1} are older than the {runs.length} runs listed here.</p>
    {/if}
    {#if hasDemo || hasSimulated}
      <p class="tests-footnote" data-demo>
        <strong>Demo data.</strong>
        {#if hasDemo}Runs without a “simulated” tag are placeholders generated for this prototype: no test engine has produced them.{/if}
        {#if hasSimulated} Runs tagged “simulated” come from Run a test and are not executed either.{/if}
      </p>
    {/if}
  </div>
{/if}
