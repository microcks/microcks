<script lang="ts">
  import { Button, Callout } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import WorkflowRunView from '#lib/components/runs/WorkflowRunView.svelte';

  let { data } = $props();
</script>

<svelte:head><title>Workflow run · Microcks</title></svelte:head>

{#if data.payload}
  {#key `${data.payload.run.id}/${data.live}`}
    <WorkflowRunView payload={data.payload} live={data.live} />
  {/key}
{:else}
  <PageHeader title="Workflow run" breadcrumb={[{ label: 'Workflows', href: '/workflows' }, { label: 'Workflow run' }]} />
  <Callout tone="danger" title="This run could not be loaded" live="polite">{data.error}</Callout>
  <p><Button href="/workflows">Back to workflows</Button></p>
{/if}
