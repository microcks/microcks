<script lang="ts">
  import type { Snippet } from 'svelte';
  let { label, class: className, children }: { label: string; class?: string; children?: Snippet } = $props();
</script>

<section aria-label={label} class={['stat-grid', className]}>{@render children?.()}</section>
