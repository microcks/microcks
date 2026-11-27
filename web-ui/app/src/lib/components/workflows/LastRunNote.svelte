<script lang="ts">
  import { Button, Icon } from '@microcks/deep-ink';
  import { stoppedStep } from '#lib/workflows/describe.js';
  import { failureText } from '#lib/workflows/header.js';
  import { runPath, workflowPath } from '#lib/workflows/paths.js';
  import type { ArazzoDocument, Workflow, WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    document: ArazzoDocument;
    workflow: Workflow;
    run: WorkflowRun;
  }

  let { document, workflow, run }: Props = $props();

  const stopped = $derived(stoppedStep(run));
  const index = $derived(stopped ? workflow.steps.findIndex((s) => s.stepId === stopped.stepId) + 1 : 0);
  const attempts = $derived(stopped?.attempts.length ?? 0);
  const text = $derived(failureText(run, workflow));
</script>

{#if run.status === 'FAILURE' && stopped}
  <div role="note" class="wf-last-note">
    <span class="wf-last-note-title">
      <Icon name="x-circle" size={16} />
      Run #{run.number} · step {index}, {stopped.stepId}, failed{attempts > 1 ? ` after ${attempts} attempts` : ''}
    </span>
    <span class="wf-last-note-text">
      {#if text.kind === 'plain'}
        {text.text}
      {:else}
        Its success criteria held (<span class="wf-note-mono">{text.conditions}</span>), but schema validation failed{#if text.field && text.expected}: {text.who}
          answers <span class="wf-note-mono">{text.field}</span> as the string {text.value} where the {text.schema} schema expects a {text.expected}.{:else}: {text.message}.{/if}{text.end}
      {/if}
    </span>
    <span class="wf-last-note-actions">
      <Button size="sm" href={workflowPath(document.id, workflow.workflowId, 'loom', { run: String(run.number), step: stopped.stepId })}>Inspect step {index}</Button>
      <a href={runPath(run.id)} class="wf-note-link">Open run #{run.number}</a>
    </span>
  </div>
{/if}
