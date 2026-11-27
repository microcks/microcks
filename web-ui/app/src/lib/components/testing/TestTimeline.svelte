<script lang="ts">
  import { Icon, MethodTag } from '@microcks/deep-ink';
  import { formatRelative } from '#lib/format.js';
  import BarChart, { type BarTone } from './BarChart.svelte';
  import { TEST_RESULT_ICON, TEST_RESULT_WORD, dayOf, fmtDuration, runConformance, shortDate, testRunHref, type TestResult, type TestRunSummary } from './model.js';

  interface Props {
    runs: TestRunSummary[];
    operations: { id: string; verb: string; path: string }[];
    selectedId?: string;
    onSelect: (id: string) => void;
    matches: (run: TestRunSummary) => boolean;
    filtered: boolean;
  }

  let { runs, operations, selectedId, onSelect, matches, filtered }: Props = $props();

  /** Bars taller than this duration are capped at 88% and labelled with their duration. */
  const CAP_MS = 3000;
  const WINDOW = 20;
  const TONE: Record<TestResult, BarTone> = { passed: 'success', failed: 'danger', timeout: 'warning', running: 'running' };
  const OP_WORD = { pass: 'passed', fail: 'failed', none: 'no result' } as const;
  const samples = (r: TestRunSummary) => `${r.passed}/${r.total}`;

  const chrono = $derived(runs.slice().sort((a, b) => a.startedAt.localeCompare(b.startedAt)).slice(-WINDOW));
  const n = $derived(chrono.length);
  const selected = $derived(runs.find((r) => r.id === selectedId) ?? chrono[n - 1]);
  const first = $derived(chrono[0]);
  const last = $derived(chrono[n - 1]);
  const line = $derived(
    chrono.map((r, i) => `${((i + 0.5) * (200 / n)).toFixed(1)},${Math.min(100, Math.max(0, ((100 - runConformance(r)) / 50) * 100)).toFixed(1)}`).join(' ')
  );
  const aria = (r: TestRunSummary) =>
    `Run #${r.number}, ${TEST_RESULT_WORD[r.result].toLowerCase()}, ${fmtDuration(r.durationMs)}, ${samples(r)} samples passed, ${shortDate(r.startedAt)}${filtered && !matches(r) ? ', outside the filters' : ''}`;
  const labelled = (r: TestRunSummary, i: number) => i === 0 || i === n - 1 || r.number % 5 === 0 || r.id === selected?.id;

  const bars = $derived(
    chrono.map((r, i) => {
      const capped = r.durationMs > CAP_MS;
      return {
        id: r.id,
        value: capped ? 0.88 * CAP_MS : r.durationMs,
        cap: capped ? `${Math.round(r.durationMs / 1000)} s` : undefined,
        description: aria(r),
        label: labelled(r, i) ? String(r.number) : '',
        tone: TONE[r.result],
        faded: filtered && !matches(r)
      };
    })
  );

  const opStats = $derived(
    operations.map((op, i) => {
      const outcome = (r: TestRunSummary) => r.operations?.[i] ?? 'none';
      const passed = chrono.filter((r) => outcome(r) === 'pass').length;
      const failed = chrono.filter((r) => outcome(r) === 'fail').length;
      const none = n - passed - failed;
      const rate = n ? Math.round((passed / n) * 100) : 0;
      const lastFail = [...chrono].reverse().find((r) => outcome(r) === 'fail');
      return {
        op,
        rate,
        tone: rate >= 80 ? 'success' : rate >= 50 ? 'warning' : 'danger',
        cells: chrono.map((r) => ({ id: r.id, outcome: outcome(r) })),
        aria: `${op.verb} ${op.path}: ${passed} passed, ${failed} failed, ${none} without result over ${n} runs`,
        note: `${passed} passed · ${failed} failed${none ? ` · ${none} no result` : ''}${lastFail ? ` · last failure #${lastFail.number}` : ''}`
      };
    })
  );

  const table = $derived({
    caption: `Conformance timeline, last ${n} runs, oldest first`,
    columns: ['Run', 'Date', 'Result', 'Duration', 'Samples passed', 'Conformance', ...operations.map((op) => `${op.verb} ${op.path}`)],
    rows: chrono.map((r) => [`#${r.number}`, shortDate(r.startedAt), TEST_RESULT_WORD[r.result], fmtDuration(r.durationMs), samples(r), `${runConformance(r)}%`, ...operations.map((_, i) => OP_WORD[r.operations?.[i] ?? 'none'])])
  });
