<script lang="ts">
  import { renderPayload } from './payload.js';

  const MAX_LINES = 10;
  export interface ExchangeBlock {
    label: string;
    text: string;
    tone?: 'secondary' | 'params' | 'headers' | 'body';
    grow?: boolean;
  }

  interface Props {
    title: string;
    label: string;
    meta?: string;
    status?: string;
    statusTone?: 'success' | 'warning' | 'danger';
    blocks: ExchangeBlock[];
    size?: 'md' | 'sm';
    class?: string;
  }

  let { title, label, meta, status, statusTone = 'success', blocks, size = 'md', class: className }: Props = $props();
  let expanded = $state<Record<number, boolean>>({});
</script>

<section aria-label={label} class={['exchange-card', className]} data-size={size === 'sm' ? 'sm' : undefined}>
  <div class="exchange-card-head">
    <h4 class="exchange-card-title">{title}</h4>
    {#if meta}<span class="exchange-card-meta">{meta}</span>{/if}
    {#if status}<span class="exchange-card-status" data-tone={statusTone === 'success' ? undefined : statusTone}>{status}</span>{/if}
  </div>
  {#each blocks as b, i (i)}
    {#if b.tone === 'secondary' || b.tone === 'params' || b.tone === 'headers'}
      <pre role="region" aria-label={b.label} tabindex="0" data-tone={b.tone} data-grow={b.grow ? '' : undefined}>{b.text}</pre>
    {:else}
      {@const p = renderPayload(b.text)}
      {@const long = p.lines > MAX_LINES}
      <pre role="region" aria-label={b.label} tabindex="0" data-grow={b.grow ? '' : undefined}><code class="hljs payload-code" data-clamped={long && !expanded[i] ? '' : undefined}>{@html p.html}</code></pre>
      {#if long}
        <button type="button" class="payload-toggle" aria-expanded={!!expanded[i]} onclick={() => (expanded[i] = !expanded[i])}>
          {expanded[i] ? 'Show less' : `Show all ${p.lines} lines`}
        </button>
      {/if}
    {/if}
  {/each}
</section>
