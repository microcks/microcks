<script lang="ts">
  import { ApiBadge, Button } from '@microcks/deep-ink';
  import CatalogPageHeader from '#lib/components/catalog/CatalogPageHeader.svelte';
  import Menu, { type MenuItem } from '#lib/components/catalog/Menu.svelte';
  import { isAvailable, unavailableReason } from '#lib/workflows/availability.js';
  import { download, runHow, syncedText, WEEK_MS } from '#lib/workflows/header.js';
  import { runPath, runSetupPath } from '#lib/workflows/paths.js';
  import { formatName, type ArazzoDocument, type Workflow, type WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    document: ArazzoDocument;
    workflow: Workflow;
    runs: WorkflowRun[];
    serviceLabel: (serviceId: string) => string | undefined;
    onmockurl: () => void;
  }

  let { document, workflow, runs, serviceLabel, onmockurl }: Props = $props();

  const canRun = $derived(workflow.runnability.runnable);
  const mockOffReason = $derived(unavailableReason('mock-url', document) ?? (workflow.runnability.runnable ? undefined : workflow.runnability.message));
  const lastRun = $derived(runs[0]);
  const used = $derived([...new Set(workflow.steps.map((s) => s.sourceName))]);
  const synced = $derived(syncedText(document));
  /** The importer of the document (its page in Sources); the list of sources for the demo data. */
  const sourceJobHref = $derived(document.job ? `/sources/${document.job.id}` : '/sources');
  const week = $derived(runs.filter((r) => r.status !== 'RUNNING' && Date.now() - new Date(r.startedAt).getTime() < WEEK_MS));
  const passed = $derived(week.filter((r) => r.status === 'SUCCESS').length);
  const lastWord = $derived(lastRun ? (lastRun.status === 'SUCCESS' ? 'passed' : lastRun.status === 'FAILURE' ? 'failed' : 'running') : undefined);

  async function fetchText(path: string) {
    const r = await fetch(path);
    return r.ok ? r.text() : undefined;
  }

  const items = $derived<MenuItem[]>([
    { id: 'copy', label: 'Copy workflowId', onselect: () => void navigator.clipboard?.writeText(workflow.workflowId).catch(() => undefined) },
    {
      id: 'download',
      label: 'Download Arazzo document',
      onselect: () => void fetchText(`/api/workflows/documents/${document.id}/source`).then((y) => y && download(document.file, y))
    },
    ...(document.metadata
      ? [
          {
            id: 'metadata',
            label: 'Download ArazzoMetadata',
            onselect: () => void fetchText(`/api/workflows/documents/${document.id}/metadata`).then((y) => y && download(document.metadata ?? 'metadata.arazzo-metadata.yaml', y))
          }
        ]
      : []),
    ...(synced ? [{ id: 'job', label: `Open source job ${document.job?.name ?? document.title}`, href: sourceJobHref }] : []),
    { id: 'reimport', label: 'Re-import the Arazzo document', href: '/sources/new' },
    { id: 'delete', label: 'Delete workflow', disabled: true, description: 'Workflows are removed with their Arazzo document.' }
  ]);
</script>

<CatalogPageHeader
  titleText={workflow.summary}
  variant="badge"
  align="start"
  breadcrumbSeparator="›"
  breadcrumb={[{ label: 'Workflows', href: '/workflows' }, { label: `${document.title} ${document.version}`, href: '/workflows' }, { label: workflow.summary }]}
>
  {#snippet title()}<ApiBadge arazzo name={workflow.summary} version={document.version} size="xl" class="wf-title-badge" />{/snippet}
  {#snippet actions()}
    {#if canRun}
      <Button variant="primary" icon="play" href={runSetupPath(document.id, workflow.workflowId)}>Run workflow</Button>
    {:else}
      <Button variant="primary" icon="play" disabled aria-describedby="wf-run-why">Run workflow</Button>
    {/if}
    {#if isAvailable('document-edit', document)}
      <Button icon="pencil" href="/sources">Edit in source</Button>
    {:else}
      <Button icon="pencil" disabled title={unavailableReason('document-edit', document)}>Edit in source</Button>
    {/if}
    <Menu label="More workflow actions" iconOnly icon="more-vertical" align="end" {items} />
  {/snippet}
  {#snippet children()}
    <div class="wf-facts">
      <span>workflowId <span class="wf-facts-strong">{workflow.workflowId}</span></span>
      <span class="wf-facts-mono">{document.file} · {formatName(document.format).toLowerCase()} {document.formatVersion}</span>
      {#if document.metadata}<span>metadata <span class="wf-facts-mono">{document.metadata}</span></span>{/if}
      {#if synced}
        <span>{synced} by source job <a href={sourceJobHref}>{document.job?.name ?? document.title}</a></span>
      {:else}
        <span>{document.origin}</span>
      {/if}
    </div>
    <p class="wf-description">{workflow.description ?? workflow.summary}</p>
    <div class="wf-proof">
      {#each used as name (name)}
        {@const src = document.sources.find((s) => s.name === name)}
        {@const link = src?.link.status === 'linked' ? src.link : undefined}
        {@const label = link ? serviceLabel(link.serviceId) : undefined}
        <span class="wf-proof-source">
          <span class="wf-proof-name">{name}</span>
          {#if link && label}
            <a href="/services/{link.serviceId}" class="wf-badge-link" aria-label="Source description {name}, linked to {link.protocol} {label}">
              <ApiBadge protocol={link.protocol} name={label} />
            </a>
          {:else}
            <ApiBadge label="not linked" value={name} status="warning" />
          {/if}
        </span>
      {/each}
      <span aria-hidden="true" class="wf-proof-divider"></span>
      {#if lastRun && lastWord}
        <a href={runPath(lastRun.id)} class="wf-badge-link">
          <ApiBadge
            label="last run"
            value="{lastWord} #{lastRun.number}"
            status={lastRun.status === 'SUCCESS' ? 'success' : lastRun.status === 'FAILURE' ? 'danger' : 'info'}
            values={[{ value: runHow(lastRun) }]}
          />
        </a>
      {/if}
      {#if week.length}<ApiBadge label="runs · 7 d" value="{passed}/{week.length} passed" />{/if}
      {#if isAvailable('mock-url', document) && canRun}
        <button type="button" class="wf-badge-button" onclick={onmockurl} aria-label="Mock URL ready: open the Mock URL tab">
          <ApiBadge label="mock url" value="ready" status="success" />
        </button>
      {:else}
        <span class="wf-badge-off" title={mockOffReason}>
          <ApiBadge label="mock url" value={isAvailable('mock-url', document) ? 'not available' : 'not available yet'} status="neutral" />
        </span>
      {/if}
    </div>
  {/snippet}
</CatalogPageHeader>

<style>
  .wf-badge-off { opacity: 0.6; cursor: not-allowed; }
  /* Arazzo summaries can be a sentence: the title badge wraps instead of running under the actions. */
  :global(.demo-page-header-text:has(.wf-title-badge)) { min-width: 0; }
  :global(.wf-title-badge) { max-width: 100%; }
  :global(.wf-title-badge > .api-badge-name) { white-space: normal; min-width: 0; line-height: 1.1; }
</style>
