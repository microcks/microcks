<script lang="ts">
  import { Icon } from '@microcks/deep-ink';
  import { fmt } from '#lib/format.js';
  import type { FlowCounts, TopologyEdge, TraceCall, TraceTopology } from './traces.js';

  interface Props {
    topology: TraceTopology;
    counts: FlowCounts;
    filter: { serviceId: string; kind: string; status: '' | 'ok' | 'error' };
    focus: string;
    onFocus: (id: string) => void;
    /** The latest call: its edges are drawn in the live colour for a moment. */
    flash?: TraceCall;
  }

  let { topology, counts, filter, focus, onFocus, flash }: Props = $props();

  // Graph geometry: 56px nodes on a 72px slot, six slots per 416px column.
  const SLOT = 72;
  const NODE = 56;
  const HEIGHT = 416;
  const yOf = (len: number, idx: number) => ((6 - len) * SLOT) / 2 + idx * SLOT + NODE / 2;

  interface EdgeView {
    id: string;
    from: string;
    to: string;
    d: string;
    top: number;
    tone: 'rest' | 'mcp' | 'event' | 'miss' | 'live';
    width: number;
    on: boolean;
    count: number;
  }

  const clients = $derived(topology.clients);
  const endpoints = $derived(topology.endpoints);
  const examples = $derived(topology.examples);
  const clientEdges = $derived(topology.clientEdges);
  const exampleEdges = $derived(topology.exampleEdges);
  const endpoint = (id: string) => endpoints.find((e) => e.id === id);
  const example = (id: string) => examples.find((e) => e.id === id);

  const edges = $derived.by(() => {
    const svcOk = (epId: string) => !filter.serviceId || endpoint(epId)?.serviceId === filter.serviceId;
    const kindOk = (e: TopologyEdge) => !filter.kind || e.kind === filter.kind;
    const exStatusOk = (exId: string) => !filter.status || (filter.status === 'ok' ? Boolean(example(exId)?.ok) : !example(exId)?.ok);
    const epHasStatus = (epId: string) => exampleEdges.some((e) => e.from === epId && exStatusOk(e.to));
    const f = focus;
    const focusOk1 = (e: TopologyEdge) => !f || f === e.from || f === e.to || exampleEdges.some((x) => x.from === e.to && x.to === f);
    const focusOk2 = (e: TopologyEdge) => !f || f === e.from || f === e.to || clientEdges.some((x) => x.to === e.from && x.from === f && kindOk(x));
    const active1 = (e: TopologyEdge) => svcOk(e.to) && kindOk(e) && epHasStatus(e.to) && focusOk1(e);
    const active2 = (e: TopologyEdge) => svcOk(e.from) && exStatusOk(e.to) && clientEdges.some((x) => x.to === e.from && kindOk(x)) && focusOk2(e);

    const count1 = (e: TopologyEdge) => counts.clientEdge[`${e.from}>${e.to}`] ?? 0;
    const count2 = (e: TopologyEdge) => counts.exampleEdge[`${e.from}>${e.to}`] ?? 0;
    const max1 = Math.max(1, ...clientEdges.map(count1));
    const max2 = Math.max(1, ...exampleEdges.map(count2));

    const e1: EdgeView[] = clientEdges.map((e) => {
      const y1 = yOf(clients.length, clients.findIndex((c) => c.id === e.from));
      const y2 = yOf(endpoints.length, endpoints.findIndex((x) => x.id === e.to));
      const n = count1(e);
      const live = Boolean(flash && flash.clientId === e.from && flash.endpointId === e.to);
      return {
        id: e.id, from: e.from, to: e.to, d: `M0 ${y1} L100 ${y2}`, top: y1 + (y2 - y1) * 0.76,
        tone: live ? 'live' : e.kind === 'MCP' ? 'mcp' : e.kind === 'EVENT' ? 'event' : 'rest',
        width: 1.2 + (2.6 * n) / max1, on: active1(e), count: n
      };
    });
    const e2: EdgeView[] = exampleEdges.map((e) => {
      const y1 = yOf(endpoints.length, endpoints.findIndex((x) => x.id === e.from));
      const y2 = yOf(examples.length, examples.findIndex((x) => x.id === e.to));
      const n = count2(e);
      const live = Boolean(flash && flash.endpointId === e.from && flash.exampleId === e.to);
      const isEvent = endpoint(e.from)?.kinds.includes('EVENT');
      return {
        id: e.id, from: e.from, to: e.to, d: `M0 ${y1} L100 ${y2}`, top: (y1 + y2) / 2,
        tone: live ? 'live' : !example(e.to)?.ok ? 'miss' : isEvent ? 'event' : 'rest',
        width: 1.2 + (2.6 * n) / max2, on: active2(e), count: n
      };
    });
    return { e1, e2 };
  });

  const all = $derived([...edges.e1, ...edges.e2]);
  const touches = (id: string) => all.some((e) => (e.from === id || e.to === id) && e.on);
  const sumFrom = (id: string) => edges.e1.reduce((a, e) => a + (e.from === id ? e.count : 0), 0);
  const sumTo1 = (id: string) => edges.e1.reduce((a, e) => a + (e.to === id ? e.count : 0), 0);
  const sumTo2 = (id: string) => edges.e2.reduce((a, e) => a + (e.to === id ? e.count : 0), 0);

  function nameOf(id: string): string {
    const c = clients.find((x) => x.id === id);
    if (c) return c.label;
    const e = endpoint(id);
    if (e) return `${e.method} ${e.label}`;
    return example(id)?.label ?? id;
  }
</script>

