<script lang="ts">
  import { goto, invalidate } from '$app/navigation';
  import { page } from '$app/state';
  import { onDestroy } from 'svelte';
  import { ApiBadge, Button, CopyButton, Icon, MethodTag } from '@microcks/deep-ink';
  import CatalogPageHeader from '#lib/components/catalog/CatalogPageHeader.svelte';
  import NotFound from '#lib/components/catalog/NotFound.svelte';
  import OpPickList from '#lib/components/catalog/OpPickList.svelte';
  import ProposalRow, { fresh, type Item } from '#lib/components/catalog/ProposalRow.svelte';
  import SplitPane from '#lib/components/catalog/SplitPane.svelte';
  import type { CatalogExample, Proposal } from '#lib/components/catalog/types.js';
  import { PLACEHOLDER_NOTE } from '#lib/placeholders/catalog.js';

  let { data } = $props();

  const COUNTS = [1, 2, 3, 4, 5];
  const GENERATION_MS = 1800;
  const exWord = (n: number) => `${n} ${n === 1 ? 'example' : 'examples'}`;

  const detail = $derived(data.detail);
  const svc = $derived(detail?.service);
  const ops = $derived(detail?.operations ?? []);
  const hintParam = page.url.searchParams.get('hint');
  let count = $state(3);
  let hint = $state(hintParam ? `Cases such as ${hintParam}` : '');
  let byOp = $state<Record<string, { items: Item[] }>>({});
  let generating = $state<string | undefined>();
  let exportOpen = $state(false);
  let status = $state('');
  let error = $state('');
  let ticket = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  onDestroy(() => clearTimeout(timer));

  const op = $derived(ops.find((o) => o.id === page.url.searchParams.get('op')) ?? ops[0]);
  const items = $derived(op ? (byOp[op.id]?.items ?? []) : []);
  const served = $derived(op?.examples ?? []);
  const contractOf = (id: string) => ops.find((o) => o.id === id)?.examples.filter((e) => !e.aiGenerated).length ?? 0;
  const all = $derived(Object.values(byOp).flatMap((s) => s.items));
  const sum = $derived({
    accepted: all.filter((i) => i.status === 'accepted').length,
    rejected: all.filter((i) => i.status === 'rejected').length,
    pending: all.filter((i) => i.status === 'pending').length
  });
  const opsWith = $derived(Object.keys(byOp).filter((k) => byOp[k].items.some((i) => i.status !== 'rejected')));
  const anyCollapsed = $derived(items.some((i) => !i.expanded && i.status !== 'rejected'));
  const isGenerating = $derived(generating === op?.id);
  const pendingNote = $derived(sum.accepted > 0 && sum.pending > 0 ? `${sum.pending} pending ${sum.pending === 1 ? 'proposal stays' : 'proposals stay'} here and are not added.` : undefined);
  const aiBadge = $derived(sum.accepted ? `+${sum.accepted} AI` : sum.pending ? `+${sum.pending} AI pending` : undefined);
  const exampleTotal = $derived(ops.reduce((n, o) => n + o.examples.length, 0));
  const artifact = $derived(detail?.artifacts[0]?.file);

  const yamlQuote = (v: string) => JSON.stringify(v);
  const exported = $derived.by(() => {
    if (!svc) return '';
    const lines = ['apiVersion: mocks.microcks.io/v1alpha1', 'kind: APIExamples', 'metadata:', `  name: ${yamlQuote(svc.name)}`, `  version: ${yamlQuote(svc.version)}`, 'operations:'];
    for (const o of ops) {
      const acc = (byOp[o.id]?.items ?? []).filter((i) => i.status === 'accepted');
      if (!acc.length) continue;
      lines.push(`  ${yamlQuote(`${o.method} ${o.path}`)}:`);
      for (const i of acc) {
        const e = i.example;
        lines.push(`    ${yamlQuote(e.name)}:`, '      request:');
        lines.push('        parameters:', ...Object.entries(e.request.params).map(([k, v]) => `          ${k}: ${yamlQuote(v)}`));
        if (e.request.body) lines.push('        body: |', ...e.request.body.split('\n').map((l) => `          ${l}`));
        lines.push('      response:', `        status: ${e.response.status}`, '        body: |', ...e.response.body.split('\n').map((l) => `          ${l}`));
      }
    }
    return lines.join('\n') + '\n';
  });
  const exportFile = $derived(`${svc?.name.toLowerCase().replace(/\s+/g, '-')}-${svc?.version}-examples.yaml`);

  function setItems(key: string, fn: (items: Item[]) => Item[]) {
    byOp[key] = { items: fn(byOp[key]?.items ?? []) };
  }
  const patchItem = (key: string, id: string, fn: (i: Item) => Item) => setItems(key, (list) => list.map((i) => (i.example.id === id ? fn(i) : i)));
  const pickOp = (id: string) => goto(`${page.url.pathname}?${new URLSearchParams({ op: id })}`, { replace: true, reset: false });

  async function generate() {
    if (!svc || !op) return;
    const key = op.id;
    const mine = ++ticket;
    const kept = (byOp[key]?.items ?? []).filter((i) => i.status === 'accepted');
    setItems(key, () => kept);
    generating = key;
    exportOpen = false;
    error = '';
    status = `Generating ${exWord(count)}…`;
    const started = Date.now();
    const res = await fetch(`/api/services/${svc.id}/operations/${key}/generate`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ count, hint: hint.trim() })
    }).catch(() => undefined);
    if (mine !== ticket) return;
    if (!res?.ok) {
      generating = undefined;
      error = 'The generation failed. Try again.';
      return;
    }
    const { proposals } = (await res.json()) as { proposals: Proposal[] };
    timer = setTimeout(() => {
      if (mine !== ticket) return;
      setItems(key, () => [...kept, ...proposals.map((p, i) => fresh(p as CatalogExample, i === 0))]);
      generating = undefined;
      status = `${proposals.length} proposals ready to review.`;
    }, Math.max(0, GENERATION_MS - (Date.now() - started)));
  }

  function cancel() {
    ticket++;
    clearTimeout(timer);
    generating = undefined;
    status = 'Generation cancelled.';
  }

  async function accept(item: Item, edited = false) {
    if (!svc || !op) return;
    let body = item.draftBody;
    try {
      body = JSON.stringify(JSON.parse(item.draftBody), null, 2);
    } catch {
      /* validated before */
    }
    const example: CatalogExample = edited ? { ...item.example, name: item.draftName.trim(), response: { ...item.example.response, body } } : item.example;
    const res = await fetch(`/api/services/${svc.id}/operations/${op.id}/examples`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ examples: [example] })
    });
    if (!res.ok) {
      error = `Could not accept ${example.name} (${res.status}).`;
      return;
    }
    error = '';
    patchItem(op.id, item.example.id, (i) => ({ ...i, example, status: 'accepted', edited: edited || i.edited, editing: false, draftName: example.name, draftBody: example.response.body }));
    await invalidate('catalog:service');
    status = `Accepted ${example.name}.`;
  }

  function reject(item: Item) {
    if (!op) return;
    patchItem(op.id, item.example.id, (i) => ({ ...i, status: 'rejected', editing: false, expanded: false }));
    status = `Rejected ${item.example.name}.`;
  }

  async function undo(item: Item) {
    if (!svc || !op) return;
    if (item.status === 'accepted') {
      await fetch(`/api/services/${svc.id}/operations/${op.id}/examples/${encodeURIComponent(item.example.id)}`, { method: 'DELETE' });
      await invalidate('catalog:service');
    }
    patchItem(op.id, item.example.id, (i) => ({ ...i, status: 'pending' }));
    status = `${item.example.name} is pending again.`;
  }

  async function acceptAll() {
    for (const i of items.filter((x) => x.status === 'pending' && !x.editing)) await accept(i);
  }

  function onCountKey(ev: KeyboardEvent, n: number) {
    const d = ev.key === 'ArrowRight' || ev.key === 'ArrowDown' ? 1 : ev.key === 'ArrowLeft' || ev.key === 'ArrowUp' ? -1 : 0;
    if (!d) return;
    ev.preventDefault();
    count = COUNTS[(COUNTS.indexOf(n) + d + COUNTS.length) % COUNTS.length];
    const group = (ev.currentTarget as HTMLElement).parentElement;
    requestAnimationFrame(() => group?.querySelector<HTMLElement>('[aria-checked="true"]')?.focus());
  }
