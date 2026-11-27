<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title?: string;
    class?: string;
    children?: Snippet;
  }

  let { title, class: className, children }: Props = $props();
  const id = $props.id();
</script>

<div class={['filter-bar-row', className]} role={title ? 'group' : undefined} aria-labelledby={title ? id : undefined}>
  {#if title}<span class="filter-bar-label" {id}>{title}</span>{/if}
  {@render children?.()}
</div>
