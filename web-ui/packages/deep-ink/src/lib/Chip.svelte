<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { HTMLAttributes } from 'svelte/elements';
  import Icon from './Icon.svelte';
  import { PROTOCOL_ATTR, type Protocol } from './types.js';

  interface Props extends Omit<HTMLAttributes<HTMLElement>, 'onclick'> {
    labelKey?: string;
    tone?: 'neutral' | 'info' | 'success' | 'warning' | 'danger';
    variant?: 'solid' | 'outline' | 'dashed';
    size?: 'md' | 'sm' | 'lg';
    protocol?: Protocol;
    count?: number;
    /** When defined the chip is a toggle `<button aria-pressed>`. */
    pressed?: boolean;
    onclick?: (e: MouseEvent) => void;
    onRemove?: () => void;
    removeLabel?: string;
    children?: Snippet;
  }

  let {
    labelKey,
    tone = 'neutral',
    variant = 'solid',
    size = 'md',
    protocol,
    count,
    pressed,
    onclick,
    onRemove,
    removeLabel,
    class: className,
    children,
    ...rest
  }: Props = $props();
</script>

{#snippet body()}
  <span class="chip-label"
    >{#if labelKey}<span class="chip-key">{labelKey}</span><span class="chip-eq">=</span>{/if}<span class="chip-value"
      >{@render children?.()}</span
    ></span
  >
  {#if count !== undefined}{' '}<span class="chip-count">{count}</span>{/if}
{/snippet}

{#if pressed !== undefined}
  <button
    type="button"
    {...rest as HTMLAttributes<HTMLButtonElement>}
    class={['chip', className]}
    data-tone={tone}
    data-variant={variant}
    data-size={size}
    data-protocol={protocol ? PROTOCOL_ATTR[protocol] : undefined}
    data-empty={count === 0 ? '' : undefined}
    aria-pressed={pressed}
    {onclick}
  >
    {@render body()}
  </button>
{:else}
  <span
    {...rest}
    class={['chip', className]}
    data-tone={tone}
    data-variant={variant}
    data-size={size}
    data-protocol={protocol ? PROTOCOL_ATTR[protocol] : undefined}
    data-empty={count === 0 ? '' : undefined}
  >
    {@render body()}
    {#if onRemove}
      <button type="button" class="chip-remove" aria-label={removeLabel ?? `Remove ${labelKey ?? ''}`} onclick={onRemove}
        ><Icon name="close" size={12} /></button
      >
    {/if}
  </span>
{/if}
