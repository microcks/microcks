<script lang="ts">
  import { untrack } from 'svelte';
  import { ApiBadge, Button, Icon, LiveIndicator, StatusBadge } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import DemoFlag from '#lib/components/workflows/DemoFlag.svelte';
  import RunApiPanel from './RunApiPanel.svelte';
  import RunBadges from './RunBadges.svelte';
  import RunExampleJudging from './RunExampleJudging.svelte';
  import RunMeta from './RunMeta.svelte';
  import RunProgress, { type RunSegment } from './RunProgress.svelte';
  import RunProperties from './RunProperties.svelte';
  import RunSteps from './RunSteps.svelte';
  import RunSummary from './RunSummary.svelte';
  import { formatRelative } from '#lib/format.js';
  import { formatClock } from '#lib/runs.js';
  import { RUN_SCENARIOS, apiBodyOf, launchedBy, phaseOf, runFrames, scenarioOf, targetLine, type RunScenario, type WorkflowRunPayload } from '#lib/runs-workflow.js';
  import { callTotals, runContext, runHeadline } from '#lib/workflows/describe.js';
  import { resolveExpression } from '#lib/workflows/expression.js';
  import { runPath, runSetupPath, workflowPath } from '#lib/workflows/paths.js';
  import { formatDuration } from '#lib/workflows/time.js';
  import type { Step, WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    payload: WorkflowRunPayload;
    live: boolean;
  }

  let { payload, live }: Props = $props();

  const stored = untrack(() => payload.run);
  const frames = untrack(() => (live ? runFrames(stored) : [stored]));
  const last = frames.length - 1;
  const REPLAY_TICK_MS = 1100;

  let index = $state(0);
  let paused = $state(false);
  const run = $derived<WorkflowRun>(live ? frames[Math.min(index, last)]! : stored);

  $effect(() => {
    if (!live || paused || index >= last) return;
    const timer = setTimeout(() => (index = Math.min(index + 1, last)), REPLAY_TICK_MS);
    return () => clearTimeout(timer);
  });

  const running = $derived(run.status === 'RUNNING');
  const passed = $derived(run.status === 'SUCCESS');
  const headline = $derived(runHeadline(run));
  const totals = $derived(callTotals(run));
  const total = $derived(run.steps.length);
  const workflow = $derived(payload.workflow);
  const document = $derived(payload.document);
  const example = $derived(payload.examples.find((e) => e.id === run.options.example));
  const documentLabel = $derived(document ? `${document.title} ${document.version}` : undefined);
  const currentIndex = $derived(running ? Math.max(0, run.steps.findIndex((s) => s.stepId === run.current?.stepId)) : total);
  const workflowTo = $derived(workflowPath(run.documentId, run.workflowId));
  const isScenarioWorkflow = $derived(run.workflowId === 'restock-pastry' && run.documentId === 'pastry');

  let message = $state('');
  let lastStep: string | undefined;
  $effect(() => {
    const stepId = run.current?.stepId;
    if (running && stepId) {
      if (lastStep !== stepId) {
        lastStep = stepId;
        message = `Step ${currentIndex + 1} of ${total}, ${stepId}, running.`;
      }
    } else if (!running && lastStep !== '__done') {
      lastStep = '__done';
      message = `${headline.title}. ${headline.detail}`.trim();
    }
  });

  // The Pause / Skip buttons go away when the run ends: move focus to the verdict instead of losing it.
  let wasRunning = untrack(() => running);
  $effect(() => {
    if (wasRunning && !running) {
      const active = document_active();
      if (!active || active === globalThis.document.body) globalThis.document.getElementById('wfr-verdict-h')?.focus();
    }
    wasRunning = running;
  });
  const document_active = () => globalThis.document.activeElement;

  const SCENARIO_DOT: Record<RunScenario, 'danger' | 'success' | 'warning'> = { impl14: 'danger', mocksEnd: 'success', mocksMissing: 'warning' };

  function resolvedUrl(def: Step): string {
    const ctx = runContext(run);
    let path = def.path;
    const query: string[] = [];
    for (const p of def.parameters) {
      const v = p.value.startsWith('$') ? resolveExpression(p.value, ctx) : p.value;
      const text = v === undefined ? `{${p.name}}` : String(v);
      if (p.in === 'path') path = path.replace(`{${p.name}}`, encodeURIComponent(text));
      else if (p.in === 'query') query.push(`${p.name}=${encodeURIComponent(text)}`);
    }
    const base =
      run.target.kind === 'implementations'
        ? (run.target.baseUrls[def.sourceName] ?? '')
        : (run.steps.find((x) => x.request)?.request?.url.replace(/\/pastries.*$/, '') ?? '');
    return `${base}${path}${query.length ? `?${query.join('&')}` : ''}`;
  }

  const progress = $derived.by(() => {
    const current = run.current ? run.steps.find((s) => s.stepId === run.current?.stepId) : undefined;
    const retrying = !!run.current && run.current.attempt > 1;
    const nowTitle = !current ? 'Now calling' : retrying ? `Retrying · attempt ${run.current?.attempt} of ${current.attempts.length}` : 'Now calling';
    const def = workflow?.steps.find((s) => s.stepId === current?.stepId);
    const target = current?.request ? `${current.request.method} ${current.request.url}` : def ? `${def.method} ${resolvedUrl(def)}` : '';
    const nowLabel = !current ? 'All steps ended · computing the result…' : retrying ? `retryAfter → ${target}` : target;
    const segments = run.steps.map((s, i): RunSegment => {
      const ph = phaseOf(run, i);
      if (ph === 'running') return 'current';
      if (ph !== 'done') return 'queued';
      return s.status === 'passed' ? 'done' : s.status === 'failed' ? 'failed' : 'queued';
    });
    return { nowTitle, nowLabel, segments };
  });