</script>

{#if n > 0 && first && last}
  <section class="test-timeline" aria-labelledby="test-timeline-title">
    <div class="test-timeline-head">
      <div class="test-timeline-heading">
        <h2 id="test-timeline-title">Conformance timeline</h2>
        <span class="test-timeline-sub">Last {n} runs · {dayOf(first.startedAt)} → {dayOf(last.startedAt)}, {new Date(last.startedAt).getUTCFullYear()} · click a bar to inspect</span>
      </div>
      <div class="test-timeline-legend" aria-hidden="true">
        <span><i data-tone="passed"></i>Passed</span>
        <span><i data-tone="failed"></i>Failed</span>
        <span><i data-tone="timeout"></i>Timed out</span>
        <span><i data-tone="line"></i>Conformance, %</span>
        <span class="test-timeline-muted">Bar height = duration</span>
      </div>
    </div>

    <BarChart
      label="Runs, oldest to newest. Use the arrow keys to move between bars."
      max={CAP_MS}
      minHeight={4}
      selectedId={selected?.id}
      {onSelect}
      ticks={['3 s', '1.5 s', '0']}
      ticksEnd={['100%', '75%', '50%']}
      {bars}
      {table}
    >
      {#snippet overlay()}
        <svg viewBox="0 0 200 100" preserveAspectRatio="none"><polyline points={line} vector-effect="non-scaling-stroke" /></svg>
      {/snippet}
    </BarChart>

    {#if selected}
      <div class="test-timeline-strip" aria-live="polite" data-tone={selected.result}>
        <span class="test-timeline-id">#{selected.number}</span>
        <span class="test-timeline-result"><Icon name={TEST_RESULT_ICON[selected.result]} size={14} stroke-width={selected.result === 'timeout' ? 2.2 : 2.6} />{TEST_RESULT_WORD[selected.result]}</span>
        <span class="test-timeline-when">{formatRelative(selected.startedAt)} · {shortDate(selected.startedAt)}, {new Date(selected.startedAt).getUTCFullYear()}</span>
        <span class="test-timeline-endpoint">{selected.endpoint}</span>
        <span class="test-timeline-meta">{selected.runner} · {fmtDuration(selected.durationMs)} · {samples(selected)}</span>
        {#if selected.reason}<span class="test-timeline-reason">{selected.reason}</span>{/if}
        <a class="test-timeline-open" href={testRunHref(selected)} aria-label="Open run #{selected.number}">Open run<Icon name="chevron" size={13} /></a>
      </div>
    {/if}

    {#if opStats.length > 0}
      <ul class="test-op-cards">
        {#each opStats as s (s.op.id)}
          <li class="test-op-card">
            <div class="test-op-card-head">
              <MethodTag method={s.op.verb} />
              <span class="test-op-card-path" title={s.op.path}>{s.op.path}</span>
              <span class="test-op-card-rate" data-tone={s.tone}>{s.rate}%</span>
            </div>
            <div class="test-op-card-strip" role="img" aria-label={s.aria} style:grid-template-columns="repeat({n}, minmax(0, 1fr))">
              {#each s.cells as c (c.id)}<span data-outcome={c.outcome}></span>{/each}
            </div>
            <span class="test-op-card-note">{s.note}</span>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
{/if}
