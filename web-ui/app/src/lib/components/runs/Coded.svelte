<script lang="ts">
  let { text }: { text: string } = $props();
  const parts = $derived(text.split('`'));
</script>

{#each parts as part, i (i)}{#if i % 2 === 1}<code class="mono">{part}</code>{:else}{part}{/if}{/each}
