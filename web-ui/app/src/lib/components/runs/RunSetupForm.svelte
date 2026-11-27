<script lang="ts">
  import { tick, untrack } from 'svelte';
  import { goto } from '$app/navigation';
  import { ApiBadge, Button, Callout, Chip, Choice, Facts, Field, Input, Select } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import DemoFlag from '#lib/components/workflows/DemoFlag.svelte';
  import RunApiPanel from './RunApiPanel.svelte';
  import RunBadges from './RunBadges.svelte';
  import RunMeta from './RunMeta.svelte';
  import { api } from '#lib/api.js';
  import {
    timeoutOrDefault,
    typedInputs,
    validateRunRequest,
    type RunSetupPayload,
    type RunTargetKind,
    type StartRunInput,
    type Where
  } from '#lib/runs-workflow.js';
  import { runPath, runSetupPath, workflowPath } from '#lib/workflows/paths.js';

  interface Props {
    setup: RunSetupPayload;
    exampleParam?: string;
  }

  let { setup, exampleParam }: Props = $props();

  const { workflow, document, defaults } = untrack(() => setup);
  const sources = [...new Set(workflow.steps.map((s) => s.sourceName))];
  const rawInputs = (id: string) => {
    const ex = setup.examples.find((e) => e.id === id);
    return ex ? Object.fromEntries(Object.entries(ex.inputs).map(([k, v]) => [k, String(v)])) : undefined;
  };
  const initialExample = untrack(() => (exampleParam && setup.examples.some((e) => e.id === exampleParam) ? exampleParam : defaults.example));

  let targetValue = $state<string>(defaults.target);
  const target = $derived(targetValue as RunTargetKind);
  let baseUrls = $state<Record<string, string>>({ ...defaults.baseUrls });
  let perSource = $state<Record<string, Where>>({ ...Object.fromEntries(sources.map((n) => [n, 'mock' as Where])), ...defaults.perSource });
  let secret = $state(defaults.secret);
  let example = $state(initialExample);
  let inputs = $state<Record<string, string>>(untrack(() => rawInputs(initialExample) ?? { ...defaults.inputs }));
  let validate = $state(defaults.validateSchemas);
  let timeoutMs = $state(defaults.timeoutMs);
  let submitted = $state(false);
  let busy = $state(false);
  let failure = $state('');
  let announcement = $state('');
  let summary = $state<HTMLElement>();

  const chosen = $derived(setup.examples.find((e) => e.id === example));
  const detached = $derived(example === '');
  const effectivePer = (name: string): Where => perSource[name] ?? 'mock';
  const usesImplementation = $derived(target === 'implementations' || (target === 'mixed' && Object.values(perSource).includes('implementation')));

  const errors = $derived(
    validateRunRequest({
      workflowId: workflow.workflowId,
      notRunnableMessage: workflow.runnability.runnable ? undefined : workflow.runnability.message,
      target,
      sources,
      baseUrls,
      perSource,
      inputs: workflow.inputs.map((i) => ({ name: i.name, type: i.type, value: inputs[i.name] ?? '' })),
      timeoutMs
    })
  );
  const errorOf = (field: string) => (submitted ? errors.find((e) => e.field === field)?.message : undefined);

  function pickExample(id: string) {
    example = id;
    const next = rawInputs(id);
    if (next) inputs = next;
  }
  function setInput(name: string, value: string) {
    inputs[name] = value;
    if (example) example = '';
  }

  const request = $derived<StartRunInput>({
    documentId: document.id,
    workflowId: workflow.workflowId,
    target,
    baseUrls: usesImplementation ? baseUrls : {},
    perSource: target === 'mixed' ? perSource : undefined,
    secret: usesImplementation && secret ? secret : undefined,
    example: example || undefined,
    inputs,
    validateSchemas: validate,
    timeoutMs: timeoutOrDefault(timeoutMs)
  });

  async function launch(e: SubmitEvent) {
    e.preventDefault();
    submitted = true;
    failure = '';
    if (errors.length > 0) {
      await tick();
      summary?.focus();
      return;
    }
    busy = true;
    try {
      const { runId } = await api<{ runId: string }>('/api/runs/workflow-runs', { method: 'POST', json: request });
      announcement = 'Run started.';
      await goto(runPath(runId, true));
    } catch (err) {
      failure = err instanceof Error ? err.message : String(err);
      busy = false;
    }
  }

  const TARGETS: { value: RunTargetKind; label: string; proposed?: boolean; description: string }[] = [
    { value: 'mocks', label: 'Microcks mocks', description: 'Each step calls the mocks of its linked API, for example /rest/API+Pastry/2.0. Checks that the mock examples form a coherent scenario.' },
    { value: 'implementations', label: 'Implementations', description: 'Each step calls the base URL you set per source description. Checks the real services against the workflow.' },
    { value: 'mixed', label: 'Mixed', proposed: true, description: 'Per source description: its mocks or its implementation URL.' }
  ];
  const targetLabel = $derived(TARGETS.find((t) => t.value === target)?.label ?? '');
  const documentLabel = `${document.title} ${document.version}`;
  const workflowTo = workflowPath(document.id, workflow.workflowId);
  const unused = document.sources.map((s) => s.name).filter((n) => !sources.includes(n));

  function helpOf(name: string): string | undefined {
    for (const st of workflow.steps) {
      const p = st.parameters.find((x) => x.value === `$inputs.${name}`);
      if (p) return `${p.in === 'query' ? 'Query parameter' : p.in === 'path' ? 'Path parameter' : 'Header'} ${p.name} of ${st.stepId}`;
    }
    const b = workflow.steps.find((st) => st.requestBody?.includes(`{$inputs.${name}}`));
    const key = b?.requestBody?.match(new RegExp(`"([^"]+)"\\s*:\\s*"\\{\\$inputs\\.${name}\\}"`))?.[1];
    return b ? `Body /${key ?? name} of ${b.stepId}` : undefined;
  }
  const launchHint = $derived(
    `${workflow.workflowId} · ${example ? `example ${example}` : 'own inputs'} against ${target === 'mocks' ? 'the mocks' : target === 'implementations' ? 'implementations' : 'mixed targets'}`
  );
  const apiBody = $derived({
    inputs: typedInputs(workflow.inputs, inputs),
    timeout: request.timeoutMs,
    ...(target !== 'mocks'
      ? { implementations: target === 'implementations' ? baseUrls : Object.fromEntries(Object.entries(baseUrls).filter(([k]) => effectivePer(k) === 'implementation')) }
      : {}),
    ...(target === 'mixed' ? { target: { kind: 'mixed' as const, perSource: Object.fromEntries(sources.map((name) => [name, effectivePer(name)])) } } : {}),
    // As the BFF sends it: the secret is for the implementations, so a mixed run with none of them leaves it out.
    ...(request.secret && (target !== 'mixed' || usesImplementation) ? { secretName: request.secret } : {}),
    validateSchema: validate,
    ...(example ? { example } : {})
  });
  const sourceId = (name: string) => `${name}`;
