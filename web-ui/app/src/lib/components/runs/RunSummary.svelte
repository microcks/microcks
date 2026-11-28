<script lang="ts">
  import { isAvailable, unavailableReason } from '#lib/workflows/availability.js';
  import { DataTable, FactRow, Facts, Icon } from '@microcks/deep-ink';
  import { criteriaState } from '#lib/workflows/criteria-state.js';
  import { endLabel, stepsTally, stoppedStep, verdictExplanation } from '#lib/workflows/describe.js';
  import { formatDuration } from '#lib/workflows/time.js';
  import { workflowPath } from '#lib/workflows/paths.js';
  import type { ArazzoDocument, Workflow, WorkflowRun } from '#lib/workflows/types.js';
  import { operationHref } from '#lib/workflows/operation-link.js';
  import { J } from '#lib/runs-workflow.js';

  interface Props {
    run: WorkflowRun;
    totals: { calls: number; ms: number };
    workflow?: Workflow;
    document?: ArazzoDocument;
  }

  let { run, totals, workflow, document }: Props = $props();

  const passed = $derived(run.status === 'SUCCESS');
  const stopped = $derived(stoppedStep(run));
  const outputs = $derived(Object.entries(run.outputs));
  const expressions = $derived(new Map((workflow?.outputs ?? []).map((o) => [o.name, o.expression])));
  const opStep = $derived(stopped ?? [...run.steps].reverse().find((s) => s.status !== 'not-run'));
  const opDef = $derived(workflow?.steps.find((s) => s.stepId === opStep?.stepId));

  /** The verdict sentence; a run ended early by an onSuccess end says which criterion held and what it spared. */
  const explanation = $derived.by(() => {
    if (run.status !== 'SUCCESS' || !stopped) return verdictExplanation(run);
    const action = workflow?.steps.find((s) => s.stepId === stopped.stepId)?.onSuccess.find((a) => a.type === 'end');
    const held = action?.criteria?.map((c) => c.condition).join(' and ');
    const name = run.workflowId === 'restock-pastry' ? run.outputs['pastryName'] : undefined;
    return `${stopped.stepId}'s onSuccess end applied${held ? `: ${held} held` : ''}${typeof name === 'string' ? `, so ${name} needs no restock` : ''}. The run ended with SUCCESS; its outputs come from the steps it reached.`;
  });

  const band = $derived(
    run.steps.map((s) => {
      const notRun = s.status === 'not-run';
      const criteria = criteriaState(s);
      const schema = s.schema.state;
      const word = notRun
        ? 'not run'
        : `${criteria === 'held' ? 'criteria held' : criteria === 'failed' ? 'criteria failed' : 'criteria not evaluated'} · ${schema === 'pass' ? 'schema passed' : schema === 'fail' ? 'schema failed' : schema === 'na' ? 'schema n/a' : 'schema off'}`;
      return { id: s.stepId, status: s.status, word, criteria: criteria === 'held' ? 'pass' : criteria === 'failed' ? 'fail' : 'none', schema: notRun ? 'none' : schema };
    })
  );
  type Out = [string, unknown];
</script>

