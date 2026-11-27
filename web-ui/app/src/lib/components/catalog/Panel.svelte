<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title?: string | Snippet;
    headingLevel?: 2 | 3 | 4;
    actions?: Snippet;
    flush?: boolean;
    titleStyle?: 'heading' | 'label';
    padding?: 'md' | 'lg';
    class?: string;
    children?: Snippet;
  }

  let { title, headingLevel = 2, actions, flush, titleStyle = 'heading', padding = 'md', class: className, children }: Props = $props();
  const id = $props.id();
</script>

<section
  class={['panel', className]}
  data-flush={flush ? '' : undefined}
  data-title={titleStyle === 'label' ? 'label' : undefined}
  data-pad={padding === 'lg' ? 'lg' : undefined}
  aria-labelledby={title ? id : undefined}
>
  {#if title || actions}
    <div class="panel-header">
      {#if title}
        <svelte:element this={`h${headingLevel}`} class="panel-title" {id}>
          {#if typeof title === 'string'}{title}{:else}{@render title()}{/if}
        </svelte:element>
      {/if}
      {#if actions}<div class="panel-actions">{@render actions()}</div>{/if}
    </div>
  {/if}
  <div class="panel-body">{@render children?.()}</div>
</section>
