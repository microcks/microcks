<script lang="ts">
  import { ApiBadge, Button, Choice, Icon } from '@microcks/deep-ink';
  import { outlineOf, yamlSpans, type OutlineBlock } from '#lib/workflows/source-outline.js';
  import { api } from '#lib/api.js';
  import { analysisIssues, type ImportAnalysis } from '#lib/workflows/import-analysis.js';
  import { isAvailable, isDemo, unavailableReason } from '#lib/workflows/availability.js';
  import { syncedText } from '#lib/workflows/header.js';
  import type { ArazzoDocument, ImportIssue, Workflow } from '#lib/workflows/types.js';

  interface Props {
    workflow: Workflow;
    document: ArazzoDocument;
    step?: string;
    serviceLabel: (serviceId: string) => string | undefined;
    onselectstep: (stepId: string) => void;
  }

  let { workflow, document, step, serviceLabel, onselectstep }: Props = $props();

  let text = $state<string | undefined>();
  let loadError = $state(false);
  let picked = $state<string | undefined>();
  let fold = $state(true);
  let validating = $state(false);
  let validated = $state<{ issues: { path: string; message: string }[]; at: string; analysed: boolean } | null>(null);
  let validateError = $state<string | null>(null);
  let copied = $state(false);

  $effect(() => {
    const id = document.id;
    text = undefined;
    loadError = false;
    fetch(`/api/workflows/documents/${encodeURIComponent(id)}/source`)
      .then((r) => (r.ok ? r.text() : Promise.reject(new Error(String(r.status)))))
      .then((t) => (text = t))
      .catch(() => (loadError = true));
  });

  const lines = $derived((text ?? '').replace(/\n$/, '').split('\n'));
  const blocks = $derived(outlineOf(lines, workflow.workflowId, workflow.steps.map((s) => s.stepId)));
  const selectedId = $derived(picked ?? step ?? 'doc');
  const selected = $derived(blocks.find((b) => b.id === selectedId) ?? blocks[0]);
  const used = $derived([...new Set(workflow.steps.map((s) => s.sourceName))]);
  const ops = $derived([...new Set(workflow.steps.map((s) => `${s.sourceName}.${s.operationId}`))]);
  const linkedTo = $derived(
    used.map((name) => {
      const src = document.sources.find((s) => s.name === name);
      return { name, label: src?.link.status === 'linked' ? (serviceLabel(src.link.serviceId) ?? src.link.serviceId) : undefined };
    })
  );
  const issues = $derived(validated?.issues ?? document.importIssues);
  const analysed = $derived(validated?.analysed === true);
  const when = $derived(validated ? validated.at : syncedText(document) ? `at sync, ${syncedText(document)?.replace(/^Synced /, '')}` : 'at import');
  const wfBlock = $derived(blocks.find((b) => b.id === 'workflow'));
  const last = $derived(blocks[blocks.length - 1]);

  type Row = { kind: 'line'; i: number; inSel: boolean } | { kind: 'fold'; i: number; block: OutlineBlock };
  const rows = $derived.by(() => {
    const out: Row[] = [];
    for (let i = 0; i < lines.length; ) {
      const block = blocks.find((b) => b.step && b.from === i);
      if (block && fold && block.id !== selected?.id) {
        out.push({ kind: 'fold', i, block });
        i = block.to + 1;
        continue;
      }
      out.push({ kind: 'line', i, inSel: selected !== undefined && i >= selected.from && i <= selected.to });
      i += 1;
    }
    return out;
  });

  function pick(b: OutlineBlock) {
    picked = b.id;
    if (b.step) onselectstep(b.id);
  }

  /** Demo documents replay their import issues; for a real one Microcks analyses the document with its current links. */
  async function validate() {
    validating = true;
    validateError = null;
    try {
      if (isDemo(document)) {
        await new Promise((r) => setTimeout(r, 350));
        validated = { issues: document.importIssues, at: 'just now', analysed: false };
      } else {
        const analysis = await api<ImportAnalysis>(`/api/workflows/documents/${encodeURIComponent(document.id)}/analysis`, { method: 'POST', json: {} });
        validated = { issues: analysisIssues(analysis), at: 'just now', analysed: true };
      }
    } catch (e) {
      validateError = e instanceof Error ? e.message : String(e);
    } finally {
      validating = false;
    }
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(text ?? '');
      copied = true;
    } catch {
      /* clipboard blocked */
    }
  }
</script>

