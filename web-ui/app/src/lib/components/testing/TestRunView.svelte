<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import { ApiBadge, Button, Callout, ConformanceBand, Icon, LiveIndicator, MethodTag, StatusBadge } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import { formatRelative } from '#lib/format.js';
  import type { Service } from '#lib/types.js';
  import CiPanel from './CiPanel.svelte';
  import RunProgress, { type RunSegment } from './RunProgress.svelte';
  import RunnerLog from './RunnerLog.svelte';
  import StepDiff from './StepDiff.svelte';
  import {
    TEST_RESULT_WORD,
    basePath,
    buildRunnerLog,
    ciCommandForRun,
    elapsedMs,
    formatClock,
    runConformance,
    summarize,
    type TestRunDto
  } from './model.js';

  interface Props {
    run: TestRunDto;
    /** All runs of the service, any order: used for the trend against the previous run. */
    history: TestRunDto[];
    service?: Service;
  }

  let { run: base, history, service }: Props = $props();

  /** The run "executes" one step every 1.3 s, as in the prototype. */
  const STEP_MS = 1300;
  /** A live run opens with 3 steps already finished. */
  const LIVE_START = 3;

  type StepState = 'pass' | 'fail' | 'run' | 'pend';
  const LOOK: Record<StepState, { word: string; status: 'conformant' | 'failed' | 'running' | 'never' }> = {
    pass: { word: 'Passed', status: 'conformant' },
    fail: { word: 'Failed', status: 'failed' },
    run: { word: 'Running', status: 'running' },
    pend: { word: 'Queued', status: 'never' }
  };
  const SEGMENT: Record<StepState, RunSegment> = { pass: 'done', fail: 'failed', run: 'current', pend: 'queued' };

  const steps = untrack(() => base.details.steps);
  const total = steps.length;
  let running = $state(untrack(() => !!base.details.live && total > 0));
  let tick = $state(untrack(() => (base.details.live ? Math.min(LIVE_START, total) : total)));
  /** `undefined` follows the run (latest finished step while running, the first failed one when done); '' = all closed. */
  let sel = $state<string>();
  let ciOpen = $state(false);
  let verdictEl = $state<HTMLElement>();
  let focusVerdict = false;
  let announcement = $state('');

  const done = $derived(!running && base.details.result !== 'running');
  const finished = $derived(done ? total : tick);

  function advance() {
    if (!running) return;
    if (tick >= total) {
      running = false;
      announcement = `Test run #${base.testNumber} ${TEST_RESULT_WORD[base.details.result].toLowerCase()}: ${passCount} of ${total} steps passed.`;
    } else tick += 1;
  }

  let timer: ReturnType<typeof setInterval> | undefined;
  $effect(() => {
    if (!running) return;
    timer = setInterval(advance, STEP_MS);
    return () => clearInterval(timer);
  });
  onDestroy(() => clearInterval(timer));

  $effect(() => {
    if (done && focusVerdict && verdictEl) {
      focusVerdict = false;
      verdictEl.focus();
    }
  });

  function replay() {
    tick = 0;
    running = total > 0;
    sel = undefined;
    ciOpen = false;
  }
  function stepForward() {
    if (tick >= total) focusVerdict = true;
    advance();
  }
  function skip() {
    focusVerdict = true;
    running = false;
    tick = total;
    sel = undefined;
  }

  const stateOf = (i: number): StepState => {
    const s = steps[i];
    if (i < finished && s) return s.result === 'passed' ? 'pass' : 'fail';
    return i === tick && running ? 'run' : 'pend';
  };
  const states = $derived(steps.map((_, i) => stateOf(i)));
  const passCount = $derived(states.filter((s) => s === 'pass').length);
  const failCount = $derived(states.filter((s) => s === 'fail').length);
  const queuedCount = $derived(total - passCount - failCount);

  const operations = $derived.by(() => {
    const labels = base.details.operationLabels;
    const ids = labels.length > 0 ? labels.map((l) => l.id) : [...new Set(steps.map((s) => s.operationId))];
    return ids.map((id) => {
      const label = labels.find((l) => l.id === id);
      const first = steps.find((s) => s.operationId === id);
      const idx = steps.flatMap((s, i) => (s.operationId === id ? [i] : []));
      return { id, method: label?.method ?? first?.method ?? 'GET', path: label?.path ?? first?.path ?? id, idx };
    });
  });
  const opStates = $derived(
    operations.map((op): StepState => {
      const st = op.idx.map((i) => states[i]);
      const fin = st.filter((s) => s === 'pass' || s === 'fail').length;
      if (fin === st.length) return st.includes('fail') ? 'fail' : 'pass';
      return st.includes('run') || fin > 0 ? 'run' : 'pend';
    })
  );

  const result = $derived(base.details.result);
  const firstFailed = $derived(steps.find((s) => s.result === 'failed'));
  const autoSel = $derived(done ? (firstFailed ?? steps[total - 1])?.id : steps[tick - 1]?.id);
  const selId = $derived(sel === undefined ? autoSel : sel);

  const log = $derived(buildRunnerLog(base, done ? result : 'running', finished, done));
  const elapsed = $derived(elapsedMs(steps, finished));
  const score = $derived(Math.round((100 * passCount) / Math.max(1, total)));
  const waitSec = $derived(Math.round(base.timeout / 1000));
  const apiRef = $derived(service ? `${service.name}:${service.version}` : base.serviceId);
  const ci = $derived(ciCommandForRun(apiRef, base.testedEndpoint, base.runnerType, waitSec));
  const current = $derived(running ? steps[tick] : undefined);
  const prefix = $derived(basePath(base.testedEndpoint));
  const serviceLabel = $derived(service ? `${service.name} ${service.version}`.trim() : base.serviceId);

  const previous = $derived(
    history
      .filter((r) => r.testDate < base.testDate)
      .sort((a, b) => b.testDate.localeCompare(a.testDate))
      .map(summarize)[0]
  );
  const previousScore = $derived(previous ? runConformance(previous) : 0);
  const delta = $derived(previous ? score - previousScore : 0);

  const brokenOps = $derived(operations.filter((_, i) => opStates[i] === 'fail'));
  const failedSchema = $derived(firstFailed?.schemaRef?.split('/').pop());
  const bandOutcomes = $derived(opStates.map((s) => (s === 'pass' ? 'pass' : s === 'fail' ? 'fail' : 'untested') as 'pass' | 'fail' | 'untested'));
  const levelOf = (s: number) => (s >= 80 ? 'high' : s >= 50 ? 'mid' : 'low');
