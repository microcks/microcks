<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    as?: 'h2' | 'h3' | 'h4' | 'h5' | 'h6' | 'span' | 'p' | 'div';
    size?: 'md' | 'sm';
    weight?: 'semibold' | 'regular';
    split?: boolean;
    lineHeight?: 'fixed' | 'normal' | 'snug';
    class?: string;
    children?: Snippet;
  }

  let { as = 'span', size = 'md', weight = 'semibold', split, lineHeight = 'fixed', class: className, children }: Props = $props();
</script>

<svelte:element
  this={as}
  class={['section-label', className]}
  data-size={size === 'md' ? undefined : size}
  data-weight={weight === 'semibold' ? undefined : weight}
  data-split={split ? '' : undefined}
  data-line-height={lineHeight === 'fixed' ? undefined : lineHeight}
>{@render children?.()}</svelte:element>