</script>

<PageHeader
  title="Run workflow"
  breadcrumb={[
    { label: 'Workflows', href: '/workflows' },
    { label: `${document.title} - ${document.version}`, href: workflowTo },
    { label: 'Runs', href: workflowPath(document.id, workflow.workflowId, 'runs') },
    { label: 'Run workflow' }
  ]}
  align="start"
>
  {#snippet badge()}
    <RunBadges workflowId={workflow.workflowId} {documentLabel} href={workflowTo}>
      {#snippet chip()}<ApiBadge label="status" value="not started" icon="circle" />{/snippet}
    </RunBadges>
    {#if setup.demo !== false}<DemoFlag />{/if}
  {/snippet}
  <RunMeta
    class="wfr-run-meta"
    items={[
      { term: 'Document', kind: 'url', value: document.file, href: workflowPath(document.id, workflow.workflowId, 'source') },
      { term: 'Run against', value: targetLabel },
      { term: 'Started', value: 'not yet' },
      { term: 'Elapsed', value: '—' },
      ...(setup.demo === false ? [] : [{ term: 'Launched by', value: 'MA' }])
    ]}
  />
</PageHeader>
<p class="sr-only" role="status" aria-live="polite">{announcement}</p>

<div class="wfr-layout">
  <div class="wfr-main">
    <form class="wfr-card wfr-setup" novalidate aria-labelledby="wfr-setup-h" onsubmit={launch}>
      <div class="wfr-setup-head">
        <h2 id="wfr-setup-h" class="wfr-setup-title">Run workflow</h2>
        <span class="wfr-muted">Choose what the steps call, fill the inputs or play an example, then run.</span>
      </div>

      <div class="wfr-setup-group">
        <Field error={errorOf('workflowId')} class="wfr-field wfr-w-420">
          {#snippet label()}<span class="wfr-legend">Workflow</span>{/snippet}
          {#snippet children(c)}
            <Select
              {...c}
              class="wfr-mono-control"
              value={workflow.workflowId}
              onchange={(e) => {
                const w = setup.workflows.find((x) => x.workflowId === e.currentTarget.value);
                if (w) goto(runSetupPath(w.documentId, w.workflowId));
              }}
            >
              {#each setup.workflows as w (`${w.documentId}/${w.workflowId}`)}
                <option value={w.workflowId}>{w.workflowId} · {w.steps.length} steps{w.runnability.runnable ? '' : ' · not runnable'}</option>
              {/each}
            </Select>
          {/snippet}
        </Field>
        {#if !workflow.runnability.runnable}
          <Callout tone="warning" title="This workflow cannot run yet" live="polite">{workflow.runnability.message}.</Callout>
        {/if}
      </div>

      <fieldset class="wfr-setup-group">
        <legend class="wfr-legend">Run against</legend>
        <div class="wfr-target-grid">
          {#each TARGETS as t (t.value)}
            <Choice kind="radio" variant="card" name="run-target" value={t.value} bind:group={targetValue}>
              {#snippet label()}
                {t.label}
                {#if t.proposed && setup.demo !== false}<Chip size="sm" variant="dashed" tone="warning" class="wfr-proposed">Proposed · beyond POC</Chip>{/if}
              {/snippet}
              {#snippet description()}{t.description}{/snippet}
            </Choice>
          {/each}
        </div>
        {#if target === 'mixed' && setup.demo === false}
          <p class="wfr-muted">
            Each source goes to its mocks or to its implementation: a workflow spanning several APIs hits one real service while Microcks mocks the others. The secret, if any, is sent to the implementations only.
          </p>
        {:else if target === 'mixed'}
          <p class="wfr-muted">
            Proposal beyond the POC: the POC sends every source to its mocks or every source to its implementation. Mixed lets a workflow spanning several APIs hit one real service while
            Microcks mocks the others, e.g. <span class="wfr-mono">compare-prices</span> with apiPastry on staging and apiPastryV1 on its mocks.
          </p>
        {/if}

        {#if target !== 'mocks'}
          <div class="wfr-source-box">
            {#each sources as name (name)}
              {@const svc = setup.services[name]}
              {@const showUrl = target === 'implementations' || effectivePer(name) === 'implementation'}
              <div class="wfr-source">
                <div class="wfr-source-head">
                  <span class="wfr-mono wfr-strong">{name}</span>
                  {#if svc}
                    <a href={`/services/${svc.id}`} class="wfr-badge-link"><ApiBadge protocol={svc.protocol} name={svc.name} version={svc.version} /></a>
                  {:else}
                    <span class="wfr-note">not linked</span>
                  {/if}
                </div>
                {#if target === 'mixed'}
                  <div role="radiogroup" aria-label={`Where ${name} calls go`} class="wfr-mix">
                    <Choice kind="radio" variant="compact" name={`mix-${name}`} value="mock" bind:group={perSource[name]}>
                      {#snippet label()}Its mocks{/snippet}
                    </Choice>
                    <Choice kind="radio" variant="compact" name={`mix-${name}`} value="implementation" bind:group={perSource[name]}>
                      {#snippet label()}Its implementation{/snippet}
                    </Choice>
                  </div>
                {/if}
                {#if showUrl}
                  <Field
                    error={errorOf(`baseUrl.${name}`)}
                    class="wfr-field wfr-w-520"
                    required
                    help={`Absolute http(s) URL; the operation paths of ${svc ? `${svc.name} ${svc.version}` : name} are appended.`}
                  >
                    {#snippet label()}Base URL of the implementation <span class="wfr-label-type">required</span>{/snippet}
                    {#snippet children(c)}
                      <Input {...c} class="wfr-mono-control wfr-url-control" type="url" spellcheck={false} autocomplete="off" placeholder="http://localhost:8080" bind:value={baseUrls[name]} />
                    {/snippet}
                  </Field>
                {:else}
                  <span class="wfr-note">Mocks of {svc ? `${svc.name} ${svc.version}` : name}</span>
                {/if}
              </div>
            {/each}
            {#if unused.length > 0}
              <span class="wfr-note">{workflow.workflowId} calls no other source: {unused.join(' and ')} {unused.length > 1 ? 'are' : 'is'} not used.</span>
            {/if}
            {#if usesImplementation}
              <div class="wfr-source-secret">
                <Field label="Secret" help={target === 'mixed' ? 'Every call to an implementation carries the authorization this secret defines, as in contract tests; the calls to the mocks never do.' : 'Every call carries the authorization this secret defines, as in contract tests.'} class="wfr-field wfr-w-320">
                  {#snippet children(c)}
                    <Select {...c} class="wfr-mono-control" bind:value={secret}>
                      <option value="">No secret</option>
                      {#each setup.secrets as s (s.id)}<option value={s.name}>{s.name}</option>{/each}
                    </Select>
                  {/snippet}
                </Field>
              </div>
            {/if}
          </div>
        {/if}
      </fieldset>

      <fieldset class="wfr-setup-group">
        <legend class="wfr-legend">Inputs <span class="wfr-legend-note">From the workflow <span class="wfr-mono">inputs</span> schema</span></legend>
        <Field
          label="Example"
          class="wfr-field wfr-w-420"
          help={`Recorded in ${document.file.replace(/\.arazzo\.yaml$/, '.arazzo-sources.yaml')}. Picking one fills the inputs; the run is then judged on it.`}
        >
          {#snippet children(c)}
            <Select {...c} value={example} onchange={(e) => pickExample(e.currentTarget.value)}>
              <option value="">None, I enter the inputs</option>
              {#each setup.examples as e (e.id)}<option value={e.id}>{e.id}</option>{/each}
            </Select>
          {/snippet}
        </Field>
        {#if workflow.inputs.length > 0}
          <div class="wfr-inputs">
            {#each workflow.inputs as input (input.name)}
              <Field class="wfr-field wfr-input-field" error={errorOf(`input.${input.name}`)} help={helpOf(input.name)}>
                {#snippet label()}<span class="wfr-mono wfr-strong">{input.name}</span> <span class="wfr-label-type">{input.name === 'size' ? 'string · S | M | L' : input.type}</span>{/snippet}
                {#snippet children(c)}
                  {#if input.name === 'size'}
                    <Select {...c} class="wfr-mono-control" value={inputs[input.name] ?? 'M'} onchange={(e) => setInput(input.name, e.currentTarget.value)}>
                      {#each ['S', 'M', 'L'] as s (s)}<option>{s}</option>{/each}
                    </Select>
                  {:else}
                    <Input
                      {...c}
                      class="wfr-mono-control"
                      inputmode={input.type === 'number' ? 'decimal' : undefined}
                      spellcheck={false}
                      autocomplete="off"
                      value={inputs[input.name] ?? ''}
                      oninput={(e) => setInput(input.name, e.currentTarget.value)}
                    />
                  {/if}
                {/snippet}
              </Field>
            {/each}
          </div>
        {:else}
          <p class="wfr-muted">This workflow has no input.</p>
        {/if}
        {#if chosen}
          <div class="wfr-expected">
            <h3 class="wfr-card-subtitle">Expected by <span class="wfr-mono">{chosen.id}</span></h3>
            <Facts
              layout="inline"
              class="wfr-expected-facts"
              items={[
                ...(Object.keys(chosen.outputs).length > 0 ? [{ term: 'Outputs', mono: true, value: Object.entries(chosen.outputs).map(([k, v]) => `${k} = ${String(v)}`).join(' · ') }] : []),
                { term: 'Steps', mono: true, value: chosen.steps.map((st) => `${st.stepId} ${st.status}`).join(' ') },
                { term: 'End', value: chosen.stopsAt ? `Stops at ${chosen.stopsAt}` : 'Runs to its end' }
              ]}
            />
          </div>
        {:else if detached && Object.keys(inputs).length > 0}
          <p class="wfr-note" role="status">This run plays no example, so its expected outputs will not be checked.</p>
        {/if}
      </fieldset>

      <fieldset class="wfr-setup-group">
        <legend class="wfr-legend">Options</legend>
        <div class="wfr-option">
          <Choice kind="checkbox" variant="card" bind:checked={validate}>
            {#snippet label()}Validate each response against its OpenAPI schema{/snippet}
            {#snippet description()}Uses the validators of OpenAPI contract tests: a step fails on a non-conformant response, even when its criteria hold. Off by default.{/snippet}
          </Choice>
        </div>
        <Field label="Timeout per step (ms)" error={errorOf('timeoutMs')} help="How long a step waits for its response." class="wfr-field wfr-w-220">
          {#snippet children(c)}
            <Input {...c} class="wfr-mono-control" type="number" min={1} step={1} bind:value={timeoutMs} />
          {/snippet}
        </Field>
      </fieldset>

      {#if submitted && errors.length > 0}
        <div class="wfr-setup-errors">
          <div bind:this={summary} class="wf-error-summary" role="alert" tabindex="-1">
            <h2>{errors.length === 1 ? '1 problem to fix before running' : `${errors.length} problems to fix before running`}</h2>
            <ul>{#each errors as e (e.field)}<li>{e.message}</li>{/each}</ul>
          </div>
        </div>
      {/if}
      {#if failure}
        <div class="wfr-setup-errors"><Callout tone="danger" title="The run could not start" live="assertive">{failure}</Callout></div>
      {/if}

      <div class="wfr-setup-foot">
        <span class="wfr-setup-hint">{launchHint}</span>
        <div class="wfr-setup-buttons">
          <Button href={workflowTo}>Cancel</Button>
          <Button type="submit" variant="primary" icon="play" loading={busy} aria-disabled={!workflow.runnability.runnable || undefined}>Run</Button>
        </div>
      </div>
    </form>
  </div>

  <aside aria-label="Run side panels" class="wfr-aside">
    <section aria-labelledby="wfr-plan-h" class="wfr-card wfr-plan">
      <div class="wfr-plan-head">
        <h2 id="wfr-plan-h" class="wfr-card-title">What runs</h2>
        <a href={workflowTo} class="wfr-link wfr-small-13">View in the loom</a>
      </div>
      <ol class="stepper" data-variant="list" data-marker="circle">
        {#each workflow.steps as s, i (s.stepId)}
          {@const actions = [
            ...s.onSuccess.map((a) => (a.type === 'end' ? `onSuccess end${a.criteria?.length ? ` when ${a.criteria.map((c) => c.condition).join(' and ')}` : ''}` : '')),
            ...s.onFailure.map((a) => (a.type === 'retry' ? `onFailure retry · retryAfter ${a.retryAfter} · retryLimit ${a.retryLimit}` : 'onFailure end'))
          ].filter(Boolean)}
          <li>
            <span class="stepper-n" aria-hidden="true">{i + 1}</span>
            <span>
              <span class="wfr-plan-id"><span class="sr-only">Step {i + 1}: </span>{s.stepId}</span>
              <span class="wfr-plan-op">{s.operationId} · {s.method} {s.path}</span>
              {#each actions as a (a)}<span class="wfr-plan-action">{a}</span>{/each}
            </span>
          </li>
        {/each}
      </ol>
      <p class="wfr-note">
        A step passes when its success criteria hold and, with schema validation on, its response conforms to its OpenAPI schema. A failed step applies its first onFailure action whose
        criteria hold; without one, the run ends there and later steps are not run.
      </p>
    </section>
    <RunApiPanel
      hint="Launches the same run as the form above."
      documentApiId={document.apiId}
      {documentLabel}
      workflowId={workflow.workflowId}
      mockUrl={workflow.mockUrl}
      mixed={target === 'mixed'}
      body={apiBody}
      proposed={setup.demo !== false}
    />
  </aside>
</div>
