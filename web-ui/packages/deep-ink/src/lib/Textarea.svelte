<script lang="ts">
  import type { HTMLTextareaAttributes } from 'svelte/elements';

  interface Props extends HTMLTextareaAttributes {
    mono?: boolean;
    size?: 'md' | 'sm';
    surface?: 'surface' | 'bg';
  }

  let { mono, size = 'md', surface, class: className, value = $bindable(), ...rest }: Props = $props();
</script>

<textarea
  {...rest}
  bind:value
  class={['input', className]}
  data-mono={mono ? '' : undefined}
  data-size={size}
  data-surface={surface === 'bg' ? 'bg' : undefined}
></textarea>
