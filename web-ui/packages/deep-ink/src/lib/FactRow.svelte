<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    term: string;
    children: Snippet;
  }

  let { term, children }: Props = $props();
</script>

<div class="facts-row">
  <dt>{term}</dt>
  <dd>{@render children()}</dd>
</div>
