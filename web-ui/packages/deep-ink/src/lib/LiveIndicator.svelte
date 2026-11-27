<script lang="ts">
  import Icon from './Icon.svelte';

  interface Props {
    label?: string;
    paused?: boolean;
    size?: 'md' | 'sm' | 'lg';
    variant?: 'pill' | 'inline' | 'box' | 'dot';
    class?: string;
  }

  let { label = 'live', paused, size = 'md', variant = 'pill', class: className }: Props = $props();
</script>

<span class={['live', className]} data-paused={paused ? '' : undefined} data-size={size} data-variant={variant}>
  {#if paused}<Icon name="pause" size={10} />{:else}<span class="live-dot" aria-hidden="true"></span>{/if}
  {#if variant === 'dot'}<span class="sr-only">{label}</span>{:else}{label}{#if paused}<span class="live-paused"> · paused</span>{/if}{/if}
</span>
