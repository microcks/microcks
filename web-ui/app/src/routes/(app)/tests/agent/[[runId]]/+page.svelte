<script lang="ts">
  import { onDestroy, untrack } from 'svelte';
  import { ApiBadge, Button, Callout, LiveIndicator, StatusBadge } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import AgentTestResults from '#lib/components/runs/AgentTestResults.svelte';
  import AgentTestSetup from '#lib/components/runs/AgentTestSetup.svelte';
  import ProjectionPill from '#lib/components/runs/ProjectionPill.svelte';
  import RunMeta from '#lib/components/runs/RunMeta.svelte';
  import { api } from '#lib/api.js';
  import { formatRelative } from '#lib/format.js';
  import { agentElapsedMs, agentRunPassed, fmtMs, formatClock, type AgentTestRun, type AgentTestSetup as Setup } from '#lib/runs.js';

  let { data } = $props();

  /** The runner fills one matrix cell every 800 ms, as in the prototype. */
  const CHECK_MS = 800;

  const stored = $derived<AgentTestRun | undefined>(data.run);
  let phase = $state<'setup' | 'running' | 'done'>(untrack(() => (data.run ? 'done' : 'setup')));
  let run = $state<AgentTestRun | undefined>();
  let tick = $state(0);
  let replays = $state(0);
  let error = $state('');
  let busy = $state(false);
  let announcement = $state('');
  let timer: ReturnType<typeof setInterval> | undefined;

  // Navigating between /tests/agent and /tests/agent/:id resets the page state.
  $effect(() => {
    const id = data.runId;
    untrack(() => {
      stop();
      run = undefined;
      error = '';
      tick = 0;
      phase = id && data.run ? 'done' : 'setup';
    });
  });

  const current = $derived(run ?? stored);
  const id = $derived(phase === 'setup' && !current ? data.defaults.nextRunId : (current?.id ?? ''));
  const hasRun = $derived(phase !== 'setup' && Boolean(current));
  const running = $derived(phase === 'running');
  const n = $derived(current?.checks.length ?? 0);
  const elapsed = $derived(current ? agentElapsedMs(current.checks, running ? tick : current.checks.length) : 0);
  const passed = $derived(current ? agentRunPassed(current.checks) : true);
  const cardUrl = $derived(current?.setup.cardUrl ?? data.defaults.setup.cardUrl);

  function stop() {
    if (timer) clearInterval(timer);
    timer = undefined;
  }
  $effect(() => {
    if (running) {
      timer = setInterval(step, CHECK_MS);
      return stop;
    }
  });
  onDestroy(stop);

  const announce = (m: string) => (announcement = m);
  const focusTitle = () => requestAnimationFrame(() => document.getElementById('page-title')?.focus());

  function step() {
    if (tick + 1 >= n) {
      tick = n;
      phase = 'done';
      focusTitle();
    } else tick += 1;
  }

  async function start(setup: Setup) {
    busy = true;
    try {
      const result = await api<AgentTestRun>('/api/runs/agent', { method: 'POST', json: setup });
      run = result;
      tick = 0;
      error = '';
      phase = 'running';
      announce(`Run #${result.id} started: ${result.checks.length} checks.`);
      focusTitle();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      busy = false;
    }
  }

  function replay() {
    tick = 0;
    replays += 1;
    phase = 'running';
    announce(`Run #${id} replayed: ${n} checks.`);
    focusTitle();
  }

  function skip() {
    phase = 'done';
    focusTitle();
  }

  function edit() {
    phase = 'setup';
    focusTitle();
  }

  const conversationSubs = $derived(
    Object.fromEntries(data.defaults.conversations.map((c) => [c.id, `skill ${c.skillId} · ${c.turns} · expects ${c.expect}`]))
  );
</script>

<svelte:head><title>Agent test #{id} · Microcks</title></svelte:head>

<div class="agent-test">
  <PageHeader
    title="Run #{id}"
    breadcrumb={[{ label: 'Tests', href: '/tests' }, { label: 'Pastry concierge 1.0', href: '/services' }, { label: `Run #${id}` }]}
    align="start"
  >
    {#snippet badge()}
      <span class="run-badges">
        <a href="/services" class="run-badge-link"><ApiBadge protocol="A2A" name="Pastry concierge 1.0" size="lg" /></a>
        <ProjectionPill title="A2A does not exist in Microcks today: this screen projects a protocol module for v2" />
        <span class="demo-note muted" title="Runs are simulated by the BFF: PLACEHOLDER, no A2A runner exists yet">simulated runner · demo data</span>
        {#if !hasRun}
          <span role="status"><ApiBadge label="status" value="not started" /></span>
        {:else if running}
          <LiveIndicator />
          <StatusBadge shape="bare" status="running" label="Running · check {Math.min(tick + 1, n)} of {n}" />
        {:else}
          <span role="status"><ApiBadge label="result" value={passed ? 'passed' : 'failed'} status={passed ? 'success' : 'danger'} icon={passed ? 'check' : 'close-sm'} /></span>
        {/if}
      </span>
    {/snippet}
    {#snippet actions()}
      {#if hasRun}
        <Button icon="reset" onclick={replay}>Replay run</Button>
        {#if running}<Button variant="primary" icon="skip" onclick={skip}>Skip to result</Button>{/if}
      {/if}
    {/snippet}
    {#snippet children()}
      <RunMeta
        items={[
          { term: 'Endpoint', value: cardUrl, kind: 'url' },
          { term: 'Runner', value: 'A2A_CONFORMANCE', kind: 'chip' },
          ...(hasRun && current
            ? [
                { term: 'Started', value: formatRelative(current.startedAt), detail: formatClock(current.startedAt) },
                { term: running ? 'Elapsed' : 'Duration', id: 'duration', value: fmtMs(elapsed), kind: 'mono' as const }
              ]
            : []),
          { term: 'Launched by', value: current?.launchedBy ?? 'MA' }
        ]}
      />
    {/snippet}
  </PageHeader>

  <p class="sr-only" role="status" aria-live="polite">{announcement}</p>

  {#if data.error}
    <Callout tone="danger" live="assertive" title="The test run could not be loaded"><p>{data.error}</p></Callout>
    <p><Button href="/tests/agent">Start a new agent test</Button></p>
  {:else}
    {#if error}<Callout tone="danger" title="The test could not start" live="assertive"><p>{error}</p></Callout>{/if}
    {#if phase === 'setup' || !current}
      <AgentTestSetup defaults={data.defaults} initial={current?.setup ?? data.defaults.setup} {busy} onrun={start} />
    {:else}
      {#key `${current.id}-${replays}`}
        <AgentTestResults
          run={current}
          {phase}
          {tick}
          {conversationSubs}
          onstep={step}
          onreplay={replay}
          onedit={edit}
          onannounce={announce}
        />
      {/key}
    {/if}
  {/if}
</div>
