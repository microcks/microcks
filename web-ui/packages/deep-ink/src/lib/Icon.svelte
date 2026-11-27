<script lang="ts">
  import type { SVGAttributes } from 'svelte/elements';
  import { ICON_PATHS, type IconName } from './icon-paths.js';

  interface Props extends Omit<SVGAttributes<SVGSVGElement>, 'name'> {
    name: IconName;
    size?: number;
    label?: string;
  }

  let { name, size = 16, label, class: className, ...rest }: Props = $props();
  const paths = $derived(ICON_PATHS[name] ?? ICON_PATHS.circle);
</script>

<svg
  width={size}
  height={size}
  viewBox="0 0 24 24"
  fill="none"
  stroke="currentColor"
  stroke-width="2"
  stroke-linecap="round"
  stroke-linejoin="round"
  focusable="false"
  role={label ? 'img' : undefined}
  aria-label={label}
  aria-hidden={label ? undefined : true}
  {...rest}
  class={['icon', className]}
>
  {#each paths as p, i (i)}
    {#if typeof p === 'string'}
      <path d={p} />
    {:else}
      <path d={p.d} stroke-dasharray={p.dash} />
    {/if}
  {/each}
</svg>
