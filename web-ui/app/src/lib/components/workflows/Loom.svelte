<script lang="ts">
  import { untrack } from 'svelte';
  import { Icon } from '@microcks/deep-ink';
  import {
    checkRows,
    layoutLoom,
    loomGrid,
    operationParts,
    weaveThreads,
    type LoomLayout,
    type LoomPlacement,
    type WovenThread
  } from '#lib/workflows/loom-layout.js';
  import type {
    LoomAction,
    LoomCheckState,
    LoomInput,
    LoomOutput,
    LoomSource,
    LoomStep,
    LoomThread
  } from '#lib/workflows/loom-model.js';

  /**
   * Arazzo workflow as a loom: one lane per sourceDescription (warp), steps as nodes, data threads between steps
   * (weft). Ported from the Deep Ink `workflow-loom` element. Keyboard: arrows move between nodes, Home/End jump to
   * the first/last, Enter/Space select (again: clear). Inputs, outputs and thread labels set the expression lens.
   */
  interface Props {
    sources: LoomSource[];
    steps: LoomStep[];
    threads?: LoomThread[];
    inputs?: LoomInput[];
    outputs?: LoomOutput[];
    outputNote?: string;
    validatesSchemas?: boolean;
    selectedStep?: string;
    lens?: string;
    label?: string;
    onstepselect?: (stepId: string | undefined) => void;
    onlensselect?: (id: string | undefined) => void;
  }

  let {
    sources,
    steps,
    threads = [],
    inputs = [],
    outputs = [],
    outputNote,
    validatesSchemas = false,
    selectedStep,
    lens,
    label = 'Arazzo workflow',
    onstepselect,
    onlensselect
  }: Props = $props();

  type StepStatus = 'passed' | 'failed' | 'not-run';
  const STATUS_WORD: Record<StepStatus, string> = { passed: 'Passed', failed: 'Failed', 'not-run': 'Not run' };
  const RING = 'M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0';
  const MARK = { pass: 'M8 12.5l2.5 2.5L16 9.5', fail: 'M9 9l6 6M15 9l-6 6', skip: 'M8 12h8' };
  const STATUS_MARK: Record<StepStatus, string> = { passed: MARK.pass, failed: MARK.fail, 'not-run': MARK.skip };
  const CHECK_MARK: Record<LoomCheckState, string> = {
    passed: MARK.pass,
    failed: MARK.fail,
    off: MARK.skip,
    'not-evaluated': MARK.skip
  };
  const PROTOCOL_TOKEN: Record<string, string> = {
    REST: 'proto-rest',
    SOAP: 'proto-soap',
    EVENT: 'proto-event',
    gRPC: 'proto-grpc',
    GRAPHQL: 'proto-graphql',
    DIRECT: 'proto-direct',
    A2A: 'proto-a2a'
  };

  const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;
  const seconds = (n: number) => `${Number.isInteger(n) ? n : Number(n.toFixed(2))} s`;

  let focusId = $state<string | undefined>();
  let edgePaths = $state<string[]>([]);
  let stageEl = $state<HTMLDivElement>();
  const nodeEls = new Map<string, HTMLButtonElement>();
  const uid = Math.random().toString(36).slice(2, 8);

  const woven = $derived(weaveThreads(threads));
  const layout = $derived(layoutLoom(steps, sources));
  const lanes = $derived.by(() => {
    const out: LoomSource[] = [...sources];
    layout.placements.forEach((p) => {
      if (!out[p.lane]) out[p.lane] = { name: steps[p.number - 1]?.source ?? '', type: 'openapi', protocol: 'REST', linked: true };
    });
    return out;
  });
  const grid = $derived(loomGrid(layout.columns));
  const parallel = $derived(steps.some((s) => s.dependsOn !== undefined));

  const firstId = $derived(layout.placements[0]?.stepId);
  const known = (id?: string) => !!id && steps.some((s) => s.stepId === id);
  const tabStop = $derived(known(focusId) ? focusId : known(selectedStep) ? selectedStep : firstId);

  const laneColor = (l: LoomSource | undefined) => `var(--${PROTOCOL_TOKEN[l?.protocol ?? 'REST'] ?? 'proto-rest'})`;
  const lensOn = (id: string) => lens === id;
  const dimmed = (id: string) => !!lens && lens !== id;

  const inputLens = (n: LoomInput) => {
    const expression = n.expression ?? `$inputs.${n.name}`;
    return { id: woven.find((t) => t.expression === expression)?.id ?? expression };
  };
  const outputLens = (o: LoomOutput) => {
    const t = woven.find((x) => x.drops.some((d) => d.to === '$outputs' && d.label === o.name));
    return { id: t?.id ?? o.name };
  };
  const colOf = (id: string) => {
    if (id === '$outputs') return grid.steps + 1;
    const pl = layout.placements.find((p) => p.stepId === id);
    return pl ? pl.column + 1 : 0;
  };

  // Grid rows: per lane, a header, an actions row when a step has actions, then one row per parallel branch.
  const laneRows = $derived.by(() => {
    let row = 1;
    return lanes.map((_, i) => {
      const header = row++;
      const hasActions = steps.some(
        (s, idx) =>
          layout.placements[idx]?.lane === i && (s.actions ?? []).some((a) => a.type === 'end' || a.type === 'retry')
      );
      const actions = hasActions ? row++ : undefined;
      const first = row;
      row += layout.laneRows[i] ?? 1;
      return { header, actions, first };
    });
  });
  const nodeFirst = $derived(laneRows[0]?.first ?? 1);
  const nodeEnd = $derived(
    laneRows.length ? laneRows[laneRows.length - 1]!.first + (layout.laneRows[lanes.length - 1] ?? 1) : 2
  );

  function select(stepId: string) {
    onstepselect?.(selectedStep === stepId ? undefined : stepId);
  }
  function pickLens(id: string) {
    onlensselect?.(lens === id ? undefined : id);
  }

  function statusPhrase(s: LoomStep): string | undefined {
    if (!s.status) return undefined;
    const word = STATUS_WORD[s.status];
    const n = s.attempts ?? 0;
    return n > 1 || (s.status === 'failed' && n > 0) ? `${word} after ${plural(n, 'attempt')}` : word;
  }

  function actionText(a: LoomAction, s: LoomStep): string {
    if (a.type === 'end') {
      const state = a.taken === true ? ', taken' : a.taken === false ? ', not taken' : '';
      return `On ${a.on}, end if ${a.criteria}${state}`;
    }
    const applied = a.applied ? `, applied${s.attempts ? ` (${plural(s.attempts, 'attempt')})` : ''}` : '';
    return `On failure, retry every ${seconds(a.retryAfter)}, up to ${plural(a.retryLimit, 'time')}${applied}`;
  }

  function description(s: LoomStep, source?: LoomSource): string {
    const bits: string[] = [];
    if (source?.type === 'asyncapi') bits.push('AsyncAPI step, shown but not runnable');
    if (source && !source.linked) bits.push(`Source ${source.name} is not linked`);
    for (const a of s.actions ?? []) bits.push(actionText(a, s));
    return bits.join('. ');
  }

  function neighbour(lay: LoomLayout, from: LoomPlacement, key: string): string | undefined {
    const offsets: number[] = [];
    lay.laneRows.reduce((acc, rows, i) => ((offsets[i] = acc), acc + rows), 0);
    const v = (p: LoomPlacement) => (offsets[p.lane] ?? 0) + p.row;
    const all = lay.placements;
    const sorted = [...all].sort((a, b) => a.column - b.column || v(a) - v(b));
    const here = v(from);
    if (key === 'Home') return sorted[0]?.stepId;
    if (key === 'End') return sorted[sorted.length - 1]?.stepId;
    if (key === 'ArrowRight' || key === 'ArrowLeft') {
      const dir = key === 'ArrowRight' ? 1 : -1;
      const cols = all.map((p) => p.column).filter((c) => (c - from.column) * dir > 0);
      if (!cols.length) return undefined;
      const target = dir > 0 ? Math.min(...cols) : Math.max(...cols);
      const cand = all.filter((p) => p.column === target);
      cand.sort((a, b) => Math.abs(v(a) - here) - Math.abs(v(b) - here) || v(a) - v(b));
      return cand[0]?.stepId;
    }
    const same = all.filter((p) => p.column === from.column);
    if (key === 'ArrowDown') return same.filter((p) => v(p) > here).sort((a, b) => v(a) - v(b))[0]?.stepId;
    if (key === 'ArrowUp') return same.filter((p) => v(p) < here).sort((a, b) => v(b) - v(a))[0]?.stepId;
    return undefined;
  }

  function onNodeKey(e: KeyboardEvent, p: LoomPlacement) {
    if (!['ArrowRight', 'ArrowLeft', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(e.key)) return;
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    e.preventDefault();
    const target = neighbour(layout, p, e.key);
    if (target && target !== p.stepId) {
      focusId = target;
      nodeEls.get(target)?.focus();
    }
  }

  function reg(node: HTMLButtonElement, id: string) {
    nodeEls.set(id, node);
    return {
      destroy() {
        if (nodeEls.get(id) === node) nodeEls.delete(id);
      }
    };
  }

  const threadText = (t: WovenThread) => {
    const value = t.missing ? ' (not produced)' : t.value !== undefined ? ` = ${t.value}` : '';
    const to = t.drops.map((d) => `${d.to === '$outputs' ? 'workflow output' : d.to} (${d.label})`).join(', ');
    return `${t.expression}${value} → ${to || 'not consumed'}`;
  };

  // Edges of parallel branches, measured after render.
  function measure() {
    const stage = stageEl;
    const edges = parallel ? layout.edges : [];
    let next: string[] = [];
    if (stage && edges.length) {
      const box = stage.getBoundingClientRect();
      next = edges.map((e) => {
        const a = nodeEls.get(e.from)?.getBoundingClientRect();
        const b = nodeEls.get(e.to)?.getBoundingClientRect();
        if (!a || !b || !a.width || !b.width) return '';
        const x1 = a.right - box.left;
        const y1 = a.top + a.height / 2 - box.top;
        const x2 = b.left - box.left;
        const y2 = b.top + b.height / 2 - box.top;
        const mx = (x1 + x2) / 2;
        return `M${x1} ${y1} C ${mx} ${y1}, ${mx} ${y2}, ${x2} ${y2}`;
      });
    }
    if (next.join('|') !== untrack(() => edgePaths.join('|'))) edgePaths = next;
  }

  $effect(() => {
    void layout;
    void steps;
    if (!stageEl) return;
    queueMicrotask(measure);
    if (typeof ResizeObserver === 'undefined') return;
    const ro = new ResizeObserver(() => measure());
    ro.observe(stageEl);
    return () => ro.disconnect();
  });
</script>

{#snippet ringed(mark: string, size: number)}
  <svg
    class="ring"
    width={size}
    height={size}
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="2.4"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path d={RING} />
    <path d={mark} />
  </svg>
{/snippet}

<div class="loom" role="group" aria-label={label}>
  <div class="scroll" role="region" aria-label="{label}, diagram" tabindex="0">
    <div
      class="stage"
      style="grid-template-columns:{grid.template};min-width:{Math.max(740, 140 * grid.steps + 180)}px"
      bind:this={stageEl}
    >
      {#if edgePaths.length > 0}
        <svg class="edges" aria-hidden="true" focusable="false">
          <defs>
            <marker id="ah-{uid}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
              <path class="ah" d="M1 1 L9 5 L1 9" fill="none" stroke-width="1.6" />
            </marker>
          </defs>
          {#each edgePaths as d, i (i)}{#if d}<path class="edge" {d} marker-end="url(#ah-{uid})" />{/if}{/each}
        </svg>
      {/if}

      {#each lanes as l, i (i)}
        {@const r = laneRows[i]!}
        <div class="lane-head" style="grid-row:{r.header}">
          <span class="badge" style="--lane:{laneColor(l)}"><span class="bp">{l.protocol}</span><span class="bn">{l.name}</span></span>
          <span class="lt">{l.serviceLabel ? `${l.type} · ${l.serviceLabel}` : l.type}</span>
          {#if l.type === 'asyncapi'}<span>AsyncAPI, not runnable</span>{/if}
          {#if !l.linked}<span class="warn"><Icon name="warning" size={12} />Not linked</span>{/if}
        </div>
        {#if r.actions !== undefined}
          <div class="acts" aria-hidden="true" style="grid-row:{r.actions}">
            {#each steps as s, idx (s.stepId)}
              {@const pl = layout.placements[idx]}
              {#if pl && pl.lane === i}
                {@const c = grid.centre(pl.column + 1)}
                {@const end = s.actions?.find((a) => a.type === 'end')}
                {@const retry = s.actions?.find((a) => a.type === 'retry')}
                {#if end && end.type === 'end'}
                  {@const taken = end.taken === true}
                  {@const state = end.taken === undefined ? '' : `${taken ? 'taken' : 'not taken'}${end.runLabel ? ` in ${end.runLabel}` : ''}`}
                  <span class="end-label" style="left:calc({grid.pct(c)} + 6px)">end if {end.label ?? end.criteria}{#if state}<span class="note">{' '}· {state}</span>{/if}</span>
                  <span class="end-v" class:on={taken} style="left:{grid.pct(c)}"></span>
                  <span class="end-h" class:on={taken} style="left:{grid.pct(c)};width:{grid.pct(0.3)}"></span>
                  <span class="end-pill" class:on={taken} style="left:{grid.pct(c + 0.3)}"><Icon name="end" size={11} />end</span>
                {/if}
                {#if retry && retry.type === 'retry'}
                  {@const applied = !!retry.applied}
                  {@const x = grid.pct(c + 0.08)}
                  <svg class="loop" class:bad={applied} style="left:{x}" width="28" height="32" viewBox="0 0 28 32" fill="none" stroke-width="1.6" aria-hidden="true" focusable="false">
                    <path d="M5 32V15a9 9 0 0 1 18 0v13" />
                    <path d="M19 25l4 5 4-5" />
                  </svg>
                  <span class="retry-label" style="left:calc({x} + 32px)">retry ×{retry.retryLimit} · {seconds(retry.retryAfter)}{#if applied && s.attempts}<span class="bad">{' '}· {plural(s.attempts, 'attempt')}{retry.runLabel ? ` in ${retry.runLabel}` : ''}</span>{/if}</span>
                {/if}
              {/if}
            {/each}
          </div>
        {/if}
        {#each { length: layout.laneRows[i] ?? 1 } as _, k (k)}
          <div class="warp" aria-hidden="true" style="grid-row:{r.first + k};--lane:{laneColor(l)}"></div>
        {/each}
      {/each}

      {#if inputs.length > 0}
        <div class="io in" style="grid-row:{nodeFirst} / {nodeEnd}">
          <span class="io-h">inputs</span>
          {#each inputs as n (n.name)}
            {@const k = inputLens(n)}
            <button type="button" class="io-chip" class:on={lensOn(k.id)} class:dim={dimmed(k.id)} aria-pressed={lensOn(k.id)} onclick={() => pickLens(k.id)}>
              <span class="sr">Trace workflow input </span><span class="io-x">{n.expression ?? `$inputs.${n.name}`}</span>
              {#if n.value !== undefined}<span class="io-v">{n.value}</span>{/if}
            </button>
          {/each}
        </div>
      {/if}

      {#each steps as s, idx (s.stepId)}
        {@const pl = layout.placements[idx]}
        {#if pl}
          {@const r = laneRows[pl.lane]!}
          {@const source = lanes[pl.lane]}
          {@const desc = description(s, source)}
          {@const parts = operationParts(s)}
          {@const cut = parts.path.indexOf('/{')}
          {@const pathA = cut > 0 ? parts.path.slice(0, cut) : parts.path}
          {@const pathB = cut > 0 ? parts.path.slice(cut) : ''}
          {@const checks = checkRows(s.checks, validatesSchemas)}
          {@const lower = s.status || checks.length > 0 || s.result}
          <div class="cell" style="grid-column:{pl.column + 2};grid-row:{r.first + pl.row};--lane:{laneColor(source)}">
            <button
              type="button"
              class="node"
              data-status={s.status}
              aria-pressed={selectedStep === s.stepId}
              aria-describedby={desc ? `d-${uid}-${idx}` : undefined}
              tabindex={s.stepId === tabStop ? 0 : -1}
              use:reg={s.stepId}
              onclick={() => select(s.stepId)}
              onkeydown={(e) => onNodeKey(e, pl)}
              onfocus={() => (focusId = s.stepId)}
            >
              <span class="hd"><span class="num"><span class="sr">Step </span>{pl.number}</span> <span class="id">{s.stepId}</span></span>
              <span class="op">
                {#if parts.method}<span class="mt" data-method={parts.method} data-protocol={(source?.protocol ?? 'REST').toLowerCase()}>{parts.method}</span>{/if}
                <span class="pa">{pathA}</span>{#if pathB}<span class="pa">{pathB}</span>{/if}
              </span>
              {#if lower}<span class="rule" aria-hidden="true"></span>{/if}
              {#if s.status}
                <span class="st {s.status}">{@render ringed(STATUS_MARK[s.status], 13)}{statusPhrase(s)}</span>
              {/if}
              {#if checks.length > 0}
                <span class="checks">
                  {#each checks as k (k.label)}
                    <span class="chk {k.state}">{@render ringed(CHECK_MARK[k.state], 12)}{k.label}<span class="sr"> {k.word}</span></span>
                  {/each}
                </span>
              {/if}
              {#if s.result}<span class="res">{s.result}</span>{/if}
            </button>
            {#if desc}<span id="d-{uid}-{idx}" class="sr">{desc}</span>{/if}
          </div>
        {/if}
      {/each}

      {#if outputs.length > 0}
        <div class="io out" style="grid-row:{nodeFirst} / {nodeEnd}">
          <span class="io-h">outputs</span>
          {#each outputs as o (o.name)}
            {@const k = outputLens(o)}
            <button type="button" class="io-chip" class:on={lensOn(k.id)} class:dim={dimmed(k.id)} aria-pressed={lensOn(k.id)} onclick={() => pickLens(k.id)}>
              <span class="sr">Trace workflow output </span><span class="io-x">{o.name}</span>
              {#if o.value !== undefined}<span class="io-v">{o.value}</span>{:else}<span class="io-v none" aria-hidden="true">—</span><span class="sr">not produced</span>{/if}
            </button>
          {/each}
          {#if outputNote}<span class="io-note">{outputNote}</span>{/if}
        </div>
      {/if}

      {#if woven.length > 0}
        <div class="weft" style="grid-row:{nodeEnd}">
          {#each { length: grid.steps + 2 } as _, c (c)}
            <span class="heddle" aria-hidden="true" style="left:{grid.pct(grid.centre(c))}"></span>
          {/each}
          <ul class="wl" aria-label="Data threads">
            {#each woven as t (t.id)}
              {@const from = colOf(t.from)}
              {@const far = t.drops.reduce((m, d) => Math.max(m, colOf(d.to)), from)}
              {@const x = grid.pct(grid.centre(from))}
              <li class="thr" class:tall={t.drops.some((d) => !!d.bad)} class:on={lens === t.id} class:dim={!!lens && lens !== t.id}>
                <span class="tline" aria-hidden="true" style="left:{x};width:{grid.pct(grid.centre(far) - grid.centre(from))}"></span>
                <span class="tdot" aria-hidden="true" style="left:{x}"></span>
                <button type="button" class="tlabel" style="left:calc({x} + 8px)" aria-pressed={lens === t.id} onclick={() => pickLens(t.id)}>
                  <span class="sr">Trace </span><span class="tx">{t.expression}</span>
                  {#if t.missing}<span class="tv missing">— not produced</span>{:else if t.value !== undefined}<span class="tv">= {t.value}</span>{/if}
                </button>
                {#each t.drops as d, di (di)}
                  <span class="drop" class:idle={!!d.idle} class:bad={!!d.bad} style="left:{grid.pct(grid.centre(colOf(d.to)))};{d.bad ? `max-width:calc(${grid.pct(1)} - 8px)` : ''}">
                    <svg width="9" height="9" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
                      <path d={d.bad ? 'M2 2l6 6M8 2L2 8' : 'M1 5h7M5 2l3 3-3 3'} />
                    </svg>
                    <span class="sr">{d.to === '$outputs' ? ', to the workflow output ' : `, to step ${d.to}: `}</span>
                    {d.bad ? `${d.label} · got ${d.bad}` : d.label}
                  </span>
                {/each}
              </li>
            {/each}
          </ul>
        </div>
      {/if}
    </div>
  </div>
  <p class="sr">{woven.map(threadText).join('. ')}</p>
</div>

<style>
  .loom { display: block; color: var(--text); font: var(--type-body); }
  .loom *, .loom *::before, .loom *::after { box-sizing: border-box; }
  button:focus-visible, .scroll:focus-visible { outline: 2px solid var(--action); outline-offset: 2px; box-shadow: var(--shadow-focus); }
  .scroll { overflow-x: auto; padding: 4px 0; border-radius: var(--radius-panel); }
  .stage { position: relative; display: grid; align-items: center; }
  svg.edges { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; pointer-events: none; }
  path.ah { stroke: var(--border-control); }
  path.edge { fill: none; stroke: var(--border-control); stroke-width: 1.5; }
  .lane-head { grid-column: 2 / -1; display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 2px 0; font: 400 12px/16px var(--font-ui); color: var(--text-muted); }
  .badge { display: inline-flex; overflow: hidden; font: 400 11px/16px var(--font-mono); border: 1px solid var(--border); border-radius: 6px; }
  .badge > span { padding: 2px 7px; }
  .bp { background: var(--lane); color: var(--on-proto); font-weight: 600; }
  .bn { background: var(--surface); color: var(--text); }
  .lt { font-family: var(--font-mono); }
  .lane-head .warn { display: inline-flex; align-items: center; gap: 4px; color: var(--warning); font-weight: 600; }
  .acts { grid-column: 1 / -1; position: relative; height: 54px; font: 400 11px/14px var(--font-mono); color: var(--text-secondary); }
  .acts > * { position: absolute; white-space: nowrap; }
  .end-label { top: 0; }
  .note { color: var(--text-muted); }
  .end-v { top: 27px; height: 27px; border-left: 1.5px dashed var(--text-secondary); }
  .end-h { top: 27px; border-top: 1.5px dashed var(--text-secondary); }
  .end-v.on, .end-h.on { border-style: solid; border-color: var(--success); }
  .end-pill { top: 18px; display: inline-flex; align-items: center; gap: 5px; padding: 1px 8px; color: var(--text-secondary); background: var(--canvas); border: 1px solid currentColor; border-radius: 999px; }
  .end-pill.on { color: var(--success); }
  svg.loop { top: 20px; stroke: var(--text-secondary); }
  svg.loop.bad { stroke: var(--danger); }
  .retry-label { top: 20px; }
  .retry-label .bad { color: var(--danger); font-weight: 600; }
  .warp { grid-column: 2 / -2; position: relative; align-self: stretch; }
  .warp::before { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 2px; margin-top: -1px; background: var(--lane); }
  .warp::after { content: ''; position: absolute; left: 0; right: 0; top: 50%; height: 8px; margin-top: -4px; background: radial-gradient(circle 4px at 4px 50%, var(--lane) 99%, transparent), radial-gradient(circle 4px at calc(100% - 4px) 50%, var(--lane) 99%, transparent); }
  .io { grid-column: 1; position: relative; z-index: 1; display: flex; flex-direction: column; align-items: flex-start; gap: 6px; min-width: 0; padding: 0 2px; }
  .io.out { grid-column: -2; align-items: flex-end; }
  .io-h { font: 400 12px/16px var(--font-ui); color: var(--text-muted); }
  .io-chip { display: flex; flex-direction: column; align-items: flex-start; gap: 1px; max-width: 100%; padding: 4px 6px; font: 400 11px/14px var(--font-mono); text-align: left; color: var(--text); background: var(--canvas); border: 1px solid var(--border); border-radius: 6px; cursor: pointer; }
  .io-chip.on { background: var(--selected); border-color: var(--action); }
  .io-chip.on .io-x { color: var(--link); }
  .io-chip.dim .io-x { color: var(--text-secondary); }
  .io-x { font-weight: 600; overflow-wrap: anywhere; }
  .io-v { color: var(--text-secondary); overflow-wrap: anywhere; }
  .io-v.none { color: var(--text-muted); }
  .io-note { max-width: 100%; font: 400 11px/1.4 var(--font-ui); color: var(--text-muted); text-align: right; }
  .cell { position: relative; z-index: 1; display: flex; justify-content: center; min-width: 0; padding: 0 5px; }
  .node { position: relative; width: 100%; max-width: 190px; display: flex; flex-direction: column; align-items: stretch; gap: 6px; padding: 8px; text-align: left; color: var(--text); background: var(--surface); border: 1px solid var(--border-strong); border-radius: 12px; cursor: pointer; font: inherit; }
  .node[data-status="failed"] { border-color: var(--danger); }
  .node[data-status="not-run"] { background: var(--canvas); border-style: dashed; }
  .node[data-status="not-run"] .id, .node[data-status="not-run"] .num { color: var(--text-secondary); }
  .node[aria-pressed="true"] { padding: 7px; background: var(--selected); border: 2px solid var(--action); }
  .hd { display: flex; align-items: center; gap: 6px; min-width: 0; }
  .num { flex: none; display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 999px; background: var(--raised); color: var(--text); font: 600 11px/1 var(--font-ui); }
  .node[data-status="failed"] .num { background: var(--danger); color: var(--on-status); }
  .id { min-width: 0; font: 600 12px/normal var(--font-mono); overflow-wrap: anywhere; }
  .op { display: flex; flex-wrap: wrap; align-items: center; gap: 2px 4px; font: 400 11px/normal var(--font-mono); }
  .mt { --method-bg: var(--lane, var(--proto-rest)); flex: none; padding: 1px 4px; font: 600 10px/normal var(--font-mono); color: var(--on-proto); background: var(--method-bg); border-radius: 3px; }
  .mt[data-protocol="rest"][data-method="POST" i], .mt[data-protocol="rest"][data-method="PATCH" i], .mt[data-protocol="rest"][data-method="PUT" i] { --method-bg: var(--proto-event); }
  .mt[data-protocol="rest"][data-method="DELETE" i] { --method-bg: var(--danger); color: var(--on-status); }
  .pa { color: var(--text-secondary); overflow-wrap: anywhere; }
  .rule { height: 1px; background: var(--border); }
  .ring { flex: none; }
  .st { display: inline-flex; align-items: center; gap: 5px; font: 600 12px/normal var(--font-ui); }
  .st.passed { color: var(--success); }
  .st.failed { color: var(--danger); }
  .st.not-run { color: var(--text-muted); }
  .checks { display: flex; flex-direction: column; gap: 3px; font: 400 11px/14px var(--font-ui); }
  .chk { display: inline-flex; align-items: center; gap: 5px; color: var(--text-secondary); }
  .chk.passed .ring { color: var(--success); }
  .chk.failed, .chk.failed .ring { color: var(--danger); }
  .chk.off, .chk.not-evaluated { color: var(--text-muted); }
  .res { font: 400 10px/normal var(--font-mono); font-variant-numeric: tabular-nums; color: var(--text-muted); }
  .weft { grid-column: 1 / -1; position: relative; padding: 10px 0 6px; }
  .heddle { position: absolute; top: 0; bottom: 0; width: 1px; background: var(--border); }
  .wl { position: relative; list-style: none; margin: 0; padding: 0; }
  .thr { position: relative; height: 46px; }
  .thr.tall { height: 64px; }
  .tline, .tdot { transition: opacity .15s; }
  .thr.dim .tline, .thr.dim .tdot { opacity: .28; }
  .thr.dim .tx, .thr.dim .drop { color: var(--text-muted); }
  .thr.dim .drop { border-color: var(--border); }
  .thr.on .tline { height: 2px; background: var(--action); }
  .thr.on .tdot { background: var(--action); }
  .thr.on .tx { color: var(--link); font-weight: 600; }
  .thr.on .drop:not(.bad) { border-color: var(--action); }
  .thr.on .drop:not(.bad):not(.idle) { color: var(--text); }
  .tline { position: absolute; top: 30px; height: 1.5px; background: var(--text-muted); }
  .tdot { position: absolute; top: 26px; width: 10px; height: 10px; margin-left: -5px; border-radius: 999px; background: var(--text-muted); }
  .tlabel { position: absolute; top: 2px; display: inline-flex; align-items: baseline; gap: 6px; padding: 0 4px; white-space: nowrap; font: 400 11px/16px var(--font-mono); color: inherit; background: var(--canvas); border: 0; border-radius: 4px; cursor: pointer; }
  .tx { color: var(--text-secondary); }
  .tv { color: var(--text-muted); }
  .tv.missing { color: var(--danger); }
  .drop { position: absolute; top: 21px; transform: translateX(-50%); display: inline-flex; align-items: center; gap: 4px; padding: 1px 7px; white-space: nowrap; font: 400 11px/16px var(--font-mono); color: var(--text-secondary); background: var(--canvas); border: 1px solid var(--border-strong); border-radius: 999px; }
  .drop.idle { color: var(--text-muted); }
  .drop.bad { color: var(--danger); border-color: var(--danger); white-space: normal; text-align: center; border-radius: 10px; }
  .sr { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; border: 0; }
  @media (prefers-reduced-motion: reduce) { .tline, .tdot { transition: none; } }
</style>
