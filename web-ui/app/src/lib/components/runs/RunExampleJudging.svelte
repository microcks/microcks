<script lang="ts">
  import { DataTable } from '@microcks/deep-ink';
  import RunVerdict from './RunVerdict.svelte';
  import { judgeExample } from '#lib/workflows/describe.js';
  import type { WorkflowExample, WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    run: WorkflowRun;
    example: WorkflowExample;
    pending: boolean;
  }

  let { run, example, pending }: Props = $props();

  const judged = $derived(judgeExample(run, example));
  // While the run plays, the expectations wait for its end.
  const rows = $derived(pending ? judged.rows.map((r) => ({ ...r, got: '…' })) : judged.rows);
  const unmet = $derived(pending ? [] : judged.unmet);
  type Row = (typeof rows)[number];
</script>

<section aria-labelledby="wfr-ex-h" class="wfr-card wfr-example" data-result={pending ? 'pending' : judged.matches ? 'passed' : 'failed'}>
  <div class="wfr-example-head">
    <h2 id="wfr-ex-h" class="wfr-example-title">Example <span class="wfr-example-name">{example.id}</span></h2>
    <span role="status">
      {#if pending}
        <RunVerdict tone="muted" icon="running" size={14}>Judged once the run ends</RunVerdict>
      {:else}
        <RunVerdict tone={judged.matches ? 'success' : 'danger'} icon={judged.matches ? 'check' : 'cross'} size={14}>
          {judged.matches ? 'Matches the example' : 'Does not match the example'}
        </RunVerdict>
      {/if}
    </span>
  </div>
  <DataTable
    caption={`Expectations of the example ${example.id}`}
    captionHidden
    headerCase="sentence"
    density="compact"
    class="wfr-example-table"
    rowKey={(r) => `${r.kind} ${r.expected}`}
    {rows}
    rowTone={(r) => (pending || r.met ? undefined : 'danger')}
    columns={[
      { key: 'kind', header: 'Expectation', rowHeader: true, nowrap: true },
      { key: 'expected', header: 'Expected', mono: true },
      { key: 'got', header: 'This run' },
      { key: 'result', header: 'Result' }
    ]}
  >
    {#snippet cell(r, col)}
      {#if col.key === 'kind'}<span class="wfr-example-kind">{r.kind}</span>
      {:else if col.key === 'expected'}<span class="wfr-mono-sm">{r.expected}</span>
      {:else if col.key === 'got'}<span class="wfr-mono-sm" data-tone={pending ? 'muted' : r.met ? undefined : 'danger'}>{r.got}</span>
      {:else if pending}<RunVerdict tone="muted" icon="dash">Pending</RunVerdict>
      {:else}<RunVerdict tone={r.met ? 'success' : 'danger'} icon={r.met ? 'check' : 'cross'}>{r.met ? 'Met' : 'Not met'}</RunVerdict>{/if}
    {/snippet}
  </DataTable>
  {#if unmet.length > 0}
    <ul aria-label="Unmet expectations" class="wfr-unmet">
      {#each unmet as u (u)}<li>{u}</li>{/each}
    </ul>
  {/if}
</section>
