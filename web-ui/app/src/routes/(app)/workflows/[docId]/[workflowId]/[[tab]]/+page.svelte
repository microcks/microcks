<script lang="ts">
  import { goto } from '$app/navigation';
  import { page } from '$app/state';
  import { Button, Callout, EmptyState, Tabs } from '@microcks/deep-ink';
  import CatalogPageHeader from '#lib/components/catalog/CatalogPageHeader.svelte';
  import DemoFlag from '#lib/components/workflows/DemoFlag.svelte';
  import ExamplesTab from '#lib/components/workflows/ExamplesTab.svelte';
  import LastRunNote from '#lib/components/workflows/LastRunNote.svelte';
  import LoomTab from '#lib/components/workflows/LoomTab.svelte';
  import MockUrlTab from '#lib/components/workflows/MockUrlTab.svelte';
  import RunsTab from '#lib/components/workflows/RunsTab.svelte';
  import SessionsTab from '#lib/components/workflows/SessionsTab.svelte';
  import SourceTab from '#lib/components/workflows/SourceTab.svelte';
  import StepsTab from '#lib/components/workflows/StepsTab.svelte';
  import WorkflowHeader from '#lib/components/workflows/WorkflowHeader.svelte';
  import { isAvailable, unavailableReason, type ArazzoFeature } from '#lib/workflows/availability.js';
  import { stoppedStep } from '#lib/workflows/describe.js';
  import { isWorkflowTab, workflowPath, type WorkflowTab } from '#lib/workflows/paths.js';

  let { data } = $props();

  const detail = $derived(data.detail);
  const doc = $derived(detail?.document);
  const workflow = $derived(detail?.workflow);
  const runs = $derived(detail?.runs ?? []);
  const rawTab = $derived(page.params.tab);
  const TAB_FEATURE: Partial<Record<WorkflowTab, ArazzoFeature>> = { 'mock-url': 'mock-url', sessions: 'sessions' };
  const tabOff = (id: WorkflowTab) => {
    const feature = TAB_FEATURE[id];
    return feature !== undefined && !isAvailable(feature, doc ?? data.detail?.demo);
  };
  const tabItem = (id: WorkflowTab, label: string, count?: number) => {
    const feature = TAB_FEATURE[id];
    return { id, label, count, disabled: tabOff(id) || undefined, title: feature ? unavailableReason(feature, doc ?? data.detail?.demo) : undefined };
  };
  const tab = $derived<WorkflowTab>(isWorkflowTab(rawTab) && !tabOff(rawTab) ? rawTab : 'loom');
  const runParam = $derived(Number(page.url.searchParams.get('run')));
  const run = $derived(Number.isFinite(runParam) && runParam > 0 ? runs.find((r) => r.number === runParam) ?? runs[0] : runs[0]);
  const step = $derived(page.url.searchParams.get('step') ?? undefined);
  const filter = $derived(page.url.searchParams.get('filter') ?? undefined);
  const lastRun = $derived(runs[0]);
  const serviceLabel = (id: string) => {
    const s = data.services.find((x) => x.id === id);
    return s ? `${s.name} ${s.version}` : undefined;
  };

  $effect(() => {
    if (workflow && rawTab !== undefined && (!isWorkflowTab(rawTab) || tabOff(rawTab))) {
      goto(workflowPath(workflow.documentId, workflow.workflowId, 'loom'), { replace: true });
    }
  });

  function go(target: WorkflowTab, query: Record<string, string | undefined> = {}) {
    if (!workflow) return;
    goto(workflowPath(workflow.documentId, workflow.workflowId, target, query), { replace: true, reset: false });
  }

  const carry = (extra: Record<string, string | undefined> = {}) => ({ run: run ? String(run.number) : undefined, ...extra });
  const goTab = (id: string) => go(id as WorkflowTab, id === 'loom' || id === 'steps' ? carry() : {});
  const setStep = (stepId: string | undefined) => go(tab, carry({ step: stepId }));
  const pickRun = (n: number) => go(tab, { run: String(n), step });
  const inspectLink = (stepId: string) => (workflow ? workflowPath(workflow.documentId, workflow.workflowId, 'loom', carry({ step: stepId })) : '#');
  const sourceStep = $derived(step ?? (run?.status === 'FAILURE' ? stoppedStep(run)?.stepId : undefined));
</script>

{#if !detail || !doc || !workflow}
  <CatalogPageHeader titleText="Workflow" />
  <div style="margin-top: 24px">
    <EmptyState title={data.status === 404 ? 'This workflow does not exist' : `The workflow could not be loaded (HTTP ${data.status})`} headingLevel={2} icon="search-x">
      {#snippet actions()}<Button variant="primary" href="/workflows">Go to Workflows</Button>{/snippet}
    </EmptyState>
  </div>
{:else}
  <div class="wf-page">
    <WorkflowHeader document={doc} {workflow} {runs} {serviceLabel} onmockurl={() => go('mock-url')} />
    {#if data.detail?.demo}<DemoFlag />{/if}

    {#if !workflow.runnability.runnable}
      <Callout tone="warning" title="This workflow cannot run yet"><span id="wf-run-why">{workflow.runnability.message}.</span></Callout>
    {/if}
    {#if lastRun}<LastRunNote document={doc} {workflow} run={lastRun} />{/if}

    <Tabs
      label="Workflow sections"
      value={tab}
      onchange={goTab}
      items={[
        tabItem('loom', 'Loom'),
        tabItem('steps', 'Steps', workflow.steps.length),
        tabItem('source', 'Source'),
        tabItem('mock-url', 'Mock URL'),
        tabItem('sessions', 'Sessions', detail.sessions.length || undefined),
        tabItem('examples', 'Examples', detail.examples.length || undefined),
        tabItem('runs', 'Runs', lastRun?.number)
      ]}
    >
      {#snippet children()}
        {#if tab === 'loom'}
          <LoomTab {workflow} document={doc} siblings={data.siblings} {runs} {run} {step} sessions={detail.sessions} {serviceLabel} onpickrun={pickRun} onselectstep={setStep} />
        {:else if tab === 'steps'}
          <StepsTab {workflow} document={doc} {runs} {run} onpickrun={pickRun} {inspectLink} />
        {:else if tab === 'source'}
          <SourceTab {workflow} document={doc} step={sourceStep} {serviceLabel} onselectstep={(s) => go('source', carry({ step: s }))} />
        {:else if tab === 'mock-url'}
          <MockUrlTab {workflow} document={doc} {runs} examples={detail.examples} onshowruns={() => go('runs', { filter: 'mock-url' })} />
        {:else if tab === 'sessions'}
          <SessionsTab {workflow} document={doc} sessions={detail.sessions} {runs} />
        {:else if tab === 'examples'}
          <ExamplesTab {workflow} document={doc} {runs} examples={detail.examples} {serviceLabel} />
        {:else}
          <RunsTab {workflow} document={doc} {runs} sessions={detail.sessions} initialFilter={filter} />
        {/if}
      {/snippet}
    </Tabs>
  </div>
{/if}
