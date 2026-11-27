<script lang="ts">
  import { fmt } from '#lib/format.js';

  let { values, total }: { values: number[]; total: number } = $props();
  const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const today = new Date();
  const days = $derived(values.map((_, i) => new Date(today.getTime() - (values.length - 1 - i) * 86_400_000)));
  const max = $derived(Math.max(1, ...values));
</script>

<div class="spark-box">
  <div class="spark-box-head"><span>Service calls · 7 days</span><span class="spark-box-total">{fmt(total)}</span></div>
  <div class="spark-box-bars" role="img" aria-label={`Service calls over 7 days: ${values.map((v, i) => `${DAYS[days[i].getDay()]} ${fmt(v)}`).join(', ')}`}>
    {#each values as v, i (i)}
      <span data-today={i === values.length - 1 ? '' : undefined} style:height={`${Math.max(6, Math.round((v / max) * 100))}%`} title={`${DAYS[days[i].getDay()]}: ${fmt(v)} calls`}></span>
    {/each}
  </div>
  <div class="spark-box-days" aria-hidden="true">{#each days as d, i (i)}<span>{DAYS[d.getDay()][0]}</span>{/each}</div>
</div>
