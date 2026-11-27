<script lang="ts">
  import type { Snippet } from 'svelte';
  import { Icon } from '@microcks/deep-ink';

  interface Crumb {
    label: string;
    href?: string;
  }

  interface Props {
    title: string;
    breadcrumb?: Crumb[];
    meta?: Snippet;
    description?: string;
    descriptionWidth?: number;
    descriptionTone?: 'muted' | 'secondary';
    actions?: Snippet;
    badge?: Snippet;
    children?: Snippet;
    size?: 'sm' | 'md' | 'lg';
    variant?: 'brand' | 'plain';
    align?: 'end' | 'start';
  }

  let {
    title,
    breadcrumb,
    meta,
    description,
    descriptionWidth,
    descriptionTone = 'muted',
    actions,
    badge,
    children,
    size = 'md',
    variant = 'brand',
    align = 'end'
  }: Props = $props();
</script>

<svelte:head><title>{title} · Microcks</title></svelte:head>

<div class="demo-page-header" data-align={align} data-variant={variant}>
  {#if breadcrumb?.length}
    <nav aria-label="Breadcrumb" class="demo-breadcrumb">
      <ol>
        {#each breadcrumb as crumb, i (i)}
          {@const last = i === breadcrumb.length - 1}
          <li>
            {#if i > 0}<span aria-hidden="true" class="demo-breadcrumb-sep">›</span>{/if}
            {#if crumb.href && !last}<a href={crumb.href}>{crumb.label}</a>{:else}<span aria-current={last ? 'page' : undefined}>{crumb.label}</span>{/if}
          </li>
        {/each}
      </ol>
    </nav>
  {/if}
  <div class="demo-page-header-row">
    <div class="demo-page-header-text">
      <div class="demo-page-title-row">
        <h1 id="page-title" tabindex="-1" class="demo-page-title" data-size={size} data-variant={variant}>{title}</h1>
        {#if meta}<span class="demo-page-meta">{@render meta()}</span>{/if}
        {#if badge}{@render badge()}{/if}
      </div>
      {#if description}
        <p class="demo-page-description" data-tone={descriptionTone} style={descriptionWidth ? `max-width:${descriptionWidth}px` : undefined}>{description}</p>
      {/if}
      {#if children}{@render children()}{/if}
    </div>
    {#if actions}<div class="demo-page-actions">{@render actions()}</div>{/if}
  </div>
</div>
