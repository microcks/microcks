<script lang="ts">
  import type { Snippet } from 'svelte';
  import { CopyButton } from '@microcks/deep-ink';

  interface Props {
    id?: string;
    title: string;
    hint?: string;
    command: string;
    footnote?: Snippet;
    copyLabel?: string;
    class?: string;
  }

  let { id, title, hint, command, footnote, copyLabel = 'Copy', class: className }: Props = $props();
  const uid = $props.id();
</script>

<section {id} aria-labelledby="{uid}-t" class={['ci-panel', className]}>
  <div class="ci-panel-head">
    <div class="ci-panel-text">
      <p class="ci-panel-title"><span id="{uid}-t">{title}</span></p>
      {#if hint}<p class="ci-panel-hint">{hint}</p>{/if}
    </div>
    <CopyButton value={command} label={copyLabel} variant="outline" size="sm" />
  </div>
  <pre class="ci-panel-code"><code>{command}</code></pre>
  {#if footnote}<p class="ci-panel-foot">{@render footnote()}</p>{/if}
</section>
