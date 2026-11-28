<script lang="ts">
  import { ApiBadge, Button, Icon } from '@microcks/deep-ink';
  import type { Service } from '#lib/types.js';
  import { formatRelative } from '#lib/format.js';
  import { isAvailable, unavailableReason } from '#lib/workflows/availability.js';
  import { runHeadline, runOutcome, stoppedStep } from '#lib/workflows/describe.js';
  import type { WorkflowListItem } from '#lib/workflows/list.js';
  import { runPath, runSetupPath, workflowPath } from '#lib/workflows/paths.js';
  import { sourceColor } from '#lib/workflows/source-color.js';
  import type { ArazzoDocument, RunKindFilter, SourceDescription, WorkflowRun } from '#lib/workflows/types.js';
  import LoomThumb from './LoomThumb.svelte';

  interface Props {
    document: ArazzoDocument;
    rows: WorkflowListItem[];
    /** Every workflow of the document, whatever the filters (step counts of the sources, warning title). */
    docWorkflows: WorkflowListItem[];
    total: number;
    services: Service[];
    runKind: RunKindFilter;
    onlink: (source: SourceDescription) => void;
  }
  let { document: doc, rows, docWorkflows, total, services, runKind, onlink }: Props = $props();
  const mockUrlOn = $derived(isAvailable('mock-url', doc));

  const TARGET_LABEL: Record<Exclude<RunKindFilter, 'any'>, string> = {
    mocks: 'Microcks mocks',
    implementations: 'Implementations',
    'mock-url': 'Mock URL',
    session: 'Session'
  };

  const targetLabel = (run: WorkflowRun) =>
    run.origin.kind === 'session'
      ? TARGET_LABEL.session
      : run.origin.kind === 'mock-url'
        ? TARGET_LABEL['mock-url']
        : run.target.kind === 'mocks'
          ? TARGET_LABEL.mocks
          : run.target.kind === 'implementations'
            ? TARGET_LABEL.implementations
            : 'Mixed targets';

  /** One line on the last run: where it stopped, or how it passed. */
  function runNote(run: WorkflowRun): string {
    if (runOutcome(run) === 'failed') {
      const stopped = stoppedStep(run);
      if (run.origin.kind === 'session' && stopped) {
        const why = stopped.flow.title;
        return `Session ${run.origin.sessionId} · ${stopped.stepId}: ${why.charAt(0).toLowerCase()}${why.slice(1)}`;
      }
      return runHeadline(run).detail;
    }
    if (run.origin.kind === 'mock-url') return 'Called through its Mock URL';
    if (run.options.example) return `Example ${run.options.example} · Matches the example`;
    return '';
  }

  const serviceName = (id: string) => services.find((s) => s.id === id);
  const unlinked = $derived(doc.sources.filter((s) => s.link.status === 'not-linked'));
  const linkedCount = $derived(doc.sources.length - unlinked.length);
  const synced = $derived(doc.origin.startsWith('Synced'));
  const stepsOn = (name: string) => docWorkflows.reduce((n, w) => n + w.workflow.steps.filter((s) => s.sourceName === name).length, 0);
  const blockedBy = $derived(
    docWorkflows
      .filter((w) => w.workflow.steps.some((s) => unlinked.some((u) => u.name === s.sourceName)))
      .map((w) => w.workflow.workflowId)
  );
</script>

