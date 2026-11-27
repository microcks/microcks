<script lang="ts">
  import { Callout, CodeBlock, Icon, Segmented } from '@microcks/deep-ink';
  import Panel from './Panel.svelte';
  import { JSON_RULE_SAMPLE, parseJsonBodyRule, reachability, ruleNames, scriptSample, storedAs } from './dispatch.js';
  import type { Behavior, CatalogExample, CatalogOperation, DispatcherKind } from './types.js';

  interface Props {
    draft: Behavior;
    op: CatalogOperation;
    examples: CatalogExample[];
    pathParams: string[];
    pathPrefix: string;
    edit: (patch: Partial<Behavior>) => void;
  }

  let { draft, op, examples, pathParams, pathPrefix, edit }: Props = $props();

  const CARDS: { id: DispatcherKind; desc: string; tag?: string; disabled?: boolean }[] = [
    { id: 'NONE', desc: 'Always serves the first example.' },
    { id: 'URI_PARTS', desc: 'Matches path segments.' },
    { id: 'URI_PARAMS', desc: 'Matches query parameter values.' },
    { id: 'QUERY_ARGS', desc: 'Matches GraphQL or gRPC arguments.', tag: 'GraphQL & gRPC only', disabled: true },
    { id: 'JSON_BODY', desc: 'Evaluates an expression on the JSON request body.' },
    { id: 'SCRIPT', desc: 'Groovy or JavaScript returns the example name.' },
    { id: 'FALLBACK', desc: 'A dispatcher, plus a default example when nothing matches.' },
    { id: 'PROXY_FALLBACK', desc: 'A dispatcher, else forwards to the real backend.' }
  ];
  const HINTS: Record<DispatcherKind, string> = {
    NONE: 'No criteria: the first example answers every call.',
    URI_PARTS: 'Criteria built from path parameters. Example names map to /name=<value>.',
    URI_PARAMS: 'Criteria built from query parameters, joined with &&.',
    QUERY_ARGS: 'Not applicable to a REST operation.',
    JSON_BODY: 'An expression is evaluated on the body; cases map values to example names.',
    SCRIPT: 'Code evaluated on each request; it returns the example name to serve.',
    FALLBACK: 'Apply a dispatcher first, then serve a default example when nothing matches.',
    PROXY_FALLBACK: 'Apply a dispatcher first, then forward unmatched calls to the real backend.'
  };
  const SCOPE = ['mockRequest', 'requestContext', 'store', 'log'];

  const names = $derived(ruleNames(draft.rule));
  const jsonCheck = $derived(draft.dispatcher === 'JSON_BODY' ? parseJsonBodyRule(draft.rule) : undefined);
  const jsonError = $derived(jsonCheck && 'error' in jsonCheck ? jsonCheck.error : undefined);
  const reach = $derived(reachability(op, draft));
  const reachable = $derived(reach.filter((r) => r.reachable).length);
  const reachLabel = $derived(
    `${reachable} of ${reach.length} examples reachable` +
      (draft.dispatcher === 'FALLBACK' && draft.fallback ? ` · unknown names → ${draft.fallback}` : '') +
      (draft.dispatcher === 'PROXY_FALLBACK' ? ' · unknown names → proxyUrl' : '')
  );
  const scriptLines = $derived((draft.script ?? '').split('\n').length);

  function pick(id: DispatcherKind) {
    const patch: Partial<Behavior> = { dispatcher: id };
    const pathRule = names.length > 0 && names.every((n) => pathParams.includes(n));
    if (id === 'JSON_BODY' && jsonError !== undefined || (id === 'JSON_BODY' && draft.dispatcher !== 'JSON_BODY')) patch.rule = JSON_RULE_SAMPLE;
    if (id === 'URI_PARAMS' && draft.dispatcher !== 'URI_PARAMS') patch.rule = '';
    if ((id === 'URI_PARTS' || id === 'FALLBACK' || id === 'PROXY_FALLBACK') && !pathRule) patch.rule = pathParams.join(' && ');
    if (id === 'NONE') patch.rule = '';
    if (id === 'SCRIPT' && !draft.script) {
      patch.scriptLanguage = draft.scriptLanguage ?? 'GROOVY';
      patch.script = scriptSample(patch.scriptLanguage, draft.stateful);
    }
    if (id !== 'SCRIPT' && draft.stateful) patch.stateful = false;
    if (id === 'FALLBACK' && !draft.fallback) patch.fallback = examples[0]?.name;
    if (id === 'PROXY_FALLBACK' && !draft.proxyUrl) patch.proxyUrl = 'https://backend.example.com/api';
    edit(patch);
  }
