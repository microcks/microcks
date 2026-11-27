<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title?: string;
    children?: Snippet;
  }

  let { title, children }: Props = $props();
</script>

<span class="projection-pill" {title}>{#if children}{@render children()}{:else}Projection · v2 module{/if}</span>