</script>

<PageHeader
  title="Run #{base.testNumber}"
  breadcrumb={[
    { label: 'APIs & Services', href: '/services' },
    { label: serviceLabel, href: `/services/${base.serviceId}` },
    { label: 'Tests', href: `/tests/service/${base.serviceId}` },
    { label: `Run #${base.testNumber}` }
  ]}
  align="start"
>
  {#snippet badge()}
    <span class="tr-badges">
      <a href="/services/{base.serviceId}" class="tr-badge-link"><ApiBadge protocol={service?.protocol ?? 'REST'} name={serviceLabel} size="lg" /></a>
      {#if running}
        <LiveIndicator />
        <StatusBadge shape="bare" status="running" label="Running · step {Math.min(tick + 1, total)} of {total}" />
      {:else}
        <ApiBadge label="result" value={TEST_RESULT_WORD[result].toLowerCase()} status={result === 'passed' ? 'success' : result === 'failed' ? 'danger' : 'warning'} />
      {/if}
    </span>
  {/snippet}
  {#snippet children()}
    <div class="tr-meta">
      <span><span class="tr-meta-key">Endpoint</span><span class="tr-meta-endpoint">{base.testedEndpoint}</span></span>
      <span><span class="tr-meta-key">Runner</span><span class="tr-meta-runner">{base.runnerType}</span></span>
      <span><span class="tr-meta-key">Started</span><span>{formatRelative(base.testDate)}</span><span class="tr-meta-date">{formatClock(base.testDate)}</span></span>
      <span><span class="tr-meta-key">{done ? 'Duration' : 'Elapsed'}</span><span class="tr-meta-mono">{elapsed} ms</span></span>
      <span><span class="tr-meta-key">Timeout</span><span class="tr-meta-mono">{waitSec} s</span></span>
      {#if base.details.launchedBy}<span><span class="tr-meta-key">Launched by</span><span>{base.details.launchedBy}</span></span>{/if}
    </div>
  {/snippet}
  {#snippet actions()}
    <Button icon="reset" onclick={replay}>Replay run</Button>
    {#if running}<Button variant="primary" icon="skip" onclick={skip}>Skip to result</Button>{/if}
  {/snippet}
</PageHeader>

{#if base.simulated || base.demo}
  <div class="tr-sim">
    <Callout tone="warning" size="sm" title={base.simulated ? 'Simulated run · no test engine' : 'Demo data · placeholder run'}>
      {#if base.simulated}
        Your request was recorded, but this prototype has no test engine: the steps below replay a simulation and every selected operation is reported as passed. Nothing was sent to {base.testedEndpoint}.
      {:else}
        This run belongs to the placeholder history of this prototype. It was not produced by a test engine and nothing was sent to {base.testedEndpoint}.
      {/if}
    </Callout>
  </div>
{/if}

<p class="sr-only" role="status">{announcement}</p>

<div class="tr-top">
  {#if running}
    <RunProgress
      class="tr-card"
      done={finished}
      {total}
      caption="test steps done"
      nowLabel="Now calling"
      now={current ? `${current.method} ${base.testedEndpoint}${current.path}` : 'Computing conformance score…'}
      segments={states.map((st) => SEGMENT[st])}
      counts={[
        { kind: 'pass', text: `${passCount} passed` },
        { kind: 'fail', text: `${failCount} failed` },
        { kind: 'pending', text: `${queuedCount} queued` }
      ]}
      note="Microcks replays every example of the contract against the endpoint, then validates each response body with the schema of the operation."
    >
      {#snippet action()}
        <Button size="sm" icon="chevron" class="tr-step-forward" onclick={stepForward}>Step forward</Button>
      {/snippet}
    </RunProgress>
  {:else}
    <section aria-label="Verdict" class="tr-verdict" bind:this={verdictEl} tabindex="-1">
      <div class="tr-verdict-head">
        <div class="tr-score">
          <span class="tr-score-label">Conformance score</span>
          <span class="tr-score-value" data-level={levelOf(score)}>{score}%</span>
          {#if previous}
            <span class="tr-trend" data-dir={delta < 0 ? 'down' : delta > 0 ? 'up' : 'flat'}>
              <Icon name={delta < 0 ? 'arrow-down' : delta > 0 ? 'arrow-up' : 'dash'} size={13} />
              {delta === 0 ? 'Same as' : `${Math.abs(delta)} pts vs`} run #{previous.number} <span class="tr-trend-prev">{previousScore}%</span>
            </span>
          {/if}
        </div>
        <div class="tr-verdict-text">
          <span class="tr-verdict-word" data-result={result}>
            <Icon name={result === 'passed' ? 'check' : result === 'failed' ? 'cross' : 'clock'} size={18} />
            {result === 'passed' ? 'Passed' : result === 'failed' ? 'Failed' : 'Timed out'}
          </span>
          <span class="tr-verdict-summary">
            <span class="tr-mono-strong">{passCount}/{total}</span> test steps passed ·
            {brokenOps.length === 0 ? `all ${operations.length} operations match their contract` : `${brokenOps.length} of ${operations.length} operations broke ${brokenOps.length === 1 ? 'its' : 'their'} contract`}
          </span>
          {#if brokenOps[0] && result === 'failed' && failedSchema}
            <span class="tr-verdict-detail"><span class="tr-mono">{brokenOps[0].method} {brokenOps[0].path}</span> returns a body that does not match the <span class="tr-mono">{failedSchema}</span> schema.</span>
          {:else if base.runnerMessage}
            <span class="tr-verdict-detail">{base.runnerMessage}</span>
          {/if}
        </div>
      </div>

      <div class="tr-band">
        <span class="tr-band-caption">Conformance band · one cell per operation</span>
        <ConformanceBand outcomes={bandOutcomes} />
      </div>

      <div class="tr-actions">
        <Button variant="primary" icon="play" href="/tests/create?service={encodeURIComponent(base.serviceId)}">Run again</Button>
        <Button href="/tests/service/{encodeURIComponent(base.serviceId)}">All runs</Button>
        <Button href="/services/{encodeURIComponent(base.serviceId)}">Back to service</Button>
        <Button icon="code" class="ci-panel-toggle" aria-expanded={ciOpen} aria-controls="ci-panel" onclick={() => (ciOpen = !ciOpen)}>Add to CI</Button>
      </div>

      {#if ciOpen}
        <CiPanel id="ci-panel" title="Run this test in your pipeline" hint="Same service, endpoint and runner. The command exits non-zero when the test fails, so a contract break stops the build." command={ci} copyLabel="Copy command">
          {#snippet footnote()}
            Set <span class="tr-mono">MICROCKS_CLIENT_SECRET</span> from the <span class="tr-mono">microcks-serviceaccount</span> credentials. The same parameters work with the GitHub Action, GitLab CI, Jenkins and Tekton integrations.
          {/snippet}
        </CiPanel>
      {/if}
    </section>
  {/if}

  <RunnerLog class="tr-log" lines={log.map((l) => ({ time: l.time, level: l.level, text: l.message, emphasis: l.tone === 'emphasis', error: l.tone === 'error' }))}>
    {#snippet status()}
      {#if running}<LiveIndicator variant="inline" label="live · streaming" />{:else}{log.length} lines · completed{/if}
    {/snippet}
  </RunnerLog>
</div>

<section aria-labelledby="tr-results-title" class="tr-results">
  <div class="tr-results-head">
    <h2 id="tr-results-title" class="tr-results-title">Results</h2>
    <span class="tr-results-hint">{operations.length} operations · {total} test steps · select a finished step to compare the contract with the actual response</span>
  </div>

  {#each operations as op, oi (op.id)}
    {@const opState = opStates[oi] ?? 'pend'}
    {@const fin = op.idx.filter((i) => i < finished)}
    {@const ms = fin.reduce((n, i) => n + (steps[i]?.ms ?? 0), 0)}
    <div class="tr-op" data-state={opState}>
      <div class="tr-op-head">
        <MethodTag method={op.method} class="tr-op-method" />
        <h3 class="tr-op-path">{op.path}</h3>
        <span class="tr-op-count">{op.idx.length} {op.idx.length === 1 ? 'step' : 'steps'}{fin.length > 0 && fin.length < op.idx.length ? ` · ${fin.length} done` : ''}</span>
        <span class="tr-op-segs" aria-hidden="true">{#each op.idx as i (i)}<span data-state={states[i]}></span>{/each}</span>
        <StatusBadge shape="bare" status={LOOK[opState].status} label={LOOK[opState].word} class="tr-state" />
        <span class="tr-op-ms">{fin.length ? `${ms} ms` : '—'}</span>
      </div>

      {#each op.idx as i (i)}
        {@const step = steps[i]}
        {#if step}
          {@const st = states[i] ?? 'pend'}
          {@const isFin = st === 'pass' || st === 'fail'}
          {@const open = isFin && selId === step.id}
          <div class="tr-step">
            <button
              type="button"
              class="tr-step-row"
              data-state={st}
              aria-expanded={open}
              aria-controls={open ? `tr-diff-${step.id}` : undefined}
              aria-disabled={!isFin}
              onclick={() => {
                if (isFin) sel = open ? '' : step.id;
              }}
            >
              <span class="tr-step-name">{step.name}</span>
              <span class="tr-step-req">{step.method} {prefix}{step.path}{step.requestBody ? `  ${step.requestBody}` : ''}</span>
              <span class="tr-step-code" data-fin={isFin ? '' : undefined}>{isFin ? (step.httpStatus ?? 200) : '—'}</span>
              <span class="tr-step-ms">{isFin ? `${step.ms} ms` : '—'}</span>
              <StatusBadge shape="bare" status={LOOK[st].status} label={LOOK[st].word} class="tr-state" />
              <span class="tr-step-chevron" aria-hidden="true">{#if isFin}<Icon name="chevron" size={16} />{/if}</span>
            </button>
            {#if open}<StepDiff id="tr-diff-{step.id}" {step} run={base} />{/if}
          </div>
        {/if}
      {/each}
    </div>
  {/each}
</section>
