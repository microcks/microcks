<script lang="ts">
  import { invalidate } from '$app/navigation';
  import TestRunView from '#lib/components/testing/TestRunView.svelte';
  import type { Service } from '#lib/types.js';

  let { data } = $props();
  const service = $derived((data.services as Service[]).find((s) => s.id === data.run.serviceId));

  // The backend runs the test asynchronously: poll until it is finished.
  $effect(() => {
    if (!data.run.inProgress) return;
    const timer = setInterval(() => void invalidate('app:test-run'), 1500);
    return () => clearInterval(timer);
  });
</script>

{#key `${data.run.id}:${data.run.inProgress}`}
  <TestRunView run={data.run} history={data.history} {service} />
{/key}
