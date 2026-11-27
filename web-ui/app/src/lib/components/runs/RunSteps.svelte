<script lang="ts">
  import { Icon } from '@microcks/deep-ink';
  import { handoversOf, runContext, stepOffsets } from '#lib/workflows/describe.js';
  import type { ArazzoDocument, Workflow, WorkflowRun } from '#lib/workflows/types.js';
  import { operationHref } from '#lib/workflows/operation-link.js';
  import { J, phaseOf, stopText } from '#lib/runs-workflow.js';
  import RunStepCard, { lookOf } from './RunStepCard.svelte';

  interface Props {
    run: WorkflowRun;
    workflow?: Workflow;
    document?: ArazzoDocument;
  }

  let { run, workflow, document }: Props = $props();

  const offsets = $derived(stepOffsets(run));
  const ctx = $derived(runContext(run));
  const sources = $derived([...new Set((workflow?.steps ?? []).map((s) => s.sourceName))]);
  const ended = $derived(run.status !== 'RUNNING');
  const stop = $derived(stopText(run));
  const total = $derived(run.steps.length);
</script>

<section aria-labelledby="wfr-steps-h" class="wfr-steps-section">
  <div class="wfr-steps-head">
    <h2 id="wfr-steps-h" class="wfr-h2">Steps</h2>
    <span class="wfr-muted">
      {total} steps on the
      {#if sources.length === 1}<span class="wfr-mono">{sources[0]}</span> thread{:else}{sources.length} threads{/if}
      · chips between steps show the values handed over
    </span>
  </div>
  <ol aria-label="Workflow steps" class="wfr-timeline">
    {#each run.steps as step, i (step.stepId)}
      {@const def = workflow?.steps.find((s) => s.stepId === step.stepId)}
      {@const phase = phaseOf(run, i)}
      {@const look = lookOf(step, phase)}
      {@const prev = i > 0 ? run.steps[i - 1] : undefined}
      {@const prevDone = i === 0 || (prev !== undefined && phaseOf(run, i - 1) === 'done' && prev.status === 'passed')}
      {@const skipped = step.status === 'not-run' && ended}
      {@const ready = !skipped && prevDone}
      {@const handovers = def ? handoversOf(def, ctx) : []}
      {@const endsHere = look === 'pass' && step.flow.kind === 'end'}
      {@const lineOn = look === 'pass' && !endsHere}
      {@const last = i === total - 1}
      {#if handovers.length > 0 || skipped}
        <li class="wfr-tl-item" data-kind="baton">
          <span class="wfr-rail" aria-hidden="true"><span class="wfr-rail-line" data-on={ready ? '' : undefined}></span></span>
          <div class="wfr-tl-body">
            {#if skipped}
              <span class="wfr-baton-skip"><Icon name="dash" size={12} stroke-width={2.4} />Not handed over: {stop}</span>
            {:else if ready}
              <div class="wfr-baton">
                <div class="wfr-baton-chips">
                  {#each handovers as h (h.target)}
                    <span class="wfr-baton-chip" title={h.expression}>
                      <span class="wfr-mono">{h.name} <span class="wfr-faint">=</span> <strong>{J(h.value)}</strong></span>
                      <Icon name="arrow-right" size={14} class="wfr-faint" />
                      <span class="wfr-secondary">{h.target}</span>
                    </span>
                  {/each}
                </div>
                <span class="wfr-baton-from">from {handovers.map((h) => h.expression).join(' · ')}</span>
              </div>
            {:else}
              <span class="wfr-baton-wait"><span class="wfr-pulse-bar" aria-hidden="true"></span>waiting for {prev?.stepId} outputs</span>
            {/if}
          </div>
        </li>
      {/if}
      <li class="wfr-tl-item" data-kind="step">
        <span class="wfr-rail" aria-hidden="true">
          <span class="wfr-rail-top" data-on={ready || i === 0 ? '' : undefined} data-hidden={i === 0 && handovers.length === 0 ? '' : undefined}></span>
          <span class="wfr-node" data-look={look}>{i + 1}</span>
          <span class="wfr-rail-line" data-on={lineOn ? '' : undefined} data-hidden={last ? '' : undefined}></span>
        </span>
        <div class="wfr-tl-body" data-kind="step">
          <RunStepCard index={i} {step} {def} {look} {run} {document} offset={offsets[i] ?? 0} {stop} />
        </div>
      </li>
    {/each}
  </ol>
</section>