<section aria-label="Arazzo document {doc.file}" class="wf-doc">
  <div class="wf-doc-header">
    <div class="wf-doc-title-row">
      <div class="wf-doc-title">
        <Icon name="file-text" size={18} class="wf-text2" />
        <h2>{doc.file}</h2>
        <span class="wf-doc-name">{doc.title}</span>
        <span class="wf-tag">arazzo {doc.arazzoVersion}</span>
        <span class="wf-tag">version {doc.version}</span>
      </div>
      <span class="wf-doc-origin">
        <Icon name={synced ? 'sync' : 'upload'} size={14} />
        {#if synced}
          <span>{doc.origin} via</span>
          <a href="/sources" class="wf-plain-link">{doc.title}</a>
        {:else}
          <span>{doc.origin}</span>
        {/if}
      </span>
    </div>

    <p class="wf-overlay">
      {#if doc.overlay}
        <Icon name="layers" size={15} />
        <span>ArazzoSources overlay</span>
        <code>{doc.overlay}</code>
        <span class="wf-tag" data-tone="success">imported</span>
        <span class="wf-muted-text">links the sources, carries the recorded examples</span>
      {:else}
        <Icon name="layers-missing" size={15} class="wf-muted-text" />
        <span class="wf-muted-text">No ArazzoSources overlay · sources linked in the import dialog</span>
      {/if}
      <span class="wf-linked-count" data-tone={unlinked.length ? 'warning' : 'success'}>
        <Icon name={unlinked.length ? 'warning' : 'check-circle'} size={15} />
        {linkedCount} of {doc.sources.length} linked
      </span>
    </p>

    <div class="wf-src-box">
      <div role="table" aria-label="Source descriptions of {doc.file}" class="wf-src-table">
        <div role="row" class="wf-src-row wf-grid-head">
          <span role="columnheader">Source description</span>
          <span role="columnheader">Declared URL</span>
          <span role="columnheader">Microcks API</span>
          <span role="columnheader">Step operations</span>
        </div>
        {#each doc.sources as s (s.name)}
          {@const svc = s.link.status === 'linked' ? serviceName(s.link.serviceId) : undefined}
          <div role="row" class="wf-src-row">
            <span role="cell" class="wf-src-name"><code>{s.name}</code><span class="wf-src-type">{s.type}</span></span>
            <code role="cell" class="wf-src-url">{s.url}</code>
            <span role="cell" class="wf-src-api">
              {#if s.link.status === 'linked'}
                <a
                  href="/services/{s.link.serviceId}"
                  class="wf-badge-link"
                  aria-label="{s.link.protocol} {svc?.name ?? s.link.serviceId} {svc?.version ?? ''}: {s.name} is linked to it, open the API"
                >
                  <ApiBadge protocol={s.link.protocol} name={svc?.name ?? s.link.serviceId} version={svc?.version} size="sm" class="wf-src-badge" />
                </a>
                <span class="wf-linked"><Icon name="check" size={12} />Linked</span>
              {:else}
                <span class="wf-not-linked"><Icon name="warning" size={12} />Not linked</span>
              {/if}
            </span>
            <span role="cell" class="wf-src-ops" data-tone={s.link.status === 'linked' ? undefined : 'warning'}>
              <Icon name={s.link.status === 'linked' ? 'check-circle' : 'warning'} size={12} />
              {s.link.status === 'linked' ? s.link.operationsFound : `${stepsOn(s.name)} step${stepsOn(s.name) === 1 ? '' : 's'} unresolved`}
            </span>
          </div>
        {/each}
      </div>
    </div>

    {#if unlinked.length > 0}
      <div role="status" class="wf-warn">
        <Icon name="warning" size={18} class="wf-warn-icon" />
        <div class="wf-warn-text">
          <span class="wf-warn-title">
            {blockedBy.join(', ') || 'Its workflows'} cannot run until {unlinked.map((s) => s.name).join(', ')}
            {unlinked.length === 1 ? 'is' : 'are'} linked
          </span>
          <span class="wf-warn-hint">
            {unlinked[0]?.link.status === 'not-linked' ? unlinked[0].link.expectedFile : unlinked[0]?.url} is not in Microcks yet: import it first, or pick the API to link in the dialog.
          </span>
        </div>
        <Button size="sm" icon="link" class="wf-warn-btn" onclick={() => unlinked[0] && onlink(unlinked[0])}>Link sources</Button>
      </div>
    {/if}
  </div>

  <div class="wf-rows-box">
    <div role="table" aria-label="Workflows of {doc.file} ({rows.length} of {total})" class="wf-rows">
      <div role="row" class="wf-wrow wf-grid-head">
        <span role="columnheader">Workflow</span>
        <span role="columnheader">Steps</span>
        <span role="columnheader">Sources</span>
        <span role="columnheader">Inputs</span>
        <span role="columnheader">Last run</span>
        <span role="columnheader">Mock URL</span>
        <span role="columnheader"><span class="sr-only">Run</span></span>
      </div>
      {#each rows as row (row.workflow.workflowId)}
        {@const workflow = row.workflow}
        {@const lastRun = row.lastRun}
        {@const runnable = workflow.runnability.runnable}
        {@const reason = workflow.runnability.runnable ? undefined : workflow.runnability.reason}
        {@const message = workflow.runnability.runnable ? '' : workflow.runnability.message}
        {@const reasonId = `wf-reason-${workflow.workflowId}`}
        {@const names = [...new Set(workflow.steps.map((s) => s.sourceName))]}
        {@const unlinkedNames = names.filter((n) => !sourceColor(doc, n).linked)}
        {@const outcome = lastRun ? runOutcome(lastRun) : 'never'}
        {@const shown = outcome === 'running' ? 'never' : outcome}
        {@const note = lastRun ? runNote(lastRun) : ''}
        <div role="row" class="wf-wrow">
          <div role="cell" class="wf-cell wf-cell-workflow">
            <a href={workflowPath(doc.id, workflow.workflowId)} class="wf-badge-link wf-name-link">
              <ApiBadge arazzo name={workflow.summary} version={doc.version} class="wf-name-badge" title={workflow.summary} />
            </a>
            <div class="wf-cell-line">
              <code class="wf-id">{workflow.workflowId}</code>
              {#if reason === 'asyncapi-steps'}<span class="wf-tag" data-border="strong">AsyncAPI steps · Arazzo 1.1</span>{/if}
              {#if !runnable}
                <span class="wf-block"><Icon name="ban" size={11} />{reason === 'asyncapi-steps' ? 'Cannot run yet: AsyncAPI steps' : 'Not linked'}</span>
              {/if}
            </div>
            {#if !runnable}<span class="wf-note">{message}</span>{/if}
          </div>

          <div role="cell" class="wf-cell wf-cell-steps">
            <LoomThumb {workflow} document={doc} run={lastRun} />
            <span class="wf-steps-text">{workflow.steps.length} step{workflow.steps.length === 1 ? "" : "s"}{workflow.steps.some((s) => s.dependsOn?.length) ? ' · dependsOn' : ''}</span>
          </div>

          <div role="cell" class="wf-cell wf-cell-sources">
            {#each names as n (n)}
              {@const sc = sourceColor(doc, n)}
              {@const src = doc.sources.find((s) => s.name === n)}
              {@const svc = src?.link.status === 'linked' ? serviceName(src.link.serviceId) : undefined}
              <span class="wf-src-tag" data-linked={sc.linked ? '' : undefined}>
                {#if sc.linked}<span aria-hidden="true" class="wf-swatch" style="background:{sc.color}"></span>{:else}<Icon name="warning" size={11} class="wf-warning-icon" />{/if}
                {n}
                <span class="sr-only">{sc.linked ? (svc ? ` (linked to ${svc.name} ${svc.version})` : '') : ' (not linked)'}</span>
              </span>
            {/each}
          </div>

          <div role="cell" class="wf-cell wf-cell-inputs" aria-label={workflow.inputs.length ? `${workflow.inputs.length} inputs: ${workflow.inputs.map((i) => i.name).join(', ')}` : 'No input'}>
            <span aria-hidden="true" class="wf-inputs-count">{workflow.inputs.length}</span>
            <span aria-hidden="true" class="wf-inputs-names">{workflow.inputs.length ? workflow.inputs.map((i) => i.name).join(' ') : 'none'}</span>
          </div>

          <div role="cell" class="wf-cell wf-cell-last">
            <span class="wf-cell-line">
              <span class="wf-result-word" data-result={shown}>
                <Icon name={shown === 'passed' ? 'check-circle' : shown === 'failed' ? 'x-circle' : 'never-tested'} size={15} />
                {shown === 'passed' ? 'Passed' : shown === 'failed' ? 'Failed' : 'Never run'}
              </span>
              {#if lastRun}<span class="wf-tag">{targetLabel(lastRun)}</span>{/if}
            </span>
            {#if lastRun}
              <a
                href={runPath(lastRun.id)}
                class="wf-mono-link"
                aria-label="#{lastRun.number} · {formatRelative(lastRun.startedAt)}, open run of {workflow.workflowId}, {targetLabel(lastRun)}, {shown === 'passed' ? 'Passed' : 'Failed'}"
              >#{lastRun.number} · {formatRelative(lastRun.startedAt)}</a>
              {#if note}<span class="wf-run-note">{note}</span>{/if}
            {:else}
              <span class="wf-note">{!runnable ? 'Run disabled' : runKind === 'any' ? 'no run yet' : `no run with ${TARGET_LABEL[runKind]}`}</span>
            {/if}
          </div>

          <div role="cell" class="wf-cell wf-cell-mock">
            <span class="wf-mock-state" data-ready={runnable && mockUrlOn ? '' : undefined} title={mockUrlOn ? undefined : unavailableReason('mock-url', doc)}>
              <Icon name={runnable && mockUrlOn ? 'link' : 'never-tested'} size={15} />
              {runnable && mockUrlOn ? 'Ready' : 'Not available'}
            </span>
            {#if runnable && mockUrlOn}
              <a href={workflowPath(doc.id, workflow.workflowId, 'mock-url')} title="POST {workflow.mockUrl}" class="wf-mono-link wf-mock-path">POST …/{doc.version}/{workflow.workflowId}</a>
            {/if}
            <span class="wf-note">
              {#if !runnable}
                {reason === 'asyncapi-steps' ? 'AsyncAPI steps cannot be played yet' : `Link ${unlinkedNames.join(', ') || 'its sources'} first`}
              {:else if !mockUrlOn}
                Not available yet
              {:else if row.lastMockUrlRun}
                Last call {formatRelative(row.lastMockUrlRun.startedAt)} (run #{row.lastMockUrlRun.number})
              {:else}
                Never called
              {/if}
            </span>
          </div>

          <div role="cell" class="wf-cell-run">
            {#if runnable}
              <Button href={runSetupPath(doc.id, workflow.workflowId)} size="sm" icon="play">Run<span class="sr-only"> {workflow.workflowId}</span></Button>
            {:else}
              <Button size="sm" disabled icon="play" title={message} aria-describedby={reasonId} class="wf-run-disabled">Run<span class="sr-only"> {workflow.workflowId}</span></Button>
              <span id={reasonId} class="sr-only">{message}</span>
            {/if}
          </div>
        </div>
      {/each}
    </div>
  </div>
</section>
