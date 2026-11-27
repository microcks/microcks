<script lang="ts">
  import { Button, EmptyState } from '@microcks/deep-ink';
  import CatalogPageHeader from './CatalogPageHeader.svelte';

  let { status, what = 'service' }: { status: number; what?: string } = $props();
</script>

<CatalogPageHeader titleText={status === 404 ? `${what} not found` : 'Something went wrong'} />
<div style="margin-top: 24px">
  <EmptyState title={status === 404 ? `This ${what} does not exist` : `The ${what} could not be loaded (HTTP ${status})`} headingLevel={2} icon="search-x">
    <p>{status === 404 ? 'It may have been removed, or its source was deleted.' : 'Try again in a moment.'}</p>
    {#snippet actions()}
      <Button variant="primary" href="/services">Go to APIs &amp; Services</Button>
    {/snippet}
  </EmptyState>
</div>
