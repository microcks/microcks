<script lang="ts">
  import { Button, Callout } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import WorkflowRunView from '#lib/components/runs/WorkflowRunView.svelte';

  import { invalidateAll } from '$app/navigation';

  let { data } = $props();

  /** A run Microcks is still executing: read it again until the minion reports its outcome. */
  const progress = $derived(data.payload ? `${data.payload.run.status}/${data.payload.run.steps.filter((s) => s.status !== 'not-run').length}` : '');
  $effect(() => {
    if (data.payload?.run.status !== 'RUNNING' || (data.live && data.payload.demo !== false)) return;
    const timer = setTimeout(() => void invalidateAll(), 1000);
    return () => clearTimeout(timer);
  });
</script>

<svelte:head><title>Workflow run · Microcks</title></svelte:head>

{#if data.payload}
  {#key `${data.payload.run.id}/${data.live}/${progress}`}
    <WorkflowRunView payload={data.payload} live={data.live} />
  {/key}
{:else}
  <PageHeader title="Workflow run" breadcrumb={[{ label: 'Workflows', href: '/workflows' }, { label: 'Workflow run' }]} />
  <Callout tone="danger" title="This run could not be loaded" live="polite">{data.error}</Callout>
  <p><Button href="/workflows">Back to workflows</Button></p>
{/if}
