<script lang="ts">
  import { ApiBadge, Button, Icon, LiveIndicator, SummaryStrip } from '@microcks/deep-ink';
  import {
    AGENT_TRANSPORT_NAMES,
    agentCellName,
    agentRunnerLog,
    fmtMs,
    totalsOf,
    type AgentTestRun,
    type TransportId
  } from '#lib/runs.js';
  import AgentCellDetail from './AgentCellDetail.svelte';
  import CiPanel from './CiPanel.svelte';
  import ConformanceMatrix, { cellKey, type CheckResult, type MatrixGroup, type MatrixRow } from './ConformanceMatrix.svelte';
  import RunProgress, { type RunSegment } from './RunProgress.svelte';
  import RunnerLog from './RunnerLog.svelte';
  import SectionLabel from './SectionLabel.svelte';

  interface Props {
    run: AgentTestRun;
    phase: 'running' | 'done';
    tick: number;
    /** "skill recommend-pastry · 1 turn · expects completed", by conversation id. */
    conversationSubs: Record<string, string>;
    onstep: () => void;
    onreplay: () => void;
    onedit: () => void;
    onannounce: (message: string) => void;
  }

  let { run, phase, tick, conversationSubs, onstep, onreplay, onedit, onannounce }: Props = $props();

  const CARD_ROWS = [
    { id: 'schema', label: 'Schema · required fields', sub: 'A2A v1.0 JSON Schema · name, version, supportedInterfaces, capabilities, skills…', perTransport: false },
    { id: 'iface', label: 'Interfaces reachable', sub: 'each supportedInterfaces entry answers with A2A-Version: 1.0', perTransport: true },
    { id: 'caps', label: 'Capabilities honoured', sub: 'streaming and pushNotifications declared true are exercised', perTransport: true },
    { id: 'sig', label: 'Signature (JWS)', sub: 'optional in v1.0 · absent is a warning, not an error', perTransport: false }
  ];
  const TRANSPORT_SUB: Record<TransportId, string> = { jsonrpc: 'preferred · SSE', http: 'SSE', grpc: 'stream' };
  const SEGMENT: Record<CheckResult, RunSegment> = { pass: 'done', fail: 'failed', warn: 'warn', running: 'current', queued: 'queued' };
  const CLI =
    "microcks test 'Pastry concierge:1.0' https://concierge.staging.acme.io A2A_CONFORMANCE --microcksURL=http://microcks.local:8080/api/ --waitFor=30sec";

  const running = $derived(phase === 'running');
  const n = $derived(run.checks.length);
  const shown = $derived(running ? tick : n);
  let chosen = $state<string | null>(null);
  let ciOpen = $state(false);
  let area = $state<HTMLElement>();

  const firstFail = $derived(run.failures[0]);
  const last = $derived(run.checks.slice(0, shown).at(-1));
  const autoKey = $derived(
    !running && firstFail ? cellKey(firstFail.row, firstFail.transport) : last ? cellKey(last.row, last.transport) : ''
  );
  const selectedKey = $derived(chosen === null ? autoKey : chosen);
  const selectedIndex = $derived(selectedKey ? run.checks.findIndex((c) => cellKey(c.row, c.transport) === selectedKey) : -1);
  const selectedCheck = $derived(selectedIndex >= 0 && selectedIndex < shown ? run.checks[selectedIndex] : undefined);
  const detail = $derived(selectedCheck ? run.details[selectedKey] : undefined);
  const { transports, conversations } = $derived(run.setup);
  const columns = $derived(transports.map((t) => ({ id: t, label: AGENT_TRANSPORT_NAMES[t], sub: TRANSPORT_SUB[t] })));

  const indexOf = (row: string, transport: string) => run.checks.findIndex((c) => c.row === row && c.transport === transport);

  function resultAt(i: number): CheckResult {
    const c = run.checks[i];
    if (!c) return 'queued';
    if (i < shown) return c.result;
    return running && i === shown ? 'running' : 'queued';
  }

  function cellsFor(row: string, perTransport: boolean): MatrixRow['cells'] {
    const cell = (transport: string) => {
      const i = indexOf(row, transport);
      const result = resultAt(i);
      const finished = result === 'pass' || result === 'fail' || result === 'warn';
      return {
        columnId: transport === '*' ? undefined : transport,
        result,
        label: result === 'warn' ? 'Absent' : undefined,
        duration: finished ? fmtMs(run.checks[i]?.ms ?? 0) : undefined
      };
    };
    return perTransport ? transports.map((t) => cell(t)) : [cell('*')];
  }

  const groups = $derived<MatrixGroup[]>([
    {
      id: 'card',
      label: 'Agent Card',
      sub: '/.well-known/agent-card.json · checked once, then per interface',
      rows: CARD_ROWS.map((r) => ({ id: r.id, label: r.label, sub: r.sub, cells: cellsFor(r.id, r.perTransport) }))
    },
    {
      id: 'conversations',
      label: 'Behaviour · conversations',
      sub: `${conversations.length} replayed · task states, artifacts, streaming, loose text match`,
      rows: conversations.map((c) => ({ id: c, label: c, sub: conversationSubs[c], mono: true, cells: cellsFor(c, true) }))
    }
  ]);
  const footer = $derived({
    label: 'Conversations passed',
    cells: transports.map((t) => {
      const fin = conversations.filter((c) => indexOf(c, t) < shown);
      const ok = fin.filter((c) => run.checks[indexOf(c, t)]?.result === 'pass').length;
      return {
        text: fin.length ? `${ok}/${conversations.length}` : `—/${conversations.length}`,
        tone: fin.length && ok < fin.length ? ('danger' as const) : fin.length === conversations.length ? ('success' as const) : undefined
      };
    })
  });

  const states = $derived(run.checks.map((_c, i) => resultAt(i)));
  const count = (r: CheckResult) => states.filter((s) => s === r).length;
  const all = $derived(totalsOf(run.checks));
  const cardPassed = $derived(
    CARD_ROWS.map((r) => run.checks.filter((c) => c.row === r.id)).filter((cs) => cs.length > 0 && cs.every((c) => c.result === 'pass')).length
  );
  const ratio = $derived(all.conversationPassed / Math.max(1, all.conversationTotal));
  const scoreLevel = $derived(all.conversationFailed === 0 ? 'high' : ratio >= 0.8 ? 'mid' : 'low');
  const current = $derived(running ? run.checks[tick] : undefined);
  const log = $derived(agentRunnerLog(run, shown, running));

  // While running, only failures are announced, never each check.
  $effect(() => {
    if (running && last?.result === 'fail') onannounce(`${agentCellName(last.row, last.transport)} failed.`);
  });
  $effect(() => {
    if (!running) onannounce(`Test finished. ${all.conversationPassed} of ${all.conversationTotal} conversation checks passed.`);
  });

  function closeDetail() {
    const cell = area?.querySelector<HTMLButtonElement>('button.matrix-cell[aria-pressed="true"]');
    chosen = '';
    cell?.focus();
  }
