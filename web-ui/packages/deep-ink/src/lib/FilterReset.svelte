<script lang="ts">
  import Icon from './Icon.svelte';

  interface Props {
    count?: number;
    label?: string;
    class?: string;
    onclick?: (e: MouseEvent) => void;
  }

  let { count, label = 'Reset', class: className, onclick }: Props = $props();
</script>

<button type="button" disabled={count === 0} class={['filter-bar-reset', className]} {onclick}>
  <Icon name="reset" size={14} />
  {label}{count !== undefined ? ` (${count})` : ''}
</button>
