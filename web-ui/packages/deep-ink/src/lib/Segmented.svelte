<script lang="ts">
  import Icon from './Icon.svelte';
  import type { SegmentedOption } from './types.js';

  interface Props {
    label: string;
    options: SegmentedOption[];
    value: string;
    onchange: (id: string) => void;
    variant?: 'track' | 'joined';
    tone?: 'neutral' | 'primary';
    pressedFill?: 'raised' | 'selected';
    iconSize?: 13 | 15;
    size?: 'md' | 'sm';
    spacing?: 'normal' | 'wide';
    class?: string;
  }

  let {
    label,
    options,
    value,
    onchange,
    variant = 'track',
    tone = 'neutral',
    pressedFill = 'raised',
    iconSize = 15,
    size = 'md',
    spacing = 'normal',
    class: className
  }: Props = $props();
</script>

<div
  role="group"
  aria-label={label}
  class={['segmented', className]}
  data-variant={variant}
  data-tone={tone}
  data-pressed-fill={pressedFill === 'selected' ? 'selected' : undefined}
  data-size={size === 'sm' ? 'sm' : undefined}
  data-spacing={spacing === 'wide' ? 'wide' : undefined}
>
  {#each options as o (o.id)}
    <button type="button" aria-pressed={o.id === value} disabled={o.disabled} title={o.title} onclick={() => onchange(o.id)}>
      {#if o.dot}<span class="segmented-dot" data-dot={o.dot} aria-hidden="true"></span>{/if}
      {#if o.icon}<Icon name={o.icon} size={iconSize} data-tone={o.iconTone} />{/if}
      {o.label}
      {#if o.count !== undefined}<span class="segmented-count"><span class="sr-only">, </span>{o.count}</span>{/if}
    </button>
  {/each}
</div>
