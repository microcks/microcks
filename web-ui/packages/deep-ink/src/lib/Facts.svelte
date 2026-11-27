<script lang="ts">
  import type { Snippet } from 'svelte';

  export interface Fact {
    term: string;
    value: string;
    sub?: string;
    mono?: boolean;
    tone?: 'success' | 'warning' | 'danger' | 'muted';
  }

  interface Props {
    items?: Fact[];
    layout?: 'rows' | 'inline' | 'columns';
    boxed?: boolean | 'surface' | 'compact';
    size?: 'sm' | 'md' | 'lg';
    class?: string;
    children?: Snippet;
  }

  let { items = [], layout = 'rows', boxed, size = 'md', class: className, children }: Props = $props();
</script>

<dl
  class={['facts', className]}
  data-layout={layout === 'rows' ? undefined : layout}
  data-boxed={boxed === 'surface' || boxed === 'compact' ? boxed : boxed ? '' : undefined}
  data-size={size === 'md' ? undefined : size}
>
  {#each items as f (f.term)}
    <div class="facts-row">
      <dt>{f.term}</dt>
      <dd data-mono={f.mono ? '' : undefined} data-tone={f.tone}>
        {#if f.sub}<span>{f.value}</span><span class="facts-sub">{f.sub}</span>{:else}{f.value}{/if}
      </dd>
    </div>
  {/each}
  {@render children?.()}
</dl>