</script>

{#snippet stepForward()}
  <Button size="sm" icon="chevron" onclick={() => (index = Math.min(index + 1, last))} aria-disabled={index < last ? undefined : true}>Step forward</Button>
{/snippet}
{#snippet progressNote()}
  Steps run in list order. Each step resolves its runtime expressions, calls its operation, then checks its success criteria{run.options.validateSchemas
    ? ' and validates its response against its OpenAPI schema'
    : ''}.
{/snippet}

<PageHeader
  title={`Run #${run.number}`}
  breadcrumb={[
    { label: 'Workflows', href: '/workflows' },
    { label: document ? `${document.title} - ${document.version}` : run.documentId, href: workflowTo },
    { label: 'Runs', href: workflowPath(run.documentId, run.workflowId, 'runs') },
    { label: `Run #${run.number}` }
  ]}
  align="start"
>
  {#snippet badge()}
    <RunBadges workflowId={run.workflowId} {documentLabel} href={workflowTo}>
      {#snippet chip()}
        {#if running}
          <span class="wfr-running">
            <LiveIndicator {paused} />
            <StatusBadge
              shape="bare"
              status="running"
              label={`Running · step ${Math.min(currentIndex + 1, total)} of ${total}${run.current && run.current.attempt > 1 ? ` · attempt ${run.current.attempt}` : ''}`}
            />
          </span>
        {:else}
          <ApiBadge label="result" value={passed ? 'passed' : 'failed'} status={passed ? 'success' : 'danger'} icon={passed ? 'check' : 'close-sm'} />
        {/if}
      {/snippet}
    </RunBadges>
    {#if payload.demo !== false}<DemoFlag />{/if}
  {/snippet}
  {#snippet actions()}
    {#if running}
      <Button aria-pressed={paused} icon={paused ? 'play' : 'pause'} onclick={() => (paused = !paused)}>{paused ? 'Resume' : 'Pause'} updates</Button>
      <Button href={runPath(run.id)} variant="primary" icon="skip">Skip to result</Button>
    {:else}
      <Button href={runPath(run.id, true)} icon="reset">Replay live</Button>
      <Button href={runSetupPath(run.documentId, run.workflowId, scenarioOf(run.id))} variant="primary" icon="play">Run again</Button>
    {/if}
  {/snippet}
  <RunMeta
    class="wfr-run-meta"
    items={[
      ...(document ? [{ term: 'Document', kind: 'url' as const, value: document.file, href: workflowPath(run.documentId, run.workflowId, 'source') }] : []),
      { term: 'Run against', value: targetLine(run) },
      { term: 'Started', value: formatRelative(run.startedAt), detail: formatClock(run.startedAt) },
      { term: running ? 'Elapsed' : 'Duration', kind: 'mono' as const, value: formatDuration(totals.ms) },
      ...(payload.demo === false ? [] : [{ term: 'Launched by', value: launchedBy(run) }])
    ]}
  />
</PageHeader>
<p class="sr-only" role="status" aria-live="polite" aria-atomic="true">{message}</p>

{#if isScenarioWorkflow}
  <div class="wfr-scenarios">
    <span id="wfr-scn-label" class="wfr-scenarios-label">Scenario</span>
    <ul aria-labelledby="wfr-scn-label" class="wfr-scenarios-list">
      {#each Object.keys(RUN_SCENARIOS) as key (key)}
        {@const s = RUN_SCENARIOS[key as RunScenario]}
        <li>
          <a href={runPath(s.runId)} class="wfr-scenario" aria-current={s.runId === run.id ? 'page' : undefined}>
            <span class="wfr-scenario-dot" data-tone={SCENARIO_DOT[key as RunScenario]} aria-hidden="true"></span>
            {s.label}
          </a>
        </li>
      {/each}
    </ul>
    <span class="wfr-scenarios-note">Three runs of restock-pastry for review: each opens on its result; Replay live plays it step by step.</span>
  </div>
{/if}

<div class="wfr-layout">
  <div class="wfr-main">
    {#if running}
      <RunProgress
        label="Progress"
        done={Math.min(currentIndex, total)}
        {total}
        caption="steps ended"
        nowLabel={progress.nowTitle}
        now={progress.nowLabel}
        segments={progress.segments}
        action={paused && live ? stepForward : undefined}
        note={progressNote}
      />
    {:else}
      <RunSummary {run} {totals} {workflow} {document} />
    {/if}
    {#if example}<RunExampleJudging {run} {example} pending={running} />{/if}
    <RunSteps {run} {workflow} {document} />
  </div>

  <aside aria-label="Run side panels" class="wfr-aside">
    <RunProperties {run} {documentLabel} {example} sourceOf={(stepId) => workflow?.steps.find((s) => s.stepId === stepId)?.sourceName} />
    {#if document && workflow}
      <RunApiPanel
        hint={`Launches the same run as run #${run.number}.`}
        documentApiId={document.apiId}
        documentTitle={document.title}
        documentVersion={document.version}
        documentLabel={documentLabel ?? ''}
        workflowId={run.workflowId}
        mockUrl={workflow.mockUrl}
        mixed={run.target.kind === 'mixed'}
        proposed={payload.demo !== false}
        body={apiBodyOf(run)}
      />
    {/if}
  </aside>
</div>
