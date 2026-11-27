<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    variant?: 'default' | 'canvas';
    gap?: 'normal' | 'roomy';
    class?: string;
    children?: Snippet;
    [key: string]: unknown;
  }

  let { variant = 'default', gap = 'normal', class: className, children, ...rest }: Props = $props();
</script>

<div
  {...rest}
  class={['summary-strip', className]}
  data-gap={gap === 'roomy' ? 'roomy' : undefined}
  data-variant={variant === 'default' ? undefined : variant}
>
  {@render children?.()}
</div>
