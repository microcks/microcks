<script lang="ts">
  import { ApiBadge, Button, Callout, Choice, Icon } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';
  import type { Service } from '#lib/types.js';
  import { readiness, sourceVerdict, type ImportAnalysis } from '#lib/workflows/import-analysis.js';
  import type { ArazzoDocument, SourceDescription } from '#lib/workflows/types.js';

  interface Props {
    document: ArazzoDocument;
    source: SourceDescription;
    services: Service[];
    onclose: () => void;
    /** Called once the source is linked, with the API it now points to. */
    onlinked: (service: Service) => void;
  }
  let { document, source, services, onclose, onlinked }: Props = $props();

  let dialog = $state<HTMLDialogElement>();
  let choice = $state('');
  let busy = $state(false);
  let error = $state<string | null>(null);

  const wanted = $derived(source.type === 'asyncapi' ? 'EVENT' : 'REST');
  const candidates = $derived(
    services.filter((s) => s.protocol === wanted || (source.type === 'openapi' && s.protocol === 'DIRECT'))
  );
  const file = $derived(source.link.status === 'not-linked' ? source.link.expectedFile : source.url);
  const hint = $derived(
    candidates.find((s) => s.name.toLowerCase().replace(/\s+/g, '').includes(source.name.toLowerCase().replace(/catalog$/, '')))
  );
  const selected = $derived(choice || hint?.id || '');
  // The API proposed by the name of the source starts selected (a radio shows its group value).
  $effect(() => {
    if (!choice && hint) choice = hint.id;
  });

  $effect(() => {
    dialog?.showModal();
  });

  /** What Microcks makes of the document if the selected API were linked (nothing is stored). */
  let analysis = $state<ImportAnalysis>();
  let analysing = $state(false);
  let analysisError = $state<string | null>(null);
  const verdict = $derived(analysis ? analysis.sources.find((x) => x.sourceName === source.name) : undefined);
  const ready = $derived(analysis ? readiness(analysis) : undefined);

  $effect(() => {
    const serviceId = selected;
    analysis = undefined;
    analysisError = null;
    if (!serviceId || !candidates.some((s) => s.id === serviceId)) return;
    let current = true;
    analysing = true;
    api<ImportAnalysis>(`/api/workflows/documents/${encodeURIComponent(document.id)}/analysis`, { method: 'POST', json: { source: source.name, serviceId } })
      .then((a) => current && (analysis = a))
      .catch((e) => current && (analysisError = e instanceof Error ? e.message : String(e)))
      .finally(() => current && (analysing = false));
    return () => {
      current = false;
    };
  });

  async function link() {
    const service = candidates.find((s) => s.id === selected);
    if (!service) {
      error = 'Pick an API to link.';
      return;
    }
    busy = true;
    error = null;
    try {
      await api(`/api/workflows/documents/${document.id}/sources/${encodeURIComponent(source.name)}/link`, {
        method: 'POST',
        json: { serviceId: service.id }
      });
      onlinked(service);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      busy = false;
    }
  }
</script>

<dialog class="dialog" bind:this={dialog} aria-labelledby="wf-link-title" aria-describedby="wf-link-desc" onclose={onclose}>
  <div class="dialog-header">
    <h2 class="dialog-title" id="wf-link-title">Link {source.name}</h2>
    <Button variant="ghost" size="sm" iconOnly icon="close" aria-label="Close" onclick={() => dialog?.close()} />
  </div>
  <div class="dialog-body">
    <p class="dialog-description" id="wf-link-desc">
      {document.file} declares {source.name} as {source.url}. The API for {file} may not be in Microcks yet: import it first, or pick the API to link here.
    </p>
    <fieldset class="wf-link-choices">
      <legend>Microcks API</legend>
      {#each candidates as s (s.id)}
        <Choice kind="radio" variant="card" name="wf-link-api" value={s.id} bind:group={choice}>
          {#snippet label()}<ApiBadge protocol={s.protocol} name={s.name} version={s.version} size="sm" />{/snippet}
          {#snippet description()}{s.operations.length} {s.operations.length === 1 ? 'operation' : 'operations'} · {s.format}{/snippet}
        </Choice>
      {/each}
    </fieldset>
    {#if selected}
      <div class="wf-link-effect" role="status" aria-live="polite" aria-busy={analysing}>
        {#if analysing}
          Checking {source.name} against the API…
        {:else if analysisError}
          <span class="wf-link-error"><Icon name="alert-circle" size={14} /> {analysisError}</span>
        {:else if verdict && ready}
          <span class="wf-link-effect-line" data-tone={verdict.link.linked ? sourceVerdict(verdict).tone : 'warning'}>
            <Icon name={sourceVerdict(verdict).tone === 'success' ? 'check-circle' : 'warning'} size={14} />
            {source.name}: {sourceVerdict(verdict).detail}
          </span>
          <span>
            {ready.runnable} of {ready.total} {ready.total === 1 ? 'workflow' : 'workflows'} can run{ready.blocked.length ? `: ${ready.blocked.map((b) => `${b.workflowId} ${b.note[0]?.toLowerCase()}${b.note.slice(1)}`).join('; ')}` : ''}.
          </span>
        {/if}
      </div>
    {/if}
    {#if error}<p class="wf-link-error" role="alert"><Icon name="alert-circle" size={14} /> {error}</p>{/if}
    {#if candidates.length === 0}
      <Callout tone="warning" title="No matching API in the catalogue">Import {file} first, then come back.</Callout>
    {/if}
  </div>
  <div class="dialog-footer">
    <Button variant="ghost" onclick={() => dialog?.close()}>Cancel</Button>
    <Button variant="primary" loading={busy} onclick={link}>Link this API</Button>
  </div>
</dialog>

<style>
  .wf-link-choices { display: grid; gap: var(--space-2); margin: 0; padding: 0; border: 0; min-width: 0; }
  .wf-link-choices legend { padding: 0; margin-bottom: var(--space-2); font: 600 13px/18px var(--font-ui); }
  .wf-link-effect { display: grid; gap: 4px; margin-top: var(--space-2); font: var(--type-small); color: var(--text-secondary); }
  .wf-link-effect-line { display: inline-flex; align-items: center; gap: 6px; color: var(--success); }
  .wf-link-effect-line[data-tone='warning'] { color: var(--warning); }
  .wf-link-error { display: flex; align-items: center; gap: 6px; margin: var(--space-2) 0 0; color: var(--danger); font: var(--type-small); }
</style>
