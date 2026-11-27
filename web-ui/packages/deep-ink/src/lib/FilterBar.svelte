<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    label: string;
    class?: string;
    children?: Snippet;
    [key: string]: unknown;
  }

  let { label, class: className, children, ...rest }: Props = $props();
</script>

<section {...rest} aria-label={label} class={['filter-bar', className]}>
  {@render children?.()}
</section>