{#snippet yaml(t: string)}{#each yamlSpans(t) as s, k (k)}{#if s.cls}<span class={s.cls}>{s.text}</span>{:else}{s.text}{/if}{/each}{/snippet}

{#if loadError}
  <p role="alert" class="wf-src-invalid">The Arazzo source could not be loaded.</p>
{:else if text === undefined}
  <p role="status" class="wf-src-muted">Loading the Arazzo source…</p>
{:else}
  <div class="wf-source-tab">
    <nav aria-labelledby="wf-outline-title" class="wf-src-outline">
      <h2 id="wf-outline-title">Outline</h2>
      <span class="wf-outline-file">{document.file}</span>
      {#each blocks as b (b.id)}
        <button type="button" class="wf-outline-item" data-nested={b.nested ? '' : undefined} aria-pressed={b.id === selected?.id} onclick={() => pick(b)}>
          <span class="wf-outline-label">{b.label}</span>
          <span class="wf-outline-range">{b.from + 1}–{b.to + 1}</span>
        </button>
      {/each}
      <div class="wf-outline-fold"><Choice bind:checked={fold}>{#snippet label()}Fold the other steps{/snippet}</Choice></div>
    </nav>

    <section aria-label="Arazzo source" class="wf-src">
      <div class="wf-src-toolbar">
        <div class="wf-src-caption">
          <ApiBadge arazzo name={document.arazzoVersion} size="sm" />
          <span class="wf-src-where">
            workflow {workflow.workflowId}{wfBlock && last ? ` · lines ${wfBlock.from + 1}–${last.to + 1}` : ''}{selected ? ` · selected ${selected.label}` : ''}
          </span>
        </div>
        <div class="wf-src-actions">
          <Button size="sm" icon="check" loading={validating} title={isDemo(document) ? undefined : 'Asks Microcks to analyse the document with its current links'} onclick={validate}>{validating ? 'Validating…' : 'Validate'}</Button>
          <Button size="sm" icon="copy" onclick={copy}>{copied ? 'Copied' : 'Copy YAML'}</Button>
          {#if isAvailable('document-edit', document)}
            <Button size="sm" href="/sources">Edit in source</Button>
          {:else}
            <Button size="sm" disabled title={unavailableReason('document-edit', document)}>Edit in source</Button>
          {/if}
        </div>
      </div>

      <div role="status" aria-live="polite" class="wf-src-band">
        {#if validating}
          <span>{isDemo(document) ? `Validating against Arazzo ${document.arazzoVersion}…` : 'Microcks is analysing the document…'}</span>
        {:else if validateError}
          <span class="wf-src-invalid"><Icon name="x-circle" size={15} />{validateError}</span>
        {:else if issues.length === 0}
          <span class="wf-src-band-line">
            <span class="wf-src-valid"><Icon name="check-circle" size={15} />Valid Arazzo {document.arazzoVersion}</span>
            <span>· inside the subset Microcks runs · {workflow.steps.length} steps · {ops.length} operations found in {linkedTo.map((l) => l.label ?? l.name).join(', ')}</span>
            <span class="wf-src-muted">· validated {when}</span>
          </span>
          <span class="wf-src-refs">
            {ops.map((o, k) => (k === 0 ? `$sourceDescriptions.${o}` : ` · .${o.split('.').pop()}`)).join('')}
            → {linkedTo.map((l) => `${l.name} ${l.label ? `linked to ${l.label}` : 'not linked'}`).join(', ')}{document.metadata ? ` by ${document.metadata}` : ''}
          </span>
        {:else}
          <span class="wf-src-invalid"><Icon name="x-circle" size={15} />{#if analysed}{issues.length} problem{issues.length === 1 ? '' : 's'} found by Microcks{:else}{issues.length} feature{issues.length === 1 ? '' : 's'} outside the subset Microcks runs{/if}</span>
          <ul class="wf-src-issues">
            {#each issues as i (i.path)}<li><code>{i.path}</code>: {i.message}</li>{/each}
          </ul>
        {/if}
      </div>

      <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
      <div class="wf-src-code" role="region" aria-label="{document.file}, lines" tabindex="0">
        <div class="wf-src-lines">
          {#each rows as r (r.i)}
            {#if r.kind === 'fold'}
              <button type="button" class="wf-yaml-row wf-src-fold" onclick={() => pick(r.block)}>
                <span class="sr-only">Unfold step {r.block.id}, lines {r.block.from + 1} to {r.block.to + 1}: </span>
                <span aria-hidden="true" class="wf-src-n">{r.i + 1}</span>
                <span class="wf-src-text">{@render yaml(lines[r.i] ?? '')}</span>
                <span class="wf-src-chip"><Icon name="chevron" size={10} />{r.block.to - r.block.from} lines folded</span>
              </button>
            {:else}
              <div class="wf-yaml-row" data-selected={r.inSel ? '' : undefined}>
                <span aria-hidden="true" class="wf-src-n">{r.i + 1}</span>
                <span class="wf-src-text">{@render yaml(lines[r.i] ?? '')}</span>
              </div>
            {/if}
          {/each}
        </div>
      </div>
    </section>
  </div>
{/if}
