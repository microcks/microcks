<script lang="ts">
  import type { Snippet } from 'svelte';
  import { CopyButton } from '@microcks/deep-ink';

  interface Props {
    id?: string;
    title: string;
    pill?: Snippet;
    hint?: Snippet;
    command: string;
    footnote?: Snippet;
    dashed?: boolean;
    copyLabel?: string;
  }

  let { id, title, pill, hint, command, footnote, dashed, copyLabel = 'Copy' }: Props = $props();
  const titleId = $props.id();
</script>

<section {id} aria-labelledby={titleId} class="ci-panel" data-dashed={dashed ? '' : undefined}>
  <div class="ci-panel-head">
    <div class="ci-panel-text">
      <p class="ci-panel-title"><span id={titleId}>{title}</span>{#if pill}{@render pill()}{/if}</p>
      {#if hint}<p class="ci-panel-hint">{@render hint()}</p>{/if}
    </div>
    <CopyButton value={command} label={copyLabel} variant="outline" size="sm" />
  </div>
  <pre class="ci-panel-code"><code>{command}</code></pre>
  {#if footnote}<p class="ci-panel-foot">{@render footnote()}</p>{/if}
</section>
