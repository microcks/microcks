<script lang="ts">
  import type { OperationResult } from './types.js';

  interface Props {
    outcomes: OperationResult[];
    size?: 'full' | 'mini';
    unit?: string;
    class?: string;
  }

  let { outcomes, size = 'full', unit = 'operation', class: className }: Props = $props();

  const label = $derived.by(() => {
    const total = outcomes.length;
    if (total === 0) return `No ${unit}s`;
    const pass = outcomes.filter((o) => o === 'pass').length;
    const fail = outcomes.filter((o) => o === 'fail').length;
    const untested = total - pass - fail;
    const parts = [`${pass} of ${total} ${unit}${total === 1 ? '' : 's'} pass`];
    if (fail) parts.push(`${fail} ${fail === 1 ? 'fails' : 'fail'}`);
    if (untested) parts.push(`${untested} untested`);
    return parts.join(', ');
  });
</script>

<div class={['conformance', className]} data-size={size} role="img" aria-label={label}>
  {#each outcomes as result, i (i)}
    <span data-result={result}></span>
  {/each}
</div>
