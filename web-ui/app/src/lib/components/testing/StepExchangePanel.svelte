<script lang="ts">
  import { renderPayload } from '#lib/components/catalog/payload.js';
  import { testCaseId, type StepExchange, type TestRunDto, type TestStepResult } from './model.js';

  interface Props {
    run: TestRunDto;
    step: TestStepResult;
  }
  let { run, step }: Props = $props();

  const CLAMP = 10;
  let exchange = $state<StepExchange>();
  let loading = $state(true);
  let failure = $state('');
  let expanded = $state<Record<string, boolean>>({});

  // Steps of the same operation share one test case: the nth step is the nth exchange unless names match.
  function pick(pairs: StepExchange[]): StepExchange | undefined {
    const byName = pairs.find((p) => p.request?.name === step.name);
    if (byName) return byName;
    const rank = run.details.steps.filter((s) => s.operationId === step.operationId).findIndex((s) => s.id === step.id);
    return pairs[rank];
  }

  $effect(() => {
    const url = `/api/tests/${encodeURIComponent(run.id)}/messages/${encodeURIComponent(testCaseId(run, step.operationId))}`;
    let cancelled = false;
    loading = true;
    failure = '';
    fetch(url)
      .then(async (res) => {
        if (!res.ok) throw new Error(`The exchanged messages could not be loaded (${res.status}).`);
        const pair = pick((await res.json()) as StepExchange[]);
        if (!cancelled) exchange = pair;
      })
      .catch((e) => {
        if (!cancelled) failure = e instanceof Error ? e.message : String(e);
      })
      .finally(() => {
        if (!cancelled) loading = false;
      });
    return () => {
      cancelled = true;
    };
  });

  const headersOf = (h?: { name: string; values?: string[] }[]) => (h ?? []).map((x) => ({ name: x.name, value: (x.values ?? []).join(', ') }));
  const sections = $derived([
    { key: 'req', title: 'Request', head: `${step.method} ${run.testedEndpoint}${step.path}`, headers: headersOf(exchange?.request?.headers), body: exchange?.request?.content },
    {
      key: 'res',
      title: 'Response',
      head: [exchange?.response?.status, exchange?.response?.mediaType, `${step.ms} ms`].filter(Boolean).join(' · '),
      headers: headersOf(exchange?.response?.headers),
      body: exchange?.response?.content
    }
  ]);
</script>

<div class="sx">
  {#if loading}
    <p class="sx-note">Loading the exchanged messages…</p>
  {:else if failure}
    <p class="sx-note" role="alert">{failure}</p>
  {:else if !exchange}
    <p class="sx-note">No request or response was recorded for this step.</p>
  {:else}
    <div class="sx-pair">
      {#each sections as sec (sec.key)}
        {@const p = sec.body ? renderPayload(sec.body) : undefined}
        {@const long = !!p && p.lines > CLAMP}
        <section class="sx-card" aria-label={sec.title}>
          <header class="sx-head"><span class="sx-title">{sec.title}</span><span class="sx-meta">{sec.head}</span></header>
          {#if sec.headers.length}
            <dl class="sx-headers">
              {#each sec.headers as h (h.name)}<dt>{h.name}</dt><dd>{h.value}</dd>{/each}
            </dl>
          {/if}
          {#if p}
            <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
            <pre class="sx-body" tabindex="0" aria-label="{sec.title} body"><code class="hljs" data-clamped={long && !expanded[sec.key] ? '' : undefined}>{@html p.html}</code></pre>
            {#if long}
              <button type="button" class="sx-toggle" aria-expanded={!!expanded[sec.key]} onclick={() => (expanded[sec.key] = !expanded[sec.key])}>
                {expanded[sec.key] ? 'Show less' : `Show all ${p.lines} lines`}
              </button>
            {/if}
          {:else}
            <p class="sx-empty">No body.</p>
          {/if}
        </section>
      {/each}
    </div>
  {/if}
</div>

<style>
  .sx-note, .sx-empty { margin: 0; color: var(--text-muted); font: var(--type-small); }
  .sx-empty { padding: 8px 12px; }
  .sx-pair { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 12px; }
  .sx-card { min-width: 0; border: 1px solid var(--border); background: var(--surface); }
  .sx-head { display: flex; flex-wrap: wrap; gap: 4px 12px; align-items: baseline; padding: 6px 12px; border-bottom: 1px solid var(--border); background: var(--surface-muted, var(--surface)); }
  .sx-title { font: 600 12px/18px var(--font-ui); }
  .sx-meta { min-width: 0; overflow-wrap: anywhere; color: var(--text-muted); font: 12px/18px var(--font-mono); }
  .sx-headers { display: grid; grid-template-columns: max-content 1fr; gap: 2px 12px; margin: 0; padding: 8px 12px; border-bottom: 1px solid var(--border); font: 12px/18px var(--font-mono); }
  .sx-headers dt { color: var(--text-muted); }
  .sx-headers dd { margin: 0; min-width: 0; overflow-wrap: anywhere; }
  .sx-body { margin: 0; padding: 8px 12px; font: 12px/1.5 var(--font-mono); white-space: pre-wrap; overflow-wrap: anywhere; overflow-x: hidden; }
  .sx-body code { display: block; padding: 0; background: transparent; color: inherit; font: inherit; }
  .sx-body code[data-clamped] { max-height: calc(10 * 1.5em); overflow: hidden; }
  .sx-toggle { margin: 0 12px 8px; padding: 0; border: 0; background: none; color: var(--link); font: 600 12px/1.5 var(--font-ui); cursor: pointer; }
  .sx-toggle:hover { text-decoration: underline; }
  .sx-body :global(.hljs-attr), .sx-body :global(.hljs-name), .sx-body :global(.hljs-attribute) { color: var(--link); }
  .sx-body :global(.hljs-string) { color: var(--success); }
  .sx-body :global(.hljs-number), .sx-body :global(.hljs-literal) { color: var(--warning); }
  .sx-body :global(.hljs-tag), .sx-body :global(.hljs-meta), .sx-body :global(.hljs-punctuation) { color: var(--text-muted); }
</style>