</script>

{#snippet firstTry()}
  <div class="rules-step">
    <span class="section-label">1 · First try</span>
    <span class="rules-readonly">{draft.baseDispatcher} <span class="rules-muted">· {draft.rule || '(none)'}</span></span>
  </div>
{/snippet}

<Panel padding="lg" title="Dispatcher" class="dispatcher">
  {#snippet actions()}<a href="https://microcks.io/documentation/explanations/dispatching/" target="_blank" rel="noreferrer">Dispatching reference</a>{/snippet}
  <p class="panel-lead">Turns an incoming request into dispatch criteria, then into the example to return.</p>
  <fieldset class="ex-fieldset">
    <legend class="sr-only">Dispatcher type</legend>
    <div class="ex-radio-cards">
      {#each CARDS as c (c.id)}
        <label class="ex-radio-card" aria-disabled={c.disabled || undefined}>
          <input type="radio" name="dispatcher" value={c.id} checked={draft.dispatcher === c.id} disabled={c.disabled} onchange={() => pick(c.id)} />
          <span class="ex-radio-card-title ex-row">{c.id}{#if draft.dispatcher === c.id}<Icon name="check" size={14} />{/if}</span>
          <span class="ex-small ex-secondary">{c.desc}</span>
          {#if c.tag}<span class="ex-radio-card-tag">{c.tag}</span>{/if}
        </label>
      {/each}
    </div>
  </fieldset>

  <div class="rules">
    <div class="rules-head">
      <h3 class="rules-title">Rules</h3>
      <span class="rules-hint">{HINTS[draft.dispatcher]}</span>
    </div>

    {#if draft.dispatcher === 'URI_PARTS'}
      <div class="rules-block">
        <span id="parts-label" class="section-label">Path parameters used as criteria</span>
        {#if pathParams.length === 0}
          <span class="rules-help">This operation has no path parameter. Use URI_PARAMS or JSON_BODY instead.</span>
        {:else}
          <div class="rules-path" role="group" aria-labelledby="parts-label">
            <span class="rules-path-prefix">{pathPrefix}</span>
            {#each pathParams as p (p)}
              {@const on = names.includes(p)}
              <button type="button" class="rules-param" aria-pressed={on} aria-label={`Use path parameter ${p} as dispatch criteria`} onclick={() => edit({ rule: (on ? names.filter((n) => n !== p) : [...names, p]).join(' && ') })}>
                {#if on}<Icon name="check" size={12} />{/if}{`{${p}}`}
              </button>
            {/each}
          </div>
          <span class="rules-help">Click a path parameter to include or exclude it. Several parameters are joined with <span class="ex-mono">&amp;&amp;</span>.</span>
        {/if}
      </div>
    {/if}

    {#if draft.dispatcher === 'URI_PARAMS'}
      <div class="rules-block">
        <label for="params-rule" class="section-label">Query parameters used as criteria</label>
        <input id="params-rule" class="rules-input" type="text" spellcheck="false" value={draft.rule} oninput={(e) => edit({ rule: e.currentTarget.value })} />
        {#if reach.some((r) => !r.reachable) || reach.length === 0}
          <Callout tone="warning" variant="outline">Examples that share the same query criteria are unreachable: only the first one answers.</Callout>
        {/if}
      </div>
    {/if}

    {#if draft.dispatcher === 'JSON_BODY'}
      <div class="rules-block">
        <label for="json-rule" class="section-label">Expression, operator and cases</label>
        <textarea id="json-rule" class="rules-textarea" rows="9" spellcheck="false" value={draft.rule} aria-invalid={jsonError ? true : undefined} aria-describedby={jsonError ? 'json-error' : undefined} oninput={(e) => edit({ rule: e.currentTarget.value })}></textarea>
        {#if jsonError}<span id="json-error" class="rules-error"><Icon name="cross" size={13} />{jsonError}</span>{/if}
        <span class="rules-help">Operators: <span class="ex-mono">equals · range · size · regexp · presence</span>. A <span class="ex-mono">default</span> case is required.</span>
      </div>
    {/if}

    {#if draft.dispatcher === 'SCRIPT'}
      <div class="rules-block">
        <div class="rules-script-bar">
          <Segmented variant="joined" size="sm" label="Script language" value={draft.scriptLanguage ?? 'GROOVY'} onchange={(id) => edit({ scriptLanguage: id as 'GROOVY' | 'JS', script: scriptSample(id as 'GROOVY' | 'JS', draft.stateful) })} options={[{ id: 'GROOVY', label: 'Groovy' }, { id: 'JS', label: 'JavaScript' }]} />
          <span class="rules-scope">In scope {#each SCOPE as s (s)}<code>{s}</code>{/each}</span>
        </div>
        <div class="rules-editor">
          <pre class="rules-gutter" aria-hidden="true">{Array.from({ length: scriptLines }, (_, i) => i + 1).join('\n')}</pre>
          <label for="script-editor" class="sr-only">Dispatcher script</label>
          <textarea id="script-editor" class="rules-code" rows={scriptLines + 1} spellcheck="false" wrap="off" value={draft.script ?? ''} oninput={(e) => edit({ script: e.currentTarget.value })}></textarea>
        </div>
        <span class="rules-help">The script returns the name of the example to serve. It runs on the Microcks server; the simulator does not evaluate it.</span>
      </div>
    {/if}

    {#if draft.dispatcher === 'FALLBACK'}
      <div class="rules-block">
        <div class="rules-chain">
          {@render firstTry()}
          <span aria-hidden="true" class="rules-arrow">→</span>
          <div class="rules-step">
            <label for="fallback-ex" class="section-label">2 · If nothing matches, serve</label>
            <select id="fallback-ex" class="rules-select" value={draft.fallback ?? ''} onchange={(e) => edit({ fallback: e.currentTarget.value })}>
              {#each examples as e (e.id)}<option value={e.name}>{e.name}</option>{/each}
            </select>
          </div>
        </div>
        <span class="rules-help">Without a fallback, an unknown value returns 404.</span>
      </div>
    {/if}

    {#if draft.dispatcher === 'PROXY_FALLBACK'}
      <div class="rules-block">
        <div class="rules-chain">
          {@render firstTry()}
          <span aria-hidden="true" class="rules-arrow">→</span>
          <div class="rules-step rules-step-wide">
            <label for="proxy-url" class="section-label">2 · Else forward to (proxyUrl)</label>
            <input id="proxy-url" class="rules-input rules-url" type="url" spellcheck="false" value={draft.proxyUrl ?? ''} oninput={(e) => edit({ proxyUrl: e.currentTarget.value })} />
          </div>
        </div>
        <span class="rules-help">Microcks keeps the path and query, swaps the base URL, and returns the real backend's response.</span>
      </div>
    {/if}

    {#if reach.length}
      <div class="reach">
        <div class="section-label reach-head">
          <h4 class="reach-title">Examples and their dispatch criteria</h4>
          <span class="reach-count">{reachLabel}</span>
        </div>
        <ul class="reach-list">
          {#each reach as r (r.name)}
            <li class="reach-row" data-ok={r.reachable || undefined}>
              <span class="reach-name">{r.name}</span>
              <span class="reach-criteria">{r.criteria}</span>
              <span class="reach-state"><Icon name={r.reachable ? 'check' : 'warning'} size={13} />{r.reachable ? 'reachable' : 'unreachable'}</span>
            </li>
          {/each}
        </ul>
      </div>
    {/if}

    <div class="rules-stored">
      <span class="section-label" aria-hidden="true">Stored as</span>
      <CodeBlock variant="plain" caption="Stored as" code={storedAs(draft)} />
    </div>
  </div>
</Panel>
