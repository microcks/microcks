<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    sideWidth?: string;
    sideMax?: string;
    mainWidth?: string;
    gap?: string;
    align?: 'start' | 'stretch';
    balanced?: boolean;
    keepSideMax?: boolean;
    sideAs?: 'div' | 'aside' | 'nav' | 'section';
    mainAs?: 'div' | 'section';
    class?: string;
    mainClass?: string;
    side: Snippet;
    children: Snippet;
  }

  let { sideWidth, sideMax, mainWidth, gap, align = 'start', balanced, keepSideMax, sideAs = 'div', mainAs = 'div', class: className, mainClass, side, children }: Props = $props();

  const style = $derived(
    [sideWidth && `--split-side:${sideWidth}`, sideMax && `--split-side-max:${sideMax}`, mainWidth && `--split-main:${mainWidth}`, gap && `--split-gap:${gap}`]
      .filter(Boolean)
      .join(';') || undefined
  );
</script>

<div
  class={['split-pane', className]}
  data-align={align === 'stretch' ? 'stretch' : undefined}
  data-balanced={balanced ? '' : undefined}
  data-keep-side-max={keepSideMax ? '' : undefined}
  {style}
>
  <svelte:element this={sideAs} class="split-pane-side">{@render side()}</svelte:element>
  <svelte:element this={mainAs} class={['split-pane-main', mainClass]}>{@render children()}</svelte:element>
</div>
