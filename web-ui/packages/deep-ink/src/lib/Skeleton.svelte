<script lang="ts">
  interface Props {
    lines?: number;
    width?: string | number;
    class?: string;
  }

  let { lines = 1, width, class: className }: Props = $props();
  const style = $derived(width !== undefined ? `--skeleton-width:${typeof width === 'number' ? `${width}px` : width}` : undefined);
</script>

{#if lines <= 1}
  <span aria-hidden="true" class={['skeleton', className]} {style}></span>
{:else}
  <span aria-hidden="true" class={['skeleton-group', className]} {style}>
    {#each { length: lines } as _, i (i)}<span class="skeleton"></span>{/each}
  </span>
{/if}
