<script lang="ts">
  import type { HTMLSelectAttributes } from 'svelte/elements';

  interface Props extends Omit<HTMLSelectAttributes, 'size'> {
    size?: 'md' | 'sm';
  }

  let { size = 'md', class: className, value = $bindable(), children, ...rest }: Props = $props();
</script>

<select {...rest} bind:value class={['select', className]} data-size={size}>
  {@render children?.()}
</select>
