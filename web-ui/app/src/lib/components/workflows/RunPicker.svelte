<script lang="ts">
  import { Icon } from '@microcks/deep-ink';
  import { runPick } from '#lib/workflows/loom-model.js';
  import { RUN_WORD } from '#lib/workflows/status.js';
  import type { WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    runs: WorkflowRun[];
    run?: WorkflowRun;
    label: string;
    caption?: string;
    onpick: (n: number) => void;
  }

  let { runs, run, label, caption, onpick }: Props = $props();

  const shown = $derived.by(() => {
    const list = runs.slice(0, 4);
    if (run && !list.some((r) => r.number === run.number)) list.push(run);
    return list;
  });
</script>

{#if runs.length > 0}
  <div role="group" aria-label={label} class="wf-run-picker">
    {#if caption}<span class="wf-run-picker-caption" aria-hidden="true">{caption}</span>{/if}
    {#each shown as r (r.number)}
      {@const pick = runPick(r)}
      <button type="button" class="wf-run-pick" aria-pressed={r.number === run?.number} data-status={r.status} onclick={() => onpick(r.number)}>
        <Icon name={r.status === 'SUCCESS' ? 'check-circle' : r.status === 'FAILURE' ? 'x-circle' : 'running'} size={13} />
        {pick.label}
        <span class="wf-run-pick-target"><span class="sr-only"> </span>{pick.target}</span>
        <span class="sr-only">, {RUN_WORD[r.status].toLowerCase()}</span>
      </button>
    {/each}
  </div>
{/if}
