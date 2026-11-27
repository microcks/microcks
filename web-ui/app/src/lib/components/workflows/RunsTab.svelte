<script lang="ts">
  import { Button, Chip, Icon, MethodTag, type IconName } from '@microcks/deep-ink';
  import { formatRelative, formatShortClock } from '#lib/format.js';
  import { callTotals, stoppedStep, verdictExplanation } from '#lib/workflows/describe.js';
  import { matchesRunKind, runPath, runSetupPath } from '#lib/workflows/paths.js';
  import { originWord } from '#lib/workflows/run-facts.js';
  import { formatDuration } from '#lib/workflows/time.js';
  import type { ArazzoDocument, RunKindFilter, Session, StepStatus, Workflow, WorkflowRun } from '#lib/workflows/types.js';

  type Filter = Exclude<RunKindFilter, 'any'> | 'all';

  interface Props {
    workflow: Workflow;
    document: ArazzoDocument;
    runs: WorkflowRun[];
    sessions?: Session[];
    initialFilter?: string;
  }

  const FILTERS: { id: Filter; label: string }[] = [
    { id: 'all', label: 'All origins' },
    { id: 'implementations', label: 'Implementations' },
    { id: 'mocks', label: 'Microcks mocks' },
    { id: 'mock-url', label: 'Mock URL' },
    { id: 'session', label: 'Session' }
  ];
  const CELL: Record<StepStatus, { icon: IconName; word: string }> = {
    passed: { icon: 'check', word: 'Passed' },
    failed: { icon: 'cross', word: 'Failed' },
    'not-run': { icon: 'dash', word: 'Not run' }
  };

  let { workflow, document, runs, sessions = [], initialFilter }: Props = $props();

  const initial = initialFilter === 'impl' ? 'implementations' : initialFilter === 'mockurl' ? 'mock-url' : initialFilter;
  let filter = $state<Filter>(FILTERS.some((f) => f.id === initial) ? (initial as Filter) : 'all');
  let copied = $state(false);

  const match = (r: WorkflowRun, f: Filter) => f === 'all' || matchesRunKind(r, f);
  const shown = $derived(runs.filter((r) => match(r, filter)));
  const label = $derived(FILTERS.find((f) => f.id === filter)?.label ?? '');
  const total = $derived(runs[0]?.number ?? runs.length);
  const n = $derived(workflow.steps.length);
  const api = $derived(`/api/arazzo/documents/{documentId}/workflows/${workflow.workflowId}/runs`);
  const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

  function originSub(run: WorkflowRun): string {
    if (run.origin.kind === 'session') return `${run.origin.sessionId} · ${run.origin.app}`;
    if (run.origin.kind === 'mock-url') return 'POST on the Mock URL';
    if (run.target.kind === 'implementations') {
      const url = Object.values(run.target.baseUrls)[0] ?? '';
      return `${/staging/.test(url) ? 'staging' : 'implementation'} · ${url.replace(/^https?:\/\//, '')}`;
    }
    return run.options.example ? `example ${run.options.example}` : 'Microcks mocks';
  }

  function endOf(run: WorkflowRun): string {
    const s = stoppedStep(run);
    if (run.status === 'RUNNING') return 'Running';
    if (!s) return 'Runs to its end';
    if (run.status === 'SUCCESS') return `Ends early at ${s.stepId}`;
    return `${run.origin.kind === 'session' ? 'Fails' : 'Stops'} at ${s.stepId}`;
  }

  function byOf(run: WorkflowRun): string {
    switch (run.origin.kind) {
      case 'session':
        return `Verification requested by ${run.origin.app}`;
      case 'mock-url':
        return `Answered ${run.status === 'SUCCESS' ? '200 with the outputs' : '409 with the failed step'}`;
      case 'api':
        return 'Started from the API';
      default:
        return `Run by MA${run.target.kind === 'implementations' && run.options.validateSchemas ? ' · schema validation on' : ''}`;
    }
  }

  function noteOf(run: WorkflowRun): { text: string; tone: 'fail' | 'pass' | 'info' } {
    const s = stoppedStep(run);
    if (run.status === 'FAILURE') {
      if (run.origin.kind === 'session') {
        const sessionId = run.origin.sessionId;
        const session = sessions.find((x) => x.id === sessionId);
        const unexpected = session?.calls.filter((c) => c.stepId === 'unexpected').length ?? 0;
        const parts = [
          s ? `${s.flow.title.charAt(0).toLowerCase()}${s.flow.title.slice(1)} on ${s.stepId}` : undefined,
          unexpected ? `${unexpected} unexpected ${unexpected === 1 ? 'call' : 'calls'}` : undefined
        ].filter(Boolean);
        return { text: `The calls of the session do not follow the workflow${parts.length ? `: ${parts.join(', ')}` : ''}.`, tone: 'fail' };
      }
      if (s && s.schema.state === 'fail') {
        const bad = s.response?.lines.find((l) => l.bad);
        const field = bad ? /"([^"]+)":\s*(.+?),?$/.exec(bad.text.trim()) : null;
        const expected = bad?.note?.replace(/^schema:\s*/, '');
        const schema = s.schema.ref.split(' · ').pop();
        const attempts = s.attempts.length;
        const why = field && expected ? `/${field[1]} is a ${field[2]?.startsWith('"') ? 'string' : 'value'} where the ${schema} schema expects a ${expected}` : s.schema.message;
        return { text: `${s.stepId} failed${attempts > 1 ? ` after ${attempts} attempts` : ''}: schema validation, ${why}.`, tone: 'fail' };
      }
      return { text: verdictExplanation(run), tone: 'fail' };
    }
    if (run.origin.kind === 'mock-url') {
      const outs = Object.entries(run.outputs).map(([k, v]) => `${k} = ${String(v)}`);
      return { text: outs.length ? `Outputs ${outs.join(' · ')}` : 'No outputs.', tone: 'info' };
    }
    if (run.options.example && run.target.kind === 'mocks') return { text: `Matches the example ${run.options.example}: every expectation holds.`, tone: 'pass' };
    return { text: verdictExplanation(run), tone: run.status === 'SUCCESS' ? 'pass' : 'info' };
  }

  async function copyApi() {
    try {
      await navigator.clipboard.writeText(`POST ${api}`);
      copied = true;
    } catch {
      /* clipboard blocked */
    }
  }
