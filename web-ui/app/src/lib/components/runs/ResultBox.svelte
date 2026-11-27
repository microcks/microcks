<script lang="ts">
  import type { Snippet } from 'svelte';
  import { Icon, type IconName } from '@microcks/deep-ink';

  type Tone = 'success' | 'warning' | 'danger' | 'neutral' | 'info';

  interface Props {
    tone?: Tone;
    size?: 'sm' | 'md';
    title?: Snippet | string;
    icon?: IconName | null;
    action?: Snippet;
    live?: 'status' | 'off';
    bordered?: boolean;
    surface?: 'surface' | 'raised';
    children?: Snippet;
    class?: string;
  }

  let { tone = 'success', size = 'sm', title, icon, action, live = 'status', bordered = true, surface = 'surface', children, class: className }: Props = $props();

  const TONE_ICON: Record<Tone, IconName | undefined> = { success: 'check', warning: 'warning', danger: 'x-circle', neutral: 'warning', info: undefined };
  const name = $derived(icon === null ? undefined : (icon ?? TONE_ICON[tone]));
</script>

<div
  role={live === 'status' ? 'status' : undefined}
  class={['result-box', className]}
  data-tone={tone === 'success' ? undefined : tone}
  data-size={size === 'md' ? 'md' : undefined}
  data-bordered={bordered ? undefined : 'false'}
  data-surface={surface === 'surface' ? undefined : surface}
>
  {#if name}<span class="result-box-icon" aria-hidden="true"><Icon name={name} size={15} /></span>{/if}
  <div class="result-box-body">
    {#if title}<p class="result-box-title">{#if typeof title === 'string'}{title}{:else}{@render title()}{/if}</p>{/if}
    {#if children}<span>{@render children()}</span>{/if}
  </div>
  {#if action}<span class="result-box-action">{@render action()}</span>{/if}
</div>
