<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Crumb {
    label: string;
    href?: string;
  }

  interface Props {
    /** Plain-text title, also used for the document title. */
    titleText: string;
    /** Rich title (an ApiBadge with variant="badge"). Defaults to titleText. */
    title?: Snippet;
    breadcrumb?: Crumb[];
    breadcrumbSeparator?: '›' | '/';
    notice?: Snippet;
    meta?: string;
    badge?: Snippet;
    leading?: Snippet;
    description?: string;
    descriptionWidth?: number;
    descriptionTone?: 'muted' | 'secondary';
    actions?: Snippet;
    size?: 'sm' | 'md' | 'lg';
    variant?: 'brand' | 'plain' | 'subtitle' | 'mono' | 'badge';
    align?: 'end' | 'start';
    children?: Snippet;
  }

  let {
    titleText,
    title,
    breadcrumb,
    breadcrumbSeparator = '›',
    notice,
    meta,
    badge,
    leading,
    description,
    descriptionWidth,
    descriptionTone = 'muted',
    actions,
    size = 'md',
    variant = 'brand',
    align = 'end',
    children
  }: Props = $props();
</script>

<svelte:head><title>{titleText} · Microcks</title></svelte:head>

<div class="demo-page-header" data-align={align} data-variant={variant}>
  {#if breadcrumb?.length}
    <nav aria-label="Breadcrumb" class="demo-breadcrumb">
      <ol>
        {#each breadcrumb as crumb, i (i)}
          {@const last = i === breadcrumb.length - 1}
          <li>
            {#if i > 0}<span aria-hidden="true" class="demo-breadcrumb-sep">{breadcrumbSeparator}</span>{/if}
            {#if crumb.href && !last}<a href={crumb.href}>{crumb.label}</a>{:else}<span aria-current={last ? 'page' : undefined}>{crumb.label}</span>{/if}
          </li>
        {/each}
      </ol>
    </nav>
  {/if}
  {#if notice}{@render notice()}{/if}
  <div class="demo-page-header-row">
    <div class="demo-page-header-text">
      <div class="demo-page-title-row" data-leading={leading ? '' : undefined}>
        {#if leading}{@render leading()}{/if}
        <h1 id="page-title" tabindex="-1" class="demo-page-title" data-size={size} data-variant={variant}>
          {#if title}{@render title()}{:else}{titleText}{/if}
        </h1>
        {#if meta}<span class="demo-page-meta">{meta}</span>{/if}
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