</script>

{#if !svc || !detail || !op}
  <NotFound status={data.status === 200 ? 404 : data.status} what="The service" />
{:else}
      {#snippet rows()}
        <div class="copilot-rows">
          {#each items as item (item.example.id)}
            <ProposalRow {item} method={op.method} pathTemplate={op.path} mockBase={detail.mockBase} {served} onpatch={(fn) => patchItem(op.id, item.example.id, fn)} onaccept={(edited) => accept(item, edited)} onreject={() => reject(item)} onundo={() => undo(item)} />
          {/each}
        </div>
      {/snippet}
  <div class="copilot">
    <CatalogPageHeader
      titleText="AI Copilot"
      size="sm"
      breadcrumb={[{ label: 'APIs & Services', href: '/services' }, { label: `${svc.name} ${svc.version}`, href: `/services/${svc.id}` }, { label: 'AI Copilot' }]}
      description="Generate request and response examples from the contract's schemas when the specification has too few. You review every proposal before it joins the Service."
      descriptionTone="secondary"
      descriptionWidth={780}
    >
      {#snippet leading()}<Icon name="spark" size={26} class="copilot-title-icon" />{/snippet}
      {#snippet badge()}
        <span class="copilot-badges">
          <a href={`/services/${svc.id}`} class="copilot-badge-link"><ApiBadge protocol={svc.protocol} name={svc.name} version={svc.version} /></a>
          <ApiBadge label="samples" value={String(exampleTotal)} status="action" values={aiBadge ? [{ value: aiBadge, status: 'warning' }] : undefined} />
        </span>
      {/snippet}
    </CatalogPageHeader>
    <div role="note" class="copilot-rule">
      <Icon name="spark" size={15} />
      <span><strong>AI examples are never served by the mock, counted in conformance tests or exported until a person accepts them.</strong> Accepted examples keep an AI provenance tag on the Service.</span>
    </div>
    <p class="copilot-help" role="note">{PLACEHOLDER_NOTE} Generation is a placeholder: no model is called, proposals are variants of the first example.</p>

    <SplitPane sideWidth="300px" sideMax="380px" mainWidth="560px" keepSideMax sideAs="div" mainAs="section" class="copilot-split" mainClass="copilot-proposals">
      {#snippet side()}
        <div class="copilot-settings">
          <section aria-labelledby="sec-op" class="copilot-op">
            <h2 id="sec-op" class="copilot-section-label">1 · Operation</h2>
            <OpPickList
              mode="radio"
              labelledBy="sec-op"
              value={op.id}
              onchange={pickOp}
              options={ops.map((o) => {
                const its = byOp[o.id]?.items ?? [];
                const pe = its.filter((i) => i.status === 'pending').length;
                const ac = its.filter((i) => i.status === 'accepted').length;
                return { id: o.id, method: o.method, protocol: svc.protocol, path: o.path, summary: `${exWord(contractOf(o.id))} in the contract`, end: generating === o.id ? 'generating' : pe ? `+${pe} pending` : ac ? `+${ac} accepted` : undefined };
              })}
            />
          </section>

          <section aria-labelledby="sec-settings" class="copilot-box">
            <h2 id="sec-settings" class="copilot-section-label">2 · Generation settings</h2>
            <div class="copilot-field">
              <span id="count-label" class="copilot-label">Number of examples</span>
              <div role="radiogroup" aria-labelledby="count-label" class="copilot-count">
                {#each COUNTS as n (n)}
                  <button type="button" role="radio" aria-checked={count === n} tabindex={count === n ? 0 : -1} onclick={() => (count = n)} onkeydown={(ev) => onCountKey(ev, n)}>{n}</button>
                {/each}
              </div>
            </div>
            <div class="copilot-field">
              <label for="copilot-hint" class="copilot-label">Hint <span class="copilot-optional">· optional</span></label>
              <textarea id="copilot-hint" class="input" rows="3" bind:value={hint} aria-describedby="copilot-hint-help" placeholder="Describe the data you want, e.g. edge cases, a locale, a season"></textarea>
              <span id="copilot-hint-help" class="copilot-help">Added to the prompt next to the operation and its schemas.</span>
            </div>
            <div class="copilot-model">
              <span class="section-label">Model</span>
              <span class="copilot-model-name">OpenAI-compatible endpoint configured by admin</span>
              <span class="copilot-model-key">ai-copilot.implementation=openai</span>
              <a href="/admin">See Administration settings</a>
            </div>
            <div class="copilot-context">
              <span class="copilot-context-title">Prompt context</span>
              <span>{op.method} {op.path}{artifact ? ` from ${artifact}` : ''}, its schemas and the {exWord(contractOf(op.id))} already defined, so proposals do not repeat them.</span>
            </div>
            {#if isGenerating}
              <Button class="copilot-wide" onclick={cancel}>Cancel generation</Button>
            {:else}
              <Button variant="primary" icon="spark" class="copilot-wide" onclick={generate}>{items.length ? 'Generate again' : `Generate ${exWord(count)}`}</Button>
            {/if}
            {#if !isGenerating && items.length > 0}<span class="copilot-help">Generating again replaces pending and rejected proposals. Accepted ones are kept.</span>{/if}
            {#if error}<span class="proposal-edit-error" role="alert"><Icon name="cross" size={13} />{error}</span>{/if}
          </section>
        </div>
      {/snippet}

      <div class="copilot-proposals-head">
        <div class="copilot-proposals-title">
          <h2 id="sec-proposals">Proposals</h2>
          <span class="copilot-op-key"><MethodTag method={op.method} protocol={svc.protocol} /><span class="copilot-op-path">{op.path}</span></span>
        </div>
        {#if items.length > 0}
          <div class="copilot-bulk">
            <Button size="sm" variant="link" onclick={acceptAll}>Accept all pending</Button>
            <Button size="sm" variant="link" onclick={() => setItems(op.id, (l) => l.map((i) => ({ ...i, expanded: anyCollapsed })))}>{anyCollapsed ? 'Expand all' : 'Collapse all'}</Button>
          </div>
        {/if}
      </div>


      {#if isGenerating}
        <div role="status" class="copilot-generating">
          <p class="copilot-generating-line">
            <span class="copilot-live"><span aria-hidden="true" class="copilot-live-dot"></span>live</span>
            Generating {exWord(count)} for {op.method} {op.path}{hint.trim() ? ` · hint: "${hint.trim()}"` : ''}…
          </p>
          {#each Array.from({ length: count }, (_, i) => i) as i (i)}
            <div aria-hidden="true" class="copilot-skeleton" style:opacity={1 - i * 0.15}>
              <Icon name="spark" size={14} />
              <span class="copilot-skeleton-bars"><span style:width={`${46 + ((i * 17) % 30)}%`}></span><span style:width={`${28 + ((i * 23) % 26)}%`}></span></span>
            </div>
          {/each}
        </div>
        {#if items.length > 0}{@render rows()}{/if}
      {:else if items.length === 0}
        <div class="copilot-empty">
          <Icon name="spark" size={22} />
          <h3>No proposal for this operation yet</h3>
          <p>Choose how many examples you want, add a hint if useful, then generate. Proposals appear here as ghost rows for review.</p>
        </div>
      {:else}
        {@render rows()}
      {/if}

      <div class="copilot-summary">
        <p class="copilot-counts" role="status">
          <span data-tone="success"><Icon name="check" size={14} /><span><strong>{sum.accepted}</strong> accepted</span></span>
          <span aria-hidden="true" class="copilot-dot">·</span>
          <span data-tone="muted"><Icon name="cross" size={14} /><span><strong>{sum.rejected}</strong> rejected</span></span>
          <span aria-hidden="true" class="copilot-dot">·</span>
          <span data-tone="warning"><Icon name="spark" size={14} /><span><strong>{sum.pending}</strong> pending</span></span>
          {#if opsWith.length > 1}<span class="copilot-across">across {opsWith.length} operations</span>{/if}
        </p>
        <span class="copilot-summary-actions">
          <Button icon="download" class="copilot-export" disabled={sum.accepted === 0} aria-expanded={exportOpen && sum.accepted > 0} aria-controls="copilot-export" onclick={() => (exportOpen = !exportOpen)}>Export accepted examples (APIExamples)</Button>
          {#if sum.accepted > 0}
            <Button variant="primary" icon="plus" class="copilot-add" href={`/services/${svc.id}?op=${op.id}`}>Add {exWord(sum.accepted)} to {svc.name} {svc.version}</Button>
          {:else}
            <span aria-disabled="true" class="copilot-add-disabled">Accept a proposal to add it</span>
          {/if}
        </span>
      </div>
      {#if pendingNote}<span class="copilot-help">{pendingNote}</span>{/if}

      {#if exportOpen && sum.accepted > 0}
        <div id="copilot-export" class="copilot-export-preview">
          <div class="copilot-export-head">
            <span class="copilot-export-name">
              <span class="copilot-export-file">{exportFile}</span>
              <span class="copilot-help">Secondary artifact. Re-import it on another Microcks to share these examples.</span>
            </span>
            <CopyButton value={exported} label="Copy" variant="link" size="sm" />
          </div>
          <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
          <pre role="region" tabindex="0" aria-label={`${exportFile} content`}>{exported}</pre>
        </div>
      {/if}
    </SplitPane>
    <p class="sr-only" role="status" aria-live="polite">{status}</p>
  </div>
{/if}
