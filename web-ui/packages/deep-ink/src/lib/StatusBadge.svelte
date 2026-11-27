<script lang="ts">
  import type { HTMLAttributes } from 'svelte/elements';
  import type { Snippet } from 'svelte';
  import Icon from './Icon.svelte';
  import type { IconName } from './icon-paths.js';
  import type { Outcome } from './types.js';

  interface Props extends HTMLAttributes<HTMLSpanElement> {
    status: Outcome;
    label?: string;
    size?: 'md' | 'sm' | 'lg';
    shape?: 'circled' | 'bare';
    children?: Snippet;
  }

  let { status, label, size = 'md', shape = 'circled', class: className, children, ...rest }: Props = $props();

  const WORD: Record<Outcome, string> = {
    conformant: 'Conformant',
    failed: 'Failed',
    partial: 'Partial',
    running: 'Running',
    never: 'Never tested'
  };
  const ICON: Record<'circled' | 'bare', Record<Outcome, IconName>> = {
    circled: { conformant: 'check-circle', failed: 'x-circle', partial: 'warning', running: 'running', never: 'never-tested' },
    bare: { conformant: 'check', failed: 'cross', partial: 'warning', running: 'running', never: 'circle' }
  };
  const known = $derived(status in WORD ? status : 'never');
  const iconSize = $derived(size === 'lg' ? 15 : shape === 'circled' ? (size === 'sm' ? 14 : 15) : size === 'sm' ? 13 : 14);
</script>

<span {...rest} class={['status', className]} data-status={status} data-size={size} data-shape={shape}>
  <Icon name={ICON[shape][known]} size={iconSize} />
  <span>{label?.trim() ? label : WORD[known]}</span>
</span>
