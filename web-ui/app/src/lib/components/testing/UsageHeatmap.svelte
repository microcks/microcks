<script lang="ts">
  import { Facts } from '@microcks/deep-ink';
  import { WEEKDAY_HEADERS, formatCount, heatmapWeeks, type UsageView } from './usage.js';

  let { view }: { view: UsageView } = $props();

  const HEAT_ROW_LABELS = ['Mon', '', 'Wed', '', 'Fri', '', 'Sun'];
  const heatmap = $derived(view.heatmap);
  const titleId = $props.id();
  let picked = $state<number | undefined>();
  const sel = $derived(picked ?? heatmap.defaultSelected);
  const cell = $derived(heatmap.cells.find((c) => c.index === sel));
  const weeks = $derived(heatmapWeeks(heatmap.cells));
  const lastDay = $derived(heatmap.cells.length - 1);
  const byPos = $derived(new Map(heatmap.cells.map((c) => [`${c.week}:${c.weekday}`, c.index])));
  const buttons: Record<number, HTMLButtonElement | undefined> = $state({});
  const MOVES: Record<string, [number, number]> = { ArrowRight: [1, 0], ArrowLeft: [-1, 0], ArrowDown: [0, 1], ArrowUp: [0, -1] };

  function onKeyDown(e: KeyboardEvent, index: number) {
    const c = heatmap.cells.find((x) => x.index === index);
    const delta = MOVES[e.key];
    if (!c || !delta) return;
    const target = byPos.get(`${c.week + delta[0]}:${c.weekday + delta[1]}`);
    if (target === undefined || !buttons[target]) return;
    e.preventDefault();
    picked = target;
    buttons[target]?.focus();
  }
</script>

<section class="panel us-heat-card" data-pad="lg" aria-labelledby={titleId}>
<div class="panel-body">
  <div class="us-heat-head">
    <h2 id={titleId}>Daily invocations · last 12 weeks</h2>
    <span>{heatmap.subtitle}</span>
  </div>
  <div class="us-heat" role="group" aria-label="Calendar heatmap, one button per day. Use the arrow keys to move between days." style:--heat-weeks={heatmap.months.length}>
    {#each heatmap.months as m, w (w)}
      <span class="us-heat-month" aria-hidden="true" style:grid-column={w + 2} style:grid-row={1}>{m}</span>
    {/each}
    {#each HEAT_ROW_LABELS as d, r (r)}
      <span class="us-heat-weekday" aria-hidden="true" style:grid-column={1} style:grid-row={r + 2}>{d}</span>
    {/each}
    {#each heatmap.cells as c (c.index)}
      <button
        bind:this={buttons[c.index]}
        type="button"
        class="us-day"
        data-level={c.level}
        style:grid-column={c.week + 2}
        style:grid-row={c.weekday + 2}
        aria-pressed={c.index === sel}
        tabindex={c.index === sel ? 0 : -1}
        aria-label="{c.name}: {formatCount(c.value)} invocations"
        onclick={() => (picked = c.index)}
        onkeydown={(e) => onKeyDown(e, c.index)}
      ></button>
    {/each}
    {#each { length: heatmap.months.length * 7 - heatmap.cells.length } as _, i (i)}
      {@const day = heatmap.cells.length + i}
      <span class="us-day-future" aria-hidden="true" style:grid-column={Math.floor(day / 7) + 2} style:grid-row={(day % 7) + 2}></span>
    {/each}
  </div>
  <div class="us-heat-foot">
    <span aria-live="polite">
      {#if cell}
        <span class="us-heat-sel-day">{cell.name}{cell.index === lastDay ? ` ${heatmap.todayNote}` : ''}</span>
        <span class="us-heat-sel-value">{formatCount(cell.value)} invocations</span>
      {/if}
    </span>
    <span class="us-heat-legend" aria-hidden="true">
      Less
      {#each [0, 1, 2, 3, 4] as l (l)}<span class="us-heat-swatch" data-level={l === 0 ? 'none' : l - 1}></span>{/each}
      More
    </span>
  </div>
  <Facts
    layout="columns"
    size="sm"
    class="us-heat-facts"
    items={[
      { term: '12-week total', value: formatCount(heatmap.total), mono: true },
      { term: 'Weekday average', value: formatCount(heatmap.weekdayAverage), mono: true },
      { term: 'Busiest day', value: heatmap.busiest, mono: true }
    ]}
  />
  <table class="sr-only">
    <caption>Invocations per week and weekday</caption>
    <thead><tr><th scope="col">Week of</th>{#each WEEKDAY_HEADERS as d (d)}<th scope="col">{d}</th>{/each}</tr></thead>
    <tbody>
      {#each weeks as w (w.week)}
        <tr><th scope="row">{w.week}</th>{#each w.values as v, i (i)}<td>{v === null ? 'not yet' : formatCount(v)}</td>{/each}</tr>
      {/each}
    </tbody>
  </table>
</div>
</section>
