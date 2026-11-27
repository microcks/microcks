<script lang="ts">
  import { Button, Icon } from '@microcks/deep-ink';
  import { formatRelative } from '#lib/format.js';
  import { stoppedStep } from '#lib/workflows/describe.js';
  import { buildLoomProps, lensOf } from '#lib/workflows/loom-model.js';
  import { workflowPath } from '#lib/workflows/paths.js';
  import { runSummary } from '#lib/workflows/run-facts.js';
  import type { ArazzoDocument, Session, Workflow, WorkflowRun } from '#lib/workflows/types.js';
  import Loom from './Loom.svelte';
  import RunPicker from './RunPicker.svelte';
  import StepInspector from './StepInspector.svelte';

  interface Props {
    workflow: Workflow;
    document: ArazzoDocument;
    siblings: Workflow[];
    runs: WorkflowRun[];
    run?: WorkflowRun;
    step?: string;
    sessions?: Session[];
    serviceLabel: (serviceId: string) => string | undefined;
    onpickrun: (n: number) => void;
    onselectstep: (stepId: string | undefined) => void;
  }

  let { workflow, document, siblings, runs, run, step, sessions, serviceLabel, onpickrun, onselectstep }: Props = $props();

  let lensId = $state<string | undefined>();

  const loomProps = $derived(buildLoomProps(workflow, document, run, step, serviceLabel));
  const lens = $derived(lensId ? lensOf(lensId, workflow, run) : undefined);
  const selected = $derived(workflow.steps.find((s) => s.stepId === step));
  const failedStepId = $derived(run?.status === 'FAILURE' ? stoppedStep(run)?.stepId : undefined);
  const summary = $derived(run ? runSummary(run, workflow, formatRelative(run.startedAt), sessions) : undefined);
  const used = $derived([...new Set(workflow.steps.map((s) => s.sourceName))]);
  const usedBy = $derived(
    siblings.filter((w) => w.documentId === document.id && w.workflowId !== workflow.workflowId && w.steps.some((s) => !used.includes(s.sourceName)))
  );
  const unused = $derived([
    ...new Set([...usedBy.flatMap((w) => w.steps.map((s) => s.sourceName)), ...document.sources.map((s) => s.name)].filter((n) => !used.includes(n)))
  ]);
  const sourceNames = $derived(loomProps.sources.map((s) => s.name).join(', '));
  const trace = (id: string | undefined) => (lensId = id && id === lensId ? undefined : id);
  const join = (i: number, n: number) => (i === 0 ? '' : i === n - 1 ? ' and ' : ', ');

  function focusDeferred(id: string, tries = 10) {
    requestAnimationFrame(() => {
      const el = window.document.getElementById(id);
      if (el) el.focus();
      else if (tries > 0) focusDeferred(id, tries - 1);
    });
  }
</script>

<div class="wf-loom-tab">
  <section aria-label="Workflow loom" class="wf-loom">
    <div class="wf-loom-toolbar">
      <RunPicker {runs} {run} onpick={onpickrun} label="Show results and resolved values from a run" caption="Results & values from" />
      <div aria-label="Legend" role="group" class="wf-loom-legend">
        <span><span aria-hidden="true" class="wf-loom-legend-warp"></span>warp: {sourceNames}</span>
        <span><span aria-hidden="true" class="wf-loom-legend-thread"></span>data thread</span>
        <span><Icon name="end" size={14} />early end</span>
        <span><Icon name="loop" size={14} />retry</span>
      </div>
    </div>

    {#if summary}
      <div class="wf-run-summary" data-tone={summary.tone}>
        <span class="wf-run-summary-title">
          <Icon name={summary.tone === 'success' ? 'check-circle' : summary.tone === 'danger' ? 'x-circle' : 'running'} size={14} />{summary.title}
        </span>
        <span class="wf-run-summary-text">{summary.text}</span>
        <span class="wf-run-summary-end">{summary.end}</span>
      </div>
    {:else}
      <div class="wf-run-summary" data-tone="info">
        <span class="wf-run-summary-text">No run yet: the loom shows the definition of the workflow. Run it to see the results on its steps.</span>
      </div>
    {/if}

    <Loom {...loomProps} lens={lensId} onstepselect={onselectstep} onlensselect={(id) => (lensId = id)} />

    {#if lens}
      <div role="region" aria-label="Expression lens" class="wf-lens-on">
        <div class="wf-lens-head">
          <span class="wf-lens-name"><Icon name="search" size={15} />Expression lens</span>
          <span class="wf-lens-expr">{lens.expression}</span>
          <span class="wf-lens-value" data-tone={lens.missing ? 'bad' : undefined}>= {lens.value}</span>
          {#if run}<span class="wf-lens-run">in run #{run.number}</span>{/if}
          <Button size="sm" class="wf-lens-clear" onclick={() => (lensId = undefined)}>Clear lens</Button>
        </div>
        {#if lens.warn}<span class="wf-lens-warn"><Icon name="warning" size={14} />{lens.warn}</span>{/if}
        <div class="wf-lens-produced"><span class="wf-lens-muted">Produced by</span><span class="wf-lens-mono">{lens.producedBy}</span></div>
        <div class="wf-lens-uses">
          <span class="wf-lens-muted">Consumed by {lens.uses.length} {lens.uses.length === 1 ? 'place' : 'places'}</span>
          <ul>
            {#each lens.uses as u (`${u.who}-${u.what}`)}
              <li><span class="wf-lens-who">{u.who}</span><span class="wf-lens-what">{u.what}</span><span class="wf-lens-val" data-tone={u.tone}>{u.value}</span></li>
            {/each}
          </ul>
        </div>
      </div>
    {:else}
      <p class="wf-lens-off"><Icon name="search" size={14} />Expression lens: select an input, an output or a thread label to trace where its value is produced and consumed.</p>
    {/if}

    {#if unused.length}
      <p class="wf-loom-foot">
        This workflow weaves {used.length} of the {document.sources.length} sourceDescriptions of <span class="wf-loom-mono">{document.file}</span>.
        {#each unused as n, i (n)}{join(i, unused.length)}<span class="wf-loom-mono">{n}</span>{/each}
        {unused.length === 1 ? 'is' : 'are'} used by
        {#if usedBy.length}
          {#each usedBy as w, k (w.workflowId)}{join(k, usedBy.length)}<a href={workflowPath(document.id, w.workflowId, 'loom')}>{w.workflowId}</a>{/each}
        {:else}the other workflows{/if}.
      </p>
    {/if}
  </section>

  <StepInspector
    {workflow}
    {document}
    {run}
    step={selected}
    {failedStepId}
    {serviceLabel}
    lens={lensId}
    ontrace={trace}
    onselectfailed={() => {
      if (!failedStepId) return;
      onselectstep(failedStepId);
      focusDeferred('wf-ins-title');
    }}
    onclose={() => onselectstep(undefined)}
  />
</div>
