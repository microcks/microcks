<script lang="ts">
  import type { Snippet } from 'svelte';
  import { ApiBadge } from '@microcks/deep-ink';

  interface Props {
    workflowId: string;
    documentLabel?: string;
    href: string;
    chip?: Snippet;
  }

  let { workflowId, documentLabel, href, chip }: Props = $props();
</script>

<span class="wfr-badges">
  <a {href} class="wfr-badge-link"><ApiBadge arazzo name={workflowId} version={documentLabel} /></a>
  {@render chip?.()}
</span>
