<script lang="ts" module>
  export interface RunMetaItem {
    term: string;
    value: string;
    kind?: 'text' | 'mono' | 'url' | 'chip';
    detail?: string;
    id?: string;
    /** Renders the value as a link. */
    href?: string;
  }
</script>

<script lang="ts">
  let { items, class: className }: { items: RunMetaItem[]; class?: string } = $props();
</script>

<dl class={['run-meta', className]}>
  {#each items as it (it.id ?? it.term)}
    <div class="run-meta-item">
      <dt>{it.term}</dt>
      <dd data-kind={it.kind && it.kind !== 'text' ? it.kind : undefined}>{#if it.href}<a href={it.href} class="wfr-meta-file">{it.value}</a>{:else}{it.value}{/if}</dd>
      {#if it.detail}<dd class="run-meta-detail">{it.detail}</dd>{/if}
    </div>
  {/each}
</dl>