</script>

<section aria-label="Runs" class="wf-runs-panel">
  <div class="wf-runs-head">
    <div class="wf-runs-left">
      <span class="wf-runs-count">Last {runs.length} of {total} {total === 1 ? 'run' : 'runs'}</span>
      <div role="group" aria-label="Filter runs by origin" class="wf-runs-filters">
        {#each FILTERS as f (f.id)}
          <Chip size="lg" pressed={f.id === filter} count={runs.filter((r) => match(r, f.id)).length} onclick={() => (filter = f.id)}>{f.label}</Chip>
        {/each}
      </div>
    </div>
    {#if workflow.runnability.runnable}
      <Button variant="primary" size="compact" icon="play" href={runSetupPath(document.id, workflow.workflowId)}>Run workflow</Button>
    {/if}
  </div>

  {#if shown.length === 0}
    <div class="wf-runs-empty">
      <span role="status" class="wf-runs-empty-title">{runs.length === 0 ? 'This workflow has no run yet' : `No run with the origin ${label} among the last runs`}</span>
      {#if runs.length > 0}<Button size="compact" onclick={() => (filter = 'all')}>Show all runs</Button>{/if}
    </div>
  {:else}
    <div class="wf-runs-scroll">
      <ul class="wf-runs-list" aria-label="Runs of {workflow.workflowId}{filter === 'all' ? '' : ` with the origin ${label}`}">
        <li aria-hidden="true" class="wf-runs-row wf-runs-header">
          <span data-col="run">Run</span>
          <span data-col="result">Result</span>
          <span data-col="origin">Origin</span>
          <span data-col="inputs">Inputs</span>
          <span data-col="steps">Steps 1 → {n}</span>
          <span data-col="end">End</span>
          <span data-col="dur">Duration</span>
          <span data-col="started">Started</span>
          <span data-col="chev"></span>
        </li>
        {#each shown as r (r.id)}
          {@const passed = r.status === 'SUCCESS'}
          {@const ms = callTotals(r).ms}
          {@const note = noteOf(r)}
          {@const band = workflow.steps.map((s) => ({ id: s.stepId, st: (r.steps.find((x) => x.stepId === s.stepId)?.status ?? 'not-run') as StepStatus }))}
          <li class="wf-runs-item">
            <a href={runPath(r.id)} class="wf-runs-row wf-runs-link">
              <span data-col="run" class="wf-runs-id">#{r.number}</span>
              <span data-col="result" class="wf-runs-result" data-tone={passed ? 'pass' : r.status === 'FAILURE' ? 'fail' : 'off'}>
                <Icon name={passed ? 'check-circle' : r.status === 'FAILURE' ? 'x-circle' : 'running'} size={14} />{passed ? 'Passed' : r.status === 'FAILURE' ? 'Failed' : 'Running'}
              </span>
              <span data-col="origin" class="wf-runs-origin"><Chip size="sm">{originWord(r)}</Chip><span class="wf-runs-sub">{originSub(r)}</span></span>
              <span data-col="inputs" class="wf-runs-inputs">{Object.entries(r.options.inputs).map(([k, v]) => `${k}=${String(v)}`).join(' · ') || 'no input'}</span>
              <span data-col="steps" role="img" aria-label="Steps: {band.map((b, i) => `${i + 1} · ${b.id} · ${CELL[b.st].word}`).join('; ')}" class="wf-runs-band">
                {#each band as b (b.id)}<span class="wf-runs-cell" data-status={b.st}><Icon name={CELL[b.st].icon} size={9} /></span>{/each}
              </span>
              <span data-col="end" class="wf-runs-end" data-tone={r.status === 'FAILURE' ? 'fail' : undefined}>{endOf(r)}</span>
              <span data-col="dur" class="wf-runs-dur">{ms > 0 ? formatDuration(ms) : '—'}</span>
              <span data-col="started" class="wf-runs-started"><span>{cap(formatRelative(r.startedAt))} · {formatShortClock(r.startedAt)}</span><span class="wf-runs-by">{byOf(r)}</span></span>
              <span data-col="chev" class="wf-runs-chev"><Icon name="chevron" size={14} /><span class="sr-only">Open run</span></span>
            </a>
            <div class="wf-runs-note" data-tone={note.tone}>{note.text}</div>
          </li>
        {/each}
      </ul>
    </div>
    <div aria-hidden="true" class="wf-runs-legend">
      <span><span class="wf-runs-cell" data-status="passed" data-size="sm"><Icon name="check" size={7} /></span>Passed</span>
      <span><span class="wf-runs-cell" data-status="failed" data-size="sm"><Icon name="cross" size={7} /></span>Failed (criteria, schema validation or verification)</span>
      <span><span class="wf-runs-cell" data-status="not-run" data-size="sm"><Icon name="dash" size={7} /></span>Not run</span>
    </div>
  {/if}

  <div class="wf-runs-pipeline">
    <span class="wf-runs-pipeline-label">Run it from a pipeline</span>
    <span class="wf-runs-api"><MethodTag size="xs" method="POST" />{api}</span>
    <Button size="sm" aria-label="Copy the runs endpoint" onclick={copyApi}>{copied ? 'Copied' : 'Copy'}</Button>
  </div>
</section>
