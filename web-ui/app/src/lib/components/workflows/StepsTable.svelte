<script lang="ts">
  import { Button, DataTable, Icon, MethodTag, type DataTableColumn } from '@microcks/deep-ink';
  import { criteriaText } from '#lib/workflows/describe.js';
  import { effectiveDependencies, stepResultLine } from '#lib/workflows/loom-model.js';
  import { stepChecksOf, stepStatusOf } from '#lib/workflows/run-facts.js';
  import { formatDuration } from '#lib/workflows/time.js';
  import { STATUS_ICON } from '#lib/workflows/status.js';
  import type { Action, ArazzoDocument, Step, Workflow, WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    workflow: Workflow;
    document: ArazzoDocument;
    run?: WorkflowRun;
    loomLink?: (stepId: string) => string;
  }

  interface Row {
    n: number;
    step: Step;
    depends: string[];
  }

  let { workflow, document, run, loomLink }: Props = $props();

  const describe = (a: Action) =>
    a.type === 'retry' ? `retry every ${a.retryAfter} s, up to ${a.retryLimit} ${a.retryLimit === 1 ? 'time' : 'times'}` : 'end the workflow';

  function actionTexts(step: Step): { text: string; muted?: boolean }[] {
    const when = (a: Action) => (a.criteria?.length ? ` when ${criteriaText(a.criteria)}` : '');
    const out = [
      ...step.onSuccess.map((a) => ({ text: `On success: ${describe(a)}${when(a)}` })),
      ...step.onFailure.map((a) => ({ text: `On failure: ${describe(a)}${when(a)}` }))
    ];
    return out.length ? out : [{ text: 'Next step · ends the run on failure', muted: true }];
  }

  const deps = $derived(effectiveDependencies(workflow.steps));
  const declared = $derived(workflow.steps.some((s) => s.dependsOn?.length));
  const rows = $derived<Row[]>(workflow.steps.map((step, i) => ({ n: i + 1, step, depends: deps[i] ?? [] })));
  const columns = $derived<DataTableColumn<Row>[]>([
    { key: 'n', header: 'n°', mono: true },
    { key: 'step', header: 'stepId' },
    { key: 'op', header: 'Operation' },
    { key: 'criteria', header: 'Success criteria' },
    { key: 'actions', header: 'Actions' },
    { key: 'outputs', header: 'Outputs' },
    { key: 'result', header: run ? `Result in #${run.number}` : 'Result' },
    ...(loomLink ? [{ key: 'inspect', header: 'Inspect', headerHidden: true, align: 'end' as const }] : [])
  ]);
  const sourceOf = (step: Step) => document.sources.find((s) => s.name === step.sourceName);
</script>

<div class="wf-st-scroll">
  <DataTable
    caption="{workflow.steps.length} steps of {workflow.workflowId}{run ? `, results of run #${run.number}` : ', definition only'}"
    captionHidden
    headerCase="sentence"
    rowTone={({ step }) => {
      const st = run?.steps.find((r) => r.stepId === step.stepId)?.status;
      return st === 'failed' ? 'failed' : st === 'not-run' ? 'idle' : undefined;
    }}
    {columns}
    {rows}
    rowKey={(r) => r.step.stepId}
  >
    {#snippet cell(row, col)}
      {@const step = row.step}
      {#if col.key === 'n'}
        <span class="wf-st-strong">{row.n}</span>
      {:else if col.key === 'step'}
        <span class="wf-st-stack">
          <span class="wf-st-id">{step.stepId}</span>
          <span class="wf-st-desc">{step.description}</span>
          {#if declared}<span class="wf-st-desc">dependsOn: {row.depends.length ? row.depends.join(', ') : 'none, starts at once'}</span>{/if}
        </span>
      {:else if col.key === 'op'}
        {@const src = sourceOf(step)}
        {@const link = src?.link.status === 'linked' ? src.link : undefined}
        <span class="wf-st-stack">
          <span class="wf-st-op">
            <MethodTag size="xs" method={step.method} protocol={link?.protocol ?? (src?.type === 'asyncapi' ? 'EVENT' : 'REST')} />
            {#if link}<a href="/services/{link.serviceId}" class="wf-st-path">{step.path}</a>{:else}<span class="wf-st-path">{step.path}</span>{/if}
          </span>
          <span class="wf-st-opref">$sourceDescriptions.{step.sourceName}.{step.operationId}</span>
          {#if step.messaging}
            <span class="wf-st-opref">
              {step.messaging.action}{step.messaging.correlationId ? ` · correlationId ${step.messaging.correlationId}` : ''}{step.messaging.timeoutMs !== undefined
                ? ` · timeout ${formatDuration(step.messaging.timeoutMs)}`
                : ''}
            </span>
          {/if}
        </span>
      {:else if col.key === 'criteria'}
        <span class="wf-st-stack">
          {#each step.successCriteria as c (c.condition)}
            <span class="wf-st-crit">{c.condition}{#if c.type === 'regex'}<span class="wf-st-muted">{' '}(regex on {c.context})</span>{/if}</span>
          {/each}
        </span>
      {:else if col.key === 'actions'}
        <span class="wf-st-stack">
          {#each actionTexts(step) as a (a.text)}<span class="wf-st-act" data-muted={a.muted ? '' : undefined}>{a.text}</span>{/each}
        </span>
      {:else if col.key === 'outputs'}
        <span class="wf-st-outs">{step.outputs.length ? step.outputs.map((o) => o.name).join(', ') : '—'}</span>
      {:else if col.key === 'result'}
        {#if !run}
          <span class="wf-st-muted">No run selected</span>
        {:else}
          {@const result = run.steps.find((r) => r.stepId === step.stepId)}
          {@const status = stepStatusOf(result)}
          <span class="wf-st-stack">
            <span class="wf-st-status" data-tone={status.tone}><Icon name={STATUS_ICON[status.tone]} size={13} />{status.word}</span>
            {#each stepChecksOf(result, run) as c (c.label)}
              <span class="wf-st-check"><Icon name={STATUS_ICON[c.tone]} size={12} />{c.label} <span data-tone={c.tone}>{c.word}</span></span>
            {/each}
            {#if result && result.status !== 'not-run'}<span class="wf-st-res">{stepResultLine(result)}</span>{/if}
          </span>
        {/if}
      {:else if col.key === 'inspect' && loomLink}
        <Button size="sm" class="wf-st-inspect" href={loomLink(step.stepId)} aria-label="Show {step.stepId} on the loom">On the loom</Button>
      {/if}
    {/snippet}
  </DataTable>
</div>
