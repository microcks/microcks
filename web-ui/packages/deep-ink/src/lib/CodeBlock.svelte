<script lang="ts" module>
  export interface CodeLine {
    text: string;
    bad?: boolean;
    note?: string;
    selected?: boolean;
    /** Changed line of a diff: a visible "+" mark and a hidden "Changed: " prefix. */
    changed?: boolean;
  }

  export interface CodeTab {
    id: string;
    label: string;
  }
</script>

<script lang="ts">
  import CopyButton from './CopyButton.svelte';
  import Icon from './Icon.svelte';

  interface Props {
    code?: string;
    lines?: CodeLine[];
    language?: string;
    caption?: string;
    label?: string;
    copy?: boolean;
    variant?: 'framed' | 'plain' | 'bare';
    tabs?: CodeTab[];
    tab?: string;
    onTabChange?: (id: string) => void;
    tabsLabel?: string;
    lineNumbers?: boolean;
    tone?: 'danger';
    maxHeight?: string | number;
    class?: string;
  }

  let {
    code,
    lines,
    language,
    caption,
    label: regionLabel,
    copy = true,
    variant = 'framed',
    tabs,
    tab,
    onTabChange,
    tabsLabel = 'Format',
    lineNumbers,
    tone,
    maxHeight,
    class: className
  }: Props = $props();

  const rows = $derived<CodeLine[] | undefined>(lines ?? (lineNumbers && code !== undefined ? code.split('\n').map((text) => ({ text })) : undefined));
  const value = $derived(code ?? (lines ?? []).map((l) => l.text).join('\n'));
  const tabLabel = $derived(tabs?.find((t) => t.id === tab)?.label);
  const regionName = $derived(regionLabel ?? caption ?? (tabLabel ? `${tabLabel} code` : language ? `${language} code` : 'Code'));
  const diff = $derived(rows?.some((l) => l.changed) ?? false);
  const header = $derived(variant === 'framed' && Boolean(caption || copy || tabs?.length));
</script>

<figure
  class={['code', className]}
  data-language={language}
  data-variant={variant}
  data-numbered={lineNumbers ? '' : undefined}
  data-tone={tone}
  data-diff={diff ? '' : undefined}
>
  {#if header}
    <figcaption>
      {#if tabs?.length}
        <span class="code-tabs" role="group" aria-label={tabsLabel}>
          {#each tabs as t (t.id)}
            <button type="button" aria-pressed={t.id === tab} onclick={() => onTabChange?.(t.id)}>{t.label}</button>
          {/each}
        </span>
      {/if}
      <span class="code-caption">{caption ?? ''}</span>
      {#if copy}<CopyButton {value} variant="link" class="code-copy" />{/if}
    </figcaption>
  {/if}
  <!-- The scroll container must be keyboard reachable, so it is focusable and named. -->
  <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
  <pre tabindex="0" role="region" aria-label={regionName} style={maxHeight !== undefined ? `max-height:${typeof maxHeight === 'number' ? `${maxHeight}px` : maxHeight}` : undefined}><code class={language ? `language-${language}` : undefined}>{#if rows}{#each rows as line, i (i)}<span class="code-line" data-bad={line.bad ? '' : undefined} data-selected={line.selected ? '' : undefined} data-changed={line.changed ? '' : undefined}>{#if lineNumbers}<span class="code-ln" aria-hidden="true">{i + 1}</span>{/if}{#if diff}<span class="code-mark" data-kind="changed" aria-hidden="true">{line.changed ? '+' : ''}</span>{#if line.changed}<span class="sr-only">Changed: </span>{/if}{/if}{#if line.bad}<span class="code-mark" aria-hidden="true"><Icon name="close" size={11} /></span><span class="sr-only">Problem: </span>{/if}<span class="code-text">{line.text}</span>{#if line.note}<span class="code-note">{line.note}</span>{/if}</span>{/each}{:else}{code}{/if}</code></pre>
</figure>
