<script lang="ts">
  import type { Snippet } from 'svelte';
  import { Icon } from '@microcks/deep-ink';

  interface Props {
    tone: 'success' | 'danger' | 'muted' | 'warning' | 'running';
    icon: 'check' | 'cross' | 'dash' | 'running' | 'circle';
    size?: number;
    children: Snippet;
  }

  let { tone, icon, size = 12, children }: Props = $props();
</script>

<span class="wfr-verdict" data-tone={tone}>
  <Icon name={icon} {size} stroke-width={icon === 'circle' ? 2 : 2.6} stroke-dasharray={icon === 'circle' ? '3 3' : undefined} />
  {@render children()}
</span>
