<script lang="ts">
  import type { Snippet } from 'svelte';
  let { request, response }: { request: Snippet; response: Snippet } = $props();
</script>

<div class="exchange">
  {@render request()}
  <div class="exchange-sep" aria-hidden="true">⇄</div>
  {@render response()}
</div>
