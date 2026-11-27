<script lang="ts">
  import { Callout, Button } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import RunSetupForm from '#lib/components/runs/RunSetupForm.svelte';

  let { data } = $props();
</script>

<svelte:head><title>Run workflow · Microcks</title></svelte:head>

{#if data.setup}
  {#key data.key}
    <RunSetupForm setup={data.setup} exampleParam={data.example} />
  {/key}
{:else}
  <PageHeader title="Run workflow" breadcrumb={[{ label: 'Workflows', href: '/workflows' }, { label: 'Run workflow' }]} />
  <Callout tone="danger" title="Workflow not found" live="polite">{data.error}</Callout>
  <p><Button href="/workflows">Back to workflows</Button></p>
{/if}
