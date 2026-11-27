<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title: string;
    headingLevel?: 2 | 3 | 4;
    action?: Snippet;
    value: string | number;
    unit?: string;
    tone?: 'neutral' | 'success' | 'warning' | 'danger';
    class?: string;
    children?: Snippet;
  }

  let { title, headingLevel = 2, action, value, unit, tone = 'neutral', class: className, children }: Props = $props();
  const id = $props.id();
</script>

<section class={['stat-card', className]} aria-labelledby={id} data-tone={tone}>
  <div class="stat-card-head">
    <svelte:element this={`h${headingLevel}`} class="stat-card-title" {id}>{title}</svelte:element>
    {#if action}<span class="stat-card-action">{@render action()}</span>{/if}
  </div>
  <p class="stat-card-figure">
    <span class="stat-card-value">{value}</span>
    {#if unit}<span class="stat-card-unit">{unit}</span>{/if}
  </p>
  {#if children}<div class="stat-card-body">{@render children()}</div>{/if}
</section>
