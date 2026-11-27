<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { HTMLAnchorAttributes, HTMLButtonAttributes } from 'svelte/elements';
  import Icon from './Icon.svelte';
  import type { IconName } from './icon-paths.js';
  import type { ButtonSize, ButtonVariant } from './types.js';

  type Own = {
    variant?: ButtonVariant;
    size?: ButtonSize;
    tone?: 'danger';
    confirm?: boolean;
    icon?: IconName;
    iconOnly?: boolean;
    loading?: boolean;
    children?: Snippet;
    ref?: HTMLElement | null;
  };
  type Props = Own & Omit<HTMLButtonAttributes & HTMLAnchorAttributes, keyof Own>;

  let {
    variant = 'secondary',
    size = 'md',
    tone,
    confirm,
    icon,
    iconOnly,
    loading,
    href,
    class: className,
    children,
    type = 'button',
    disabled,
    ref = $bindable(),
    ...rest
  }: Props = $props();

  const small = $derived(size === 'sm' || size === 'compact' || size === 'tile' || variant === 'link' || variant === 'dashed');
</script>

{#if href !== undefined}
  <a
    bind:this={ref}
    {href}
    {...rest as HTMLAnchorAttributes}
    class={['button', className]}
    data-variant={variant}
    data-size={size}
    data-confirm={confirm ? '' : undefined}
    data-tone={tone}
    data-icon-only={iconOnly ? '' : undefined}
    aria-busy={loading ? true : undefined}
  >
    {#if icon}<Icon name={icon} size={small ? 14 : 16} />{/if}
    {@render children?.()}
  </a>
{:else}
  <button
    bind:this={ref}
    {type}
    disabled={disabled || loading}
    {...rest as HTMLButtonAttributes}
    class={['button', className]}
    data-variant={variant}
    data-size={size}
    data-confirm={confirm ? '' : undefined}
    data-tone={tone}
    data-icon-only={iconOnly ? '' : undefined}
    aria-busy={loading ? true : undefined}
  >
    {#if icon}<Icon name={icon} size={small ? 14 : 16} />{/if}
    {@render children?.()}
  </button>
{/if}
