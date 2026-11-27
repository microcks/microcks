<script lang="ts">
  import { Button, Icon } from '@microcks/deep-ink';
  import Panel from './Panel.svelte';
  import { simulate, type SimResult, type StepKind } from './dispatch.js';
  import { exampleUrlPath } from './snippets.js';
  import type { Behavior, CatalogOperation } from './types.js';

  interface Props {
    op: CatalogOperation;
    protocol: string;
    mockBase: string;
    draft: Behavior;
  }

  let { op, protocol, mockBase, draft }: Props = $props();

  const presets = $derived([...new Set(op.examples.map((e) => exampleUrlPath(op.path, e.request.params)))].slice(0, 4));
  let typed = $state<string | undefined>();
  let path = $state<string | undefined>();
  let sendKey = $state(true);
  let history = $state<{ id: number; path: string; code: string; what: string }[]>([]);
  let status = $state('');
  const input = $derived(typed ?? presets[0] ?? op.path);
  const current = $derived(path ?? presets[0] ?? op.path);
  const sim = $derived<SimResult>(
    simulate(op, draft, { call: current, headers: sendKey ? { 'x-api-key': 'demo-7f3a' } : undefined })
  );
  const kindOf = (r: SimResult) => (r.status === null ? 'fwd' : r.status < 300 ? 'ok' : 'fail');
  const kind = $derived(kindOf(sim));
  const STEP_ICON: Record<StepKind, 'check' | 'cross' | 'warning' | 'minus-circle' | 'info'> = { ok: 'check', fail: 'cross', warn: 'warning', skip: 'minus-circle', info: 'info' };
  const STATUS_ICON = { ok: 'check', fail: 'cross', fwd: 'arrow-right' } as const;

  function what(r: SimResult) {
    if (r.outcome === 'matched') return r.exampleId ? (op.examples.find((e) => e.id === r.exampleId)?.name ?? 'example') : 'example';
    if (r.outcome === 'constraint') return 'constraint';
    if (r.outcome === 'forwarded') return 'proxied';
    return 'no match';
  }

  function run(target: string) {
    typed = target;
    path = target;
    const r = simulate(op, draft, { call: target, headers: sendKey ? { 'x-api-key': 'demo-7f3a' } : undefined });
    history = [{ id: Date.now() + history.length, path: target, code: r.status === null ? 'fwd' : String(r.status), what: what(r) }, ...history].slice(0, 4);
    status = `Simulation: ${r.statusLabel}.`;
  }
</script>

<Panel class="sim" padding="lg">
  {#snippet title()}<Icon name="play" size={16} class="sim-title-icon" />Request simulator{/snippet}
  <p class="panel-lead">Dry run against the configuration on this page, saved or not. Nothing reaches the live mock.</p>
  <form class="sim-form" onsubmit={(e) => { e.preventDefault(); run(input); }}>
    <div class="sim-field">
      <label for="sim-path" class="section-label">Request</label>
      <div class="sim-request" style:--sim-proto={`var(--proto-${protocol.toLowerCase()}, var(--proto-rest))`}>
        <span class="sim-method">{op.method}</span>
        <input id="sim-path" class="input sim-input" type="text" spellcheck="false" value={input} oninput={(e) => (typed = e.currentTarget.value)} aria-describedby="sim-base" />
      </div>
      <span id="sim-base" class="sim-base">{mockBase} + path</span>
    </div>
    {#if presets.length}
      <div class="sim-presets" role="group" aria-label="Request examples">
        {#each presets as p (p)}<button type="button" class="sim-preset" onclick={() => run(p)}>{p}</button>{/each}
      </div>
    {/if}
    <div class="sim-send">
      <label class="choice sim-key"><input type="checkbox" class="checkbox" bind:checked={sendKey} /><span>Send header <code>x-api-key: demo-7f3a</code></span></label>
      <Button type="submit" variant="primary" icon="play">Simulate</Button>
    </div>
  </form>

  <div class="sim-result" aria-label="Simulation result" role="group">
    <div class="sim-result-head">
      <span class="sim-line">{sim.requestLine}</span>
      <span class="sim-status" data-kind={kind}><Icon name={STATUS_ICON[kind]} size={12} />{sim.statusLabel}</span>
    </div>
    <div class="sim-chain">{sim.chain}</div>
    <ol class="sim-steps">
      {#each sim.steps as s, i (i)}
        <li class="sim-step" data-kind={s.kind}>
          <span class="sim-step-icon"><Icon name={STEP_ICON[s.kind]} size={14} /></span>
          <span class="sim-step-text"><span class="sim-step-label">{s.label}</span><span class="sim-step-value">{s.value}</span></span>
        </li>
      {/each}
    </ol>
    <div class="sim-response">
      <div class="sim-response-head"><span>Response</span><span class="sim-media">{sim.mediaType}</span></div>
      <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
      <pre class="sim-body" data-kind={kind} role="region" aria-label="Response body" tabindex="0">{sim.body}</pre>
    </div>
  </div>

  {#if history.length}
    <div class="sim-history">
      <div class="section-label sim-history-head">
        <h3 class="sim-history-title">Recent runs</h3>
        <button type="button" class="sim-clear" onclick={() => (history = [])}>Clear</button>
      </div>
      <ul class="sim-history-list">
        {#each history as h (h.id)}
          <li>
            <button type="button" class="sim-run" onclick={() => run(h.path)}>
              <span class="sim-run-code" data-code={h.code === '200' ? 'ok' : h.code === 'fwd' ? 'fwd' : 'fail'}>{h.code}</span>
              <span class="sim-run-path">{h.path}</span>
              <span class="sim-run-what">{h.what}</span>
            </button>
          </li>
        {/each}
      </ul>
    </div>
  {/if}
  <p class="sr-only" role="status" aria-live="polite">{status}</p>
</Panel>
