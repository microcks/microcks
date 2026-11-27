<script lang="ts">
  import type { Snippet } from 'svelte';
  import { usePermissions, type Action } from '#lib/permissions.js';

  let { action, children, fallback }: { action: Action; children: Snippet; fallback?: Snippet } = $props();
  const can = usePermissions();
</script>

{#if can(action)}
  {@render children()}
{:else if fallback}
  {@render fallback()}
{/if}