<section aria-label="Run summary" class="wfr-card wfr-summary" data-result={passed ? 'passed' : 'failed'}>
  <div class="wfr-summary-head">
    <div class="wfr-summary-text">
      <h2 class="wfr-verdict-title" id="wfr-verdict-h" tabindex="-1">
        <Icon name={passed ? 'check' : 'cross'} size={20} stroke-width={2.6} />
        {passed ? 'Run passed' : 'Run failed'}
      </h2>
      <span class="wfr-summary-end">{endLabel(run)}</span>
      <span class="wfr-summary-explain">{explanation}</span>
    </div>
    <Facts layout="columns" class="wfr-summary-facts">
      <FactRow term="Duration">{#snippet children()}<span class="wfr-stat">{formatDuration(totals.ms)}</span>{/snippet}</FactRow>
      <FactRow term="Calls">{#snippet children()}<span class="wfr-stat">{totals.calls}</span>{/snippet}</FactRow>
      <FactRow term="Steps">{#snippet children()}<span class="wfr-stat-text">{stepsTally(run)}</span>{/snippet}</FactRow>
    </Facts>
  </div>

  <div class="wfr-band-wrap">
    <span class="wfr-band-legend">Steps band · upper bar = success criteria, lower bar = schema validation</span>
    <div class="wfr-band" role="img" aria-label={`Steps band: ${band.map((b) => `${b.id} ${b.word}`).join('; ')}`}>
      {#each band as b (b.id)}
        <div class="wfr-band-step" data-status={b.status}>
          <span class="wfr-band-bar" data-part="criteria" data-state={b.criteria}></span>
          <span class="wfr-band-bar" data-part="schema" data-state={b.schema}></span>
          <span class="wfr-band-id">
            <Icon
              name={b.status === 'passed' ? 'check' : b.status === 'failed' ? 'cross' : 'circle'}
              size={12}
              stroke-width={b.status === 'not-run' ? 2 : 2.8}
              stroke-dasharray={b.status === 'not-run' ? '3 3' : undefined}
            />
            <span class="wfr-mono" title={b.id}>{b.id}</span>
          </span>
          <span class="wfr-band-word">{b.word}</span>
        </div>
      {/each}
    </div>
  </div>

  <div class="wfr-block">
    <h3 class="wfr-h3">Workflow outputs</h3>
    {#if outputs.length > 0}
      <DataTable
        caption="Workflow outputs"
        captionHidden
        headerCase="sentence"
        density="compact"
        class="wfr-outputs-table"
        rowKey={(r) => r[0]}
        rows={outputs}
        columns={[
          { key: 'output', header: 'Output' },
          { key: 'expression', header: 'Runtime expression' },
          { key: 'value', header: 'Value' }
        ]}
      >
        {#snippet cell(r, col)}
          {#if col.key === 'output'}<span class="wfr-mono-strong">{r[0]}</span>
          {:else if col.key === 'expression'}<span class="wfr-mono-secondary">{expressions.get(r[0]) ?? ''}</span>
          {:else}<span class="wfr-mono-strong">{J(r[1])}</span>{/if}
        {/snippet}
      </DataTable>
    {:else}
      <p class="wfr-muted">{passed ? 'None.' : `None: the workflow stopped at ${stopped?.stepId ?? 'a step'} and produced no outputs.`}</p>
    {/if}
  </div>

  <div class="wfr-actions">
    {#if run.relatedTestRunId}
      <a href={`/tests/runner/${run.relatedTestRunId}`} class="wfr-action" data-tone="danger">
        <Icon name="warning" size={14} stroke-width={2.4} />
        Same bug in test run #{run.relatedTestRunId}
      </a>
    {/if}
    {#if run.coherence[0]}
      {#if isAvailable('ai-copilot', run.documentId)}
        <a href={`/services?hint=${encodeURIComponent(run.coherence[0].copilotHint)}`} class="wfr-action" data-tone="action">
          <Icon name="sparkles" size={14} />
          Generate the example with the AI Copilot
        </a>
      {:else}
        <button type="button" class="wfr-action" data-tone="action" disabled title={unavailableReason('ai-copilot', run.documentId)}>
          <Icon name="sparkles" size={14} />
          Generate the example with the AI Copilot
        </button>
      {/if}
    {/if}
    {#if opDef}
      <a href={operationHref(document, opDef)} class="wfr-action">Open operation<span class="wfr-action-op">{opDef.method} {opDef.path}</span></a>
    {/if}
    <a href={workflowPath(run.documentId, run.workflowId, 'loom', { run: String(run.number), step: stopped?.stepId })} class="wfr-action">Back to workflow</a>
  </div>
</section>
