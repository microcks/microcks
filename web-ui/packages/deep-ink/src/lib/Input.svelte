<script lang="ts">
  import type { HTMLInputAttributes } from 'svelte/elements';

  interface Props extends Omit<HTMLInputAttributes, 'size'> {
    mono?: boolean;
    size?: 'md' | 'sm';
    surface?: 'surface' | 'bg';
    ref?: HTMLInputElement | null;
  }

  let { mono, size = 'md', surface, class: className, type = 'text', value = $bindable(), ref = $bindable(), ...rest }: Props = $props();
</script>

<input
  {...rest}
  {type}
  bind:value
  bind:this={ref}
  class={['input', className]}
  data-mono={mono ? '' : undefined}
  data-size={size}
  data-surface={surface === 'bg' ? 'bg' : undefined}
/>
