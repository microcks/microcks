<script lang="ts">
  import { ApiBadge, Button, Facts, FactRow, Icon } from '@microcks/deep-ink';
  import {
    arazzoAttempt,
    arazzoRunId,
    arazzoStep,
    arazzoWorkflow,
    clockTime,
    isMiss,
    shortPath,
    statusLabel,
    traceEvents,
    traceIds,
    type TraceCall
  } from './traces.js';

  interface Props {
    call: TraceCall;
    replayNote: string;
    onClose: () => void;
    onReplay: () => void;
  }

  let { call: c, replayNote, onClose, onReplay }: Props = $props();

  const miss = $derived(isMiss(c));
  const isEvent = $derived(c.kind === 'EVENT');
  const runId = $derived(arazzoRunId(c));
  const workflowId = $derived(arazzoWorkflow(c));
  const ids = $derived(traceIds(c.seq));
  const title = $derived(c.kind === 'MCP' ? `tools/call · ${c.mcpTool ?? ''}` : isEvent ? `SUBSCRIBE ${c.endpointLabel}` : shortPath(c));
  const tone = $derived(c.kind === 'EVENT' ? 'event' : c.kind === 'MCP' ? 'mcp' : ['PATCH', 'PUT', 'POST'].includes(c.method) ? 'event' : 'rest');
  const request = $derived(c.rawRequest.replace('Accept: application/json', `Accept: application/json\ntraceparent: 00-${ids.traceId}-${ids.spanId}-01`));
</script>

<section aria-labelledby="tr2-detail-title" class="split-pane-side tr2-detail">
  <div class="tr2-detail-head">
    <div class="tr2-detail-id">
      <div class="tr2-detail-title">
        <span class="tr2-method" data-size="md" data-tone={tone}>{c.method}</span>
        <h2 id="tr2-detail-title">{title}</h2>
      </div>
      <span class="tr2-detail-meta">
        {#if c.kind === 'MCP'}
          <ApiBadge label="MCP" value={c.serviceName} size="sm" />
        {:else}
          <ApiBadge protocol={isEvent ? 'EVENT' : 'REST'} name={c.serviceName} size="sm" />
        {/if}
        <span class="tr2-time">{clockTime(c.at)}</span>
      </span>
    </div>
    <button type="button" class="tr2-close" aria-label="Close call detail" onclick={onClose}><Icon name="close" size={14} /></button>
  </div>

  <Facts layout="columns" boxed="surface" size="sm" class="tr2-facts">
    <FactRow term="Client"><span class="demo-mono">{c.clientName} · {c.clientIp}</span></FactRow>
    <FactRow term="Dispatcher"><span class="demo-mono">{c.dispatcher}</span></FactRow>
    <FactRow term="Dispatch criteria"><span class="demo-mono" style="color: var(--warning)">{c.criteria}</span></FactRow>
    <FactRow term="Matched example"><span class="tr2-example-name" style:color={miss ? 'var(--warning)' : undefined}>{c.exampleName ?? 'none'}</span></FactRow>
    <FactRow term="Status · latency">
      <span class="tr2-status" data-miss={miss ? '' : undefined}>
        <Icon name={miss ? 'warning' : 'check'} size={13} />
        <span class="demo-mono">{statusLabel(c)} · {c.ms} ms</span>
      </span>
    </FactRow>
    <FactRow term="Trace id"><span class="tr2-trace-id demo-mono">{ids.traceId}</span></FactRow>
  </Facts>

  {#if c.baggage}
    <div role="note" aria-label="Arazzo run of this call" class="tr2-note">
      <div class="tr2-note-line">
        <span class="tr2-arazzo-tag">ARAZZO</span>
        <span>
          From workflow <a class="tr2-strong-link" href={`/workflows/pastry/${workflowId ?? 'restock-pastry'}/loom`}>{c.workflowName ?? workflowId}</a>
          · <a href={`/workflows/runs/${runId ?? ''}`}>run #{c.arazzoRun}</a> · step <span class="demo-mono">{arazzoStep(c)}</span> · attempt {arazzoAttempt(c) ?? '1'}
        </span>
      </div>
      <span class="tr2-note-label">W3C baggage</span>
      <code class="tr2-note-code">{c.baggage}</code>
    </div>
  {/if}
  {#if c.sessionId}
    <div role="note" aria-label="Arazzo session of this call" class="tr2-note" data-variant="session">
      <span>Session <span class="demo-mono tr2-strong">{c.sessionId}</span></span>
      <span class="tr2-secondary">
        verification of <a class="tr2-strong-link" href="/workflows/pastry/restock-pastry/loom">Restock a pastry</a> · the mocks answer along the workflow
      </span>
      <code class="tr2-note-code tr2-muted">x-microcks-session: {c.sessionId}</code>
    </div>
  {/if}
  {#if miss}
    <div role="note" class="tr2-note" data-variant="miss">
      <Icon name="warning" size={14} />
      <span>
        No example of <span class="demo-mono">{c.method} {c.endpointLabel}</span> matches <span class="demo-mono">{c.criteria}</span>. Add an example or let the AI Copilot propose one.
      </span>
    </div>
  {/if}

  <div class="tr2-raw">
    <div class="tr2-raw-head">
      <span class="tr2-raw-label">{isEvent ? 'Subscription' : c.kind === 'MCP' ? 'MCP request' : 'Request'}</span>
      <span class="demo-mono tr2-secondary">{c.clientName}</span>
    </div>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
    <pre tabindex="0" aria-label="Request">{request}</pre>
  </div>
  <div class="tr2-raw" data-part="response">
    <div class="tr2-raw-head">
      <span class="tr2-raw-label">{isEvent ? 'Message' : 'Response'}</span>
      <span class="tr2-raw-status" data-miss={miss ? '' : undefined}>{statusLabel(c)}</span>
    </div>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
    <pre tabindex="0" aria-label="Response">{c.rawResponse}</pre>
  </div>

  <div class="tr2-events">
    <h3 class="section-label">Span events</h3>
    {#each traceEvents(c) as e, i (i)}
      <div class="tr2-event"><span class="tr2-event-at">{e.at}</span><span class="tr2-event-text">{e.text}</span></div>
    {/each}
  </div>

  <div class="tr2-actions">
    <Button variant="primary" icon="replay" onclick={onReplay}>Replay this call</Button>
    <Button icon="external" href={`/services/${c.serviceId}`}>Open service</Button>
  </div>
  <p role="status" class="tr2-replayed">
    {#if replayNote}<Icon name="check" size={13} />{replayNote}{/if}
  </p>
</section>