</script>

<SummaryStrip variant="canvas" class="setup-strip">
  <SectionLabel lineHeight="normal">Setup</SectionLabel>
  <span class="setup-strip-text">{transports.map((t) => AGENT_TRANSPORT_NAMES[t]).join(' · ')}</span>
  <span class="setup-strip-text">{conversations.length} {conversations.length === 1 ? 'conversation' : 'conversations'}</span>
  <span><span class="muted">secret</span> <span class="mono">{run.setup.secret || 'none'}</span></span>
  <span><span class="muted">timeout</span> <span class="mono">{run.setup.timeoutMs} ms</span></span>
  <span><span class="muted">text</span> <span class="mono">{run.setup.textMode}{run.setup.dataSchema ? ' + JSON Schema' : ''}</span></span>
  {#if !running}<Button size="sm" icon="pencil" class="edit-setup" onclick={onedit}>Edit setup</Button>{/if}
</SummaryStrip>

<div class="run-top">
  {#if running}
    <RunProgress
      class="run-top-main"
      done={tick}
      total={n}
      caption="matrix cells filled"
      nowLabel="Now checking"
      now={current ? agentCellName(current.row, current.transport) : 'Computing the conformance score…'}
      segments={states.map((s) => SEGMENT[s])}
      gap={3}
      counts={[
        { kind: 'pass', text: `${count('pass')} passed` },
        { kind: 'fail', text: `${count('fail')} failed` },
        { kind: 'warn', text: `${count('warn')} warning` },
        { kind: 'pending', text: `${count('queued') + count('running')} queued` }
      ]}
    >
      {#snippet action()}<Button size="sm" icon="chevron" class="step-forward" onclick={onstep}>Step forward</Button>{/snippet}
      {#snippet note()}Agent Card checks run first. Each conversation then runs once per transport, with a fresh <span class="mono">contextId</span> per run and the OAuth2 token from the secret.{/snippet}
    </RunProgress>
  {:else}
    <section aria-label="Summary" class="run-summary run-top-main">
      <div class="run-summary-head">
        <div class="run-score">
          <span class="run-score-value" data-level={scoreLevel}>{all.conversationPassed}<span class="run-score-total">/{all.conversationTotal}</span></span>
          <span class="run-score-caption">conversation checks passed · {conversations.length} conversations × {transports.length} transports</span>
        </div>
        <div class="run-summary-text">
          <span class="run-summary-line">
            {all.conversationPassed} of {all.conversationTotal} checks passed{all.conversationFailed
              ? ` · ${all.conversationFailed} failed on the task contract, each on a single transport`
              : ' on every transport'}
          </span>
          <span class="run-summary-card">
            <span class="muted">Agent Card</span>
            <span data-tone="success"><Icon name="check" size={12} />{cardPassed} passed</span>
            {#if all.warn}<span data-tone="warning"><Icon name="warning" size={12} />{all.warn} warning · signature absent</span>{/if}
          </span>
          {#each run.failures as f (`${f.row}|${f.transport}`)}
            <button type="button" class="failure-link" onclick={() => (chosen = cellKey(f.row, f.transport))}>
              <Icon name="cross" size={13} />
              <span><span class="failure-link-cell">{AGENT_TRANSPORT_NAMES[f.transport]} × {f.row}</span> · {f.cause}</span>
            </button>
          {/each}
        </div>
      </div>
      <div class="transport-scores">
        <span class="transport-scores-caption">Score per transport · one segment per conversation</span>
        <div class="transport-scores-row">
          {#each transports as t (t)}
            {@const results = conversations.map((c) => run.checks[indexOf(c, t)]?.result ?? 'pass')}
            {@const ok = results.filter((r) => r === 'pass').length}
            <div class="transport-score">
              <div class="transport-score-head">
                <span class="transport-score-name">{AGENT_TRANSPORT_NAMES[t]}</span>
                <span class="transport-score-value" data-tone={ok === conversations.length ? 'success' : 'danger'}>{ok}/{conversations.length}</span>
              </div>
              <div class="transport-score-segs" aria-hidden="true">
                {#each results as r, i (i)}<span data-result={r}></span>{/each}
              </div>
            </div>
          {/each}
        </div>
      </div>
      <div class="run-actions">
        <Button variant="primary" href="/services">Open agent</Button>
        <Button icon="play" onclick={onreplay}>Run again</Button>
        <Button href="/tests">All runs</Button>
        <Button icon="code" class="ci-panel-toggle" aria-expanded={ciOpen} aria-controls="ci-panel" onclick={() => (ciOpen = !ciOpen)}>Run it from CI</Button>
      </div>
      {#if ciOpen}
        <CiPanel id="ci-panel" dashed copyLabel="Copy command" title="Run this test in your pipeline" command={CLI}>
          {#snippet pill()}<ApiBadge label="cli" value="proposed" status="warning" size="sm" />{/snippet}
          {#snippet hint()}Proposed, not available today: the existing <span class="mono">microcks test</span> command, unchanged, with the new runner name
            <span class="mono">A2A_CONFORMANCE</span> that the A2A module would register. It exits non-zero when a check fails.{/snippet}
          {#snippet footnote()}Add <span class="mono">--keycloakClientId</span> and <span class="mono">--keycloakClientSecret</span> when your Microcks instance has
            authentication on. The agent's OAuth2 token comes from the secret bound to the test, as in this run.{/snippet}
        </CiPanel>
      {/if}
    </section>
  {/if}
  <RunnerLog
    class="run-top-log"
    lines={log.map((l) => ({ time: l.time, level: l.level, text: l.text, emphasis: l.tone === 'emphasis', error: l.tone === 'error' }))}
  >
    {#snippet status()}{#if running}<LiveIndicator variant="inline" label="live · streaming" />{:else}{log.length} lines · completed{/if}{/snippet}
  </RunnerLog>
</div>

<section aria-labelledby="matrix-title" class="matrix-section" bind:this={area}>
  <div class="matrix-section-head">
    <h2 id="matrix-title">Conformance matrix</h2>
    <span class="matrix-section-hint">checks × transports · select a finished cell to see the exchange</span>
    {#if running}<LiveIndicator variant="inline" label="live · filling" />{/if}
  </div>
  <ConformanceMatrix
    caption="Checks of run #{run.id}: Agent Card, then conversations, by transport"
    {columns}
    {groups}
    {footer}
    selectedKey={detail ? selectedKey : undefined}
    onselect={(k) => (chosen = k ?? '')}
  />
  {#if detail && selectedCheck}
    <AgentCellDetail {detail} check={selectedCheck} cardRow={CARD_ROWS.some((r) => r.id === selectedCheck.row)} onclose={closeDetail} />
  {:else}
    <p class="cell-detail-empty">
      Cells fill as checks finish. Select a finished cell to see what Microcks sent, what the agent answered, and why the check passed or failed.
    </p>
  {/if}
</section>
