<script lang="ts">
  import type { Snippet } from 'svelte';
  import Icon from './Icon.svelte';
  import type { IconName } from './icon-paths.js';

  interface Props {
    title: string;
    headingLevel?: 1 | 2 | 3 | 4 | 5 | 6;
    icon?: IconName;
    align?: 'center' | 'start';
    class?: string;
    children?: Snippet;
    actions?: Snippet;
  }

  let { title, headingLevel = 2, icon, align = 'center', class: className, children, actions }: Props = $props();
  const id = $props.id();
</script>

<section class={['empty', className]} aria-labelledby={id} data-align={align}>
  {#if icon}<span class="empty-icon"><Icon name={icon} size={40} /></span>{/if}
  <svelte:element this={`h${headingLevel}`} class="empty-title" {id}>{title}</svelte:element>
  {#if children}<div class="empty-body">{@render children()}</div>{/if}
  {#if actions}<div class="empty-actions">{@render actions()}</div>{/if}
</section>
