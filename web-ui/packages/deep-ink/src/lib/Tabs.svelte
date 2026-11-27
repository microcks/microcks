<script lang="ts" module>
  import type { IconName } from './icon-paths.js';

  export interface TabItem {
    id: string;
    label: string;
    count?: number;
    /** Warning pill after the count, read with `alertLabel` (default "need attention"). */
    alert?: number;
    alertLabel?: string;
    icon?: IconName;
    hint?: string;
    disabled?: boolean;
  }
</script>

<script lang="ts">
  import type { Snippet } from 'svelte';
  import Icon from './Icon.svelte';

  interface Props {
    label: string;
    items: TabItem[];
    value: string;
    onchange: (id: string) => void;
    variant?: 'line' | 'card';
    class?: string;
    /** Receives the panel ids: render the panel body for the selected tab. */
    children?: Snippet<[{ tabId: string; panelId: string }]>;
  }

  let { label, items, value, onchange, variant = 'line', class: className, children }: Props = $props();

  const prefix = $props.id();
  const tabId = (id: string) => `${prefix}-tab-${id}`;
  const panelId = (id: string) => `${prefix}-panel-${id}`;
  let buttons: HTMLButtonElement[] = [];

  const enabled = $derived(items.map((t, i) => (t.disabled ? -1 : i)).filter((i) => i >= 0));
  const selectedIndex = $derived(items.findIndex((t) => t.id === value && !t.disabled));
  const tabStop = $derived(selectedIndex >= 0 ? selectedIndex : (enabled[0] ?? -1));

  function onKeydown(e: KeyboardEvent, index: number) {
    if (enabled.length === 0) return;
    const pos = enabled.indexOf(index);
    let next: number | undefined;
    if (e.key === 'ArrowRight') next = enabled[(pos + 1) % enabled.length];
    else if (e.key === 'ArrowLeft') next = enabled[(pos - 1 + enabled.length) % enabled.length];
    else if (e.key === 'Home') next = enabled[0];
    else if (e.key === 'End') next = enabled[enabled.length - 1];
    if (next === undefined) return;
    e.preventDefault();
    buttons[next]?.focus();
    onchange(items[next].id);
  }
</script>

<div class={['tabs', className]} data-variant={variant === 'card' ? 'card' : undefined}>
  <div role="tablist" class="tablist" aria-label={label}>
    {#each items as t, i (t.id)}
      <button
        bind:this={buttons[i]}
        type="button"
        role="tab"
        class="tab"
        id={tabId(t.id)}
        aria-selected={i === selectedIndex}
        aria-controls={panelId(t.id)}
        tabindex={i === tabStop ? 0 : -1}
        disabled={t.disabled}
        onclick={() => onchange(t.id)}
        onkeydown={(e) => onKeydown(e, i)}
      >
        {#if t.icon}<Icon name={t.icon} size={16} />{/if}
        {t.label}
        {#if t.hint}<span class="tab-hint"><span class="sr-only">,</span> {t.hint}</span>{/if}
        {#if t.count !== undefined}<span class="tab-count"><span class="sr-only">, </span>{t.count}</span>{/if}
        {#if t.alert}<span class="tab-alert"><span class="sr-only">, </span>{t.alert}<span class="sr-only"> {t.alertLabel ?? 'need attention'}</span></span>{/if}
      </button>
    {/each}
  </div>
  {#if children}
    <div role="tabpanel" id={panelId(value)} aria-labelledby={tabId(value)} class="tabpanel" tabindex="0">
      {@render children({ tabId: tabId(value), panelId: panelId(value) })}
    </div>
  {/if}
</div>