<section aria-labelledby="tr2-graph-title" class="tr2-graph">
  <div class="tr2-graph-head">
    <h2 id="tr2-graph-title">Call graph</h2>
    <span class="tr2-muted">Edge labels count calls over the last hour. Select a node to focus on its calls.</span>
  </div>
  <div class="tr2-graph-scroll">
    <div class="tr2-graph-grid">
      <p class="section-label" data-size="sm">Clients</p>
      <span aria-hidden="true"></span>
      <p class="section-label" data-size="sm">Mock endpoints</p>
      <span aria-hidden="true"></span>
      <p class="section-label" data-size="sm">Dispatched example</p>

      <div class="tr2-graph-col" data-col="clients">
        {#each clients as c (c.id)}
          <button type="button" class="tr2-node" data-kind={c.kind.toLowerCase()} aria-pressed={focus === c.id} data-dim={focus === c.id || touches(c.id) ? undefined : ''} onclick={() => onFocus(c.id)}>
            <span class="sr-only">Client</span>
            <span class="tr2-node-text">
              <span class="tr2-node-label demo-mono">{c.label}</span>
              <span class="tr2-node-sub demo-mono">{c.detail}</span>
            </span>
            <span class="tr2-node-total">{fmt(sumFrom(c.id))}</span>
            <span class="sr-only">calls in the last hour. Focus on this client.</span>
          </button>
        {/each}
      </div>
      {@render edgeLayer(edges.e1, '76%')}
      <div class="tr2-graph-col" data-col="endpoints">
        {#each endpoints as e (e.id)}
          {@const isEvent = e.kinds.includes('EVENT')}
          <button type="button" class="tr2-node" data-kind={isEvent ? 'event' : 'rest'} aria-pressed={focus === e.id} data-dim={focus === e.id || touches(e.id) ? undefined : ''} onclick={() => onFocus(e.id)}>
            <span class="sr-only">Endpoint</span>
            <span class="tr2-method" data-size="node" data-tone={isEvent || e.method === 'PATCH' ? 'event' : 'rest'}>{e.method}</span>
            <span class="tr2-node-text">
              <span class="tr2-node-label demo-mono">{e.label}</span>
              <span class="tr2-node-sub">{e.detail}</span>
            </span>
            <span class="tr2-node-total">{fmt(sumTo1(e.id))}</span>
            <span class="sr-only">calls in the last hour. Focus on this endpoint.</span>
          </button>
        {/each}
      </div>
      {@render edgeLayer(edges.e2, '50%')}
      <div class="tr2-graph-col" data-col="examples">
        {#each examples as x (x.id)}
          {@const event = exampleEdges.some((e) => e.to === x.id && endpoint(e.from)?.kinds.includes('EVENT'))}
          <button type="button" class="tr2-node" data-kind={!x.ok ? 'miss' : event ? 'event' : 'rest'} aria-pressed={focus === x.id} data-dim={focus === x.id || touches(x.id) ? undefined : ''} onclick={() => onFocus(x.id)}>
            <span class="sr-only">Example, status</span>
            <span class="tr2-code" data-miss={x.ok ? undefined : ''}>
              <Icon name={x.ok ? 'check' : 'warning'} size={11} />
              {x.code}
            </span>
            <span class="tr2-node-text">
              <span class="tr2-node-label">{x.label}</span>
              <span class="tr2-node-sub demo-mono">{x.detail}</span>
            </span>
            <span class="tr2-node-total">{fmt(sumTo2(x.id))}</span>
            <span class="sr-only">times served in the last hour. Focus on this example.</span>
          </button>
        {/each}
      </div>
    </div>
  </div>

  <table class="sr-only">
    <caption>Call graph as a table: calls over the last hour on each link</caption>
    <thead><tr><th scope="col">From</th><th scope="col">To</th><th scope="col">Calls</th></tr></thead>
    <tbody>
      {#each all as e (e.id)}
        <tr><th scope="row">{nameOf(e.from)}</th><td>{nameOf(e.to)}</td><td>{fmt(e.count)}</td></tr>
      {/each}
    </tbody>
  </table>

  <div class="tr2-legend">
    {#each [['rest', 'HTTP call'], ['mcp', 'MCP tool call'], ['event', 'Kafka message to a subscriber'], ['live', 'latest call']] as [tone, label] (tone)}
      <span><svg width="28" height="8" viewBox="0 0 28 8" aria-hidden="true"><path d="M0 4h28" data-tone={tone} /></svg>{label}</span>
    {/each}
    <span class="tr2-legend-end">
      Node borders carry the protocol: <span class="demo-mono" data-tone="rest">REST</span> · <span class="demo-mono" data-tone="event">EVENT</span> ·
      <span class="demo-mono" data-tone="mcp">MCP</span>
    </span>
  </div>
</section>

{#snippet edgeLayer(list: EdgeView[], labelAt: string)}
  <div class="tr2-graph-edges" aria-hidden="true">
    {#each list as e (e.id)}
      <svg viewBox={`0 0 100 ${HEIGHT}`} preserveAspectRatio="none" style:opacity={e.on ? 1 : 0.22}>
        <path d={e.d} data-tone={e.tone} style={e.tone === 'live' ? undefined : `stroke-width:${e.width.toFixed(2)}`} vector-effect="non-scaling-stroke" />
      </svg>
    {/each}
    {#each list as e (e.id)}
      <span class="tr2-edge-count" data-tone={e.tone} data-dim={e.on ? undefined : ''} style:left={labelAt} style:top={`${e.top}px`}>{fmt(e.count)}</span>
    {/each}
  </div>
{/snippet}
