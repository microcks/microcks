<script lang="ts">
  import { MethodTag, type Protocol } from '@microcks/deep-ink';

  export interface OpPickOption {
    id: string;
    method: string;
    protocol?: Protocol;
    path: string;
    summary?: string;
    meta?: string;
    end?: string;
    endLabel?: string;
  }

  interface Props {
    options: OpPickOption[];
    value?: string;
    onchange: (id: string) => void;
    mode?: 'toggle' | 'radio';
    label?: string;
    labelledBy?: string;
    class?: string;
  }

  let { options, value, onchange, mode = 'toggle', label, labelledBy, class: className }: Props = $props();
  let buttons: HTMLButtonElement[] = [];
  const checked = $derived(Math.max(0, options.findIndex((o) => o.id === value)));

  function onkeydown(e: KeyboardEvent, i: number) {
    const n = options.length;
    const next =
      e.key === 'ArrowDown' || e.key === 'ArrowRight' ? (i + 1) % n
      : e.key === 'ArrowUp' || e.key === 'ArrowLeft' ? (i - 1 + n) % n
      : e.key === 'Home' ? 0
      : e.key === 'End' ? n - 1
      : undefined;
    if (next === undefined) return;
    e.preventDefault();
    onchange(options[next].id);
    buttons[next]?.focus();
  }
</script>

{#snippet card(o: OpPickOption)}
  <MethodTag method={o.method} protocol={o.protocol} />
  <span class="op-pick-main">
    <span class="op-pick-path">{o.path}</span>
    {#if o.summary}<span class="op-pick-summary">{o.summary}</span>{/if}
    {#if o.meta}<span class="op-pick-meta">{o.meta}</span>{/if}
  </span>
  {#if o.end}
    <span class="op-pick-end">{#if o.endLabel}<span class="sr-only">{o.endLabel} </span>{/if}{o.end}</span>
  {/if}
{/snippet}

{#if mode === 'toggle'}
  <ul class={['op-pick-list', className]} aria-label={label} aria-labelledby={labelledBy}>
    {#each options as o (o.id)}
      <li>
        <button type="button" class="op-pick" aria-pressed={o.id === value} onclick={() => onchange(o.id)}>{@render card(o)}</button>
      </li>
    {/each}
  </ul>
{:else}
  <div class={['op-pick-list', className]} role="radiogroup" aria-label={label} aria-labelledby={labelledBy}>
    {#each options as o, i (o.id)}
      <button
        bind:this={buttons[i]}
        type="button"
        role="radio"
        class="op-pick"
        aria-checked={o.id === value}
        tabindex={i === checked ? 0 : -1}
        onclick={() => onchange(o.id)}
        onkeydown={(e) => onkeydown(e, i)}
      >{@render card(o)}</button>
    {/each}
  </div>
{/if}
