<script lang="ts" module>
  export type CommandProtocol = 'REST' | 'SOAP' | 'EVENT' | 'gRPC' | 'GRAPHQL' | 'DIRECT' | 'A2A';

  export interface CommandItem {
    id: string;
    label: string;
    kind: 'action' | 'service' | 'operation' | 'test';
    protocol?: CommandProtocol;
    hint?: string;
    keywords?: string[];
  }
</script>

<script lang="ts">
  import { tick } from 'svelte';
  import Icon from './Icon.svelte';

  interface Props {
    items: CommandItem[];
    open?: boolean;
    placeholder?: string;
    label?: string;
    onCommand?: (item: CommandItem) => void;
    onClose?: () => void;
  }

  let {
    items,
    open = $bindable(false),
    placeholder = 'Jump to a service, or type an action…',
    label = 'Command bar',
    onCommand,
    onClose
  }: Props = $props();

  const KINDS: CommandItem['kind'][] = ['action', 'service', 'operation', 'test'];
  const KIND_LABEL = { action: 'Actions', service: 'Services', operation: 'Operations', test: 'Tests' } as const;
  const PROTOCOL_TOKEN: Record<CommandProtocol, string> = {
    REST: 'proto-rest',
    SOAP: 'proto-soap',
    EVENT: 'proto-event',
    gRPC: 'proto-grpc',
    GRAPHQL: 'proto-graphql',
    DIRECT: 'proto-direct',
    A2A: 'proto-a2a'
  };

  const id = $props.id();
  let dialog = $state<HTMLDialogElement>();
  let input = $state<HTMLInputElement>();
  let listEl = $state<HTMLElement>();
  let query = $state('');
  let active = $state(0);
  let opener: HTMLElement | null = null;

  const list = $derived.by(() => {
    const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
    const filtered = items.filter((i) => {
      const hay = [i.label, ...(i.keywords ?? [])].join(' ').toLowerCase();
      return words.every((w) => hay.includes(w));
    });
    return KINDS.flatMap((k) => filtered.filter((i) => i.kind === k));
  });
  const cur = $derived(Math.max(0, Math.min(active, list.length - 1)));
  const optionId = (i: number) => `${id}-o${i}`;

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) {
      opener = document.activeElement as HTMLElement | null;
      dialog.showModal();
      tick().then(() => input?.focus());
    } else if (!open && dialog.open) {
      dialog.close();
      opener?.isConnected && opener.focus();
      opener = null;
    }
  });

  $effect(() => {
    cur;
    list;
    listEl?.querySelector(`[id="${optionId(cur)}"]`)?.scrollIntoView?.({ block: 'nearest' });
  });

  function close() {
    query = '';
    active = 0;
    if (open) {
      open = false;
      onClose?.();
    }
  }
  function choose(item?: CommandItem) {
    if (!item) return;
    onCommand?.(item);
    close();
  }
  function onGlobalKey(e: KeyboardEvent) {
    if (!(e.metaKey || e.ctrlKey) || e.key.toLowerCase() !== 'k') return;
    e.preventDefault();
    if (open) close();
    else open = true;
  }
  function onKeydown(e: KeyboardEvent) {
    const last = list.length - 1;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (last >= 0) active = cur >= last ? 0 : cur + 1;
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (last >= 0) active = cur <= 0 ? last : cur - 1;
    } else if (e.key === 'Home' && last >= 0) {
      e.preventDefault();
      active = 0;
    } else if (e.key === 'End' && last >= 0) {
      e.preventDefault();
      active = last;
    } else if (e.key === 'Enter' && list[cur]) {
      e.preventDefault();
      choose(list[cur]);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      close();
    }
  }

  const announce = $derived(list.length === 0 ? 'No results' : `${list.length} ${list.length === 1 ? 'result' : 'results'}`);
</script>

<svelte:document onkeydown={onGlobalKey} />

<dialog
  bind:this={dialog}
  class="command-bar"
  aria-label={label}
  oncancel={(e) => {
    e.preventDefault();
    close();
  }}
  onclick={(e) => {
    if (e.target === dialog) close();
  }}
>
  {#if open}
    <div class="command-bar-panel">
      <div class="command-bar-search">
        <Icon name="search" size={18} />
        <label class="sr-only" for="{id}-input">{label}</label>
        <input
          id="{id}-input"
          bind:this={input}
          class="command-bar-input"
          type="text"
          role="combobox"
          autocomplete="off"
          aria-autocomplete="list"
          aria-expanded={list.length > 0}
          aria-controls={list.length > 0 ? `${id}-list` : undefined}
          aria-activedescendant={list[cur] ? optionId(cur) : undefined}
          aria-keyshortcuts="Control+K Meta+K"
          {placeholder}
          bind:value={query}
          oninput={() => (active = 0)}
          onkeydown={onKeydown}
        />
        <kbd class="command-bar-kbd" aria-hidden="true">Ctrl K</kbd>
        <button type="button" class="command-bar-close" aria-label="Close" onclick={close}><Icon name="close" size={18} /></button>
      </div>
      {#if list.length > 0}
        <div class="command-bar-list" id="{id}-list" bind:this={listEl} role="listbox" aria-label="{label} results">
          {#each KINDS as k (k)}
            {@const group = list.filter((i) => i.kind === k)}
            {#if group.length}
              <div class="command-bar-group" role="group" aria-label={KIND_LABEL[k]}>
                <div class="command-bar-group-title" aria-hidden="true">{KIND_LABEL[k]}</div>
                {#each group as i (i.id)}
                  {@const idx = list.indexOf(i)}
                  <!-- svelte-ignore a11y_click_events_have_key_events -->
                  <div
                    id={optionId(idx)}
                    class="command-bar-option"
                    role="option"
                    tabindex="-1"
                    aria-selected={idx === cur}
                    onmousemove={() => active !== idx && (active = idx)}
                    onmousedown={(e) => e.preventDefault()}
                    onclick={() => choose(i)}
                  >
                    {#if i.kind === 'action'}
                      <span class="command-bar-tag" data-kind="action">RUN</span>
                    {:else if i.protocol}
                      <span class="command-bar-tag" style="background:var(--{PROTOCOL_TOKEN[i.protocol]})">{i.protocol}</span>
                    {:else}
                      <span class="command-bar-tag" data-kind="nav">{i.kind.toUpperCase()}</span>
                    {/if}
                    <span>{i.label}</span>
                    <span class="command-bar-hint" aria-hidden="true">{idx === cur ? 'Enter' : (i.hint ?? i.kind)}</span>
                  </div>
                {/each}
              </div>
            {/if}
          {/each}
        </div>
      {:else}
        <div class="command-bar-empty">No match. Try a service name or "test".</div>
      {/if}
      <div class="sr-only" role="status" aria-live="polite" aria-atomic="true">{announce}</div>
    </div>
  {/if}
</dialog>
