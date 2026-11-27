<script lang="ts">
  import { onDestroy } from 'svelte';
  import Button from './Button.svelte';
  import type { ButtonSize, ButtonVariant } from './types.js';

  interface Props {
    value: string;
    label?: string;
    copiedLabel?: string;
    size?: ButtonSize;
    variant?: ButtonVariant;
    iconOnly?: boolean;
    class?: string;
  }

  let { value, label = 'Copy', copiedLabel = 'Copied', size = 'sm', variant = 'secondary', iconOnly, class: className }: Props = $props();

  let state = $state<'idle' | 'copied' | 'failed'>('idle');
  let timer: ReturnType<typeof setTimeout> | undefined;
  onDestroy(() => clearTimeout(timer));

  async function write(text: string): Promise<boolean> {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {
      try {
        const ta = document.createElement('textarea');
        ta.value = text;
        ta.setAttribute('readonly', '');
        ta.style.position = 'fixed';
        ta.style.opacity = '0';
        document.body.appendChild(ta);
        ta.select();
        const ok = document.execCommand('copy');
        ta.remove();
        return ok;
      } catch {
        return false;
      }
    }
  }

  async function copy() {
    state = (await write(value)) ? 'copied' : 'failed';
    clearTimeout(timer);
    timer = setTimeout(() => (state = 'idle'), 1800);
  }

  const text = $derived(state === 'copied' ? copiedLabel : label);
  const message = $derived(state === 'copied' ? copiedLabel : state === 'failed' ? 'Copy failed' : '');
</script>

<Button
  {size}
  {variant}
  {iconOnly}
  icon={state === 'copied' ? 'check' : 'copy'}
  class={['copy-button', className]}
  data-state={state === 'idle' ? undefined : state}
  aria-label={iconOnly ? text : undefined}
  onclick={copy}
>
  {#if !iconOnly}{text}{/if}
</Button>
<span role="status" aria-live="polite" class="sr-only">{message}</span>
