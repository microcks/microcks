<script lang="ts">
  import { Button, Callout, Choice, DataTable, Field, Icon, Input, MethodTag, Select, type DataTableColumn } from '@microcks/deep-ink';
  import { formatRelative, formatShortClock } from '#lib/format.js';
  import { runPath } from '#lib/workflows/paths.js';
  import { STATUS_ICON } from '#lib/workflows/status.js';
  import type { ArazzoDocument, Session, SessionCall, Workflow, WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    workflow: Workflow;
    document: ArazzoDocument;
    sessions: Session[];
    runs: WorkflowRun[];
  }

  type Tone = 'pass' | 'fail' | 'off' | 'pending';

  // PLACEHOLDER: examples of the mocked API that can answer a step under failure injection (API Pastry 2.0).
  const FAILURE_EXAMPLES: Record<string, { value: string; code: number }[]> = {
    GetPastries: [{ value: 'Size S', code: 200 }, { value: 'Size M', code: 200 }, { value: 'Size L', code: 200 }],
    GetPastryByName: [
      { value: 'Millefeuille', code: 200 },
      { value: 'Eclair Cafe', code: 200 },
      { value: 'Eclair Chocolat', code: 200 },
      { value: 'Tartelette Framboise', code: 200 }
    ],
    PatchPastry: [{ value: 'Eclair Cafe · out of stock', code: 409 }, { value: 'Eclair Chocolat · price', code: 200 }]
  };
  const MICROCKS = 'http://microcks.local:8080';
  const SESSION_TTL_S = 3600;
  const isNumber = (t: string) => t === 'number' || t === 'integer';

  let { workflow, document, sessions, runs }: Props = $props();

  const failDefault = $derived(workflow.steps.find((s) => s.onFailure.length > 0) ?? workflow.steps[0]);
  let selectedId = $state<string | undefined>();
  let values = $state<Record<string, string>>({});
  let fail = $state(true);
  let failStep = $state('');
  let failExample = $state('');
  let copied = $state<string>();

  $effect.pre(() => {
    selectedId ??= sessions[0]?.id;
    failStep ||= failDefault?.stepId ?? '';
    for (const i of workflow.inputs) values[i.name] ??= String(i.example ?? (i.name === 'size' ? 'M' : ''));
  });

  const step = $derived(workflow.steps.find((s) => s.stepId === failStep));
  const exampleList = $derived(FAILURE_EXAMPLES[step?.operationId ?? ''] ?? []);
  const example = $derived(exampleList.some((e) => e.value === failExample) ? failExample : (exampleList[0]?.value ?? ''));
  const errors = $derived(
    Object.fromEntries(
      workflow.inputs
        .filter((i) => isNumber(i.type) && !/^-?\d+(\.\d+)?$/.test((values[i.name] ?? '').trim()))
        .map((i) => [i.name, `${i.name} must be a number, such as 3.9.`])
    ) as Record<string, string>
  );
  const valid = $derived(Object.keys(errors).length === 0);
  const inputs = $derived(
    Object.fromEntries(
      workflow.inputs.flatMap((i) => {
        const v = (values[i.name] ?? '').trim();
        if (v === '') return [];
        return [[i.name, isNumber(i.type) && /^-?\d+(\.\d+)?$/.test(v) ? Number(v) : v]];
      })
    )
  );
  const body = $derived({
    documentId: `${document.title}:${document.version}`,
    workflowId: workflow.workflowId,
    inputs,
    ...(fail && example ? { failures: { [failStep]: example } } : {})
  });
  const curl = $derived(`curl -X POST '${MICROCKS}/api/arazzo/sessions' \\\n  -H 'Content-Type: application/json' \\\n  -d '${JSON.stringify(body)}'`);
  const selected = $derived(sessions.find((s) => s.id === selectedId) ?? sessions[0]);
  const sessionId = $derived(sessions[0]?.id ?? 's-7f3a2c91');
  const services = $derived([...new Set(workflow.steps.map((s) => s.sourceName))]);
  const serviceName = $derived(services.length === 1 && services[0] === 'apiPastry' ? 'API Pastry 2.0' : 'its API');
  const selectedRun = $derived(
    selected?.verification
      ? runs.find((r) => r.origin.kind === 'session' && r.origin.sessionId === selected.id && r.number === selected.verification?.runNumber)
      : undefined
  );
  const columns = $derived<DataTableColumn<SessionCall>[]>([
    { key: 'n', header: 'Call', mono: true },
    { key: 'req', header: 'Request' },
    { key: 'step', header: 'Step' },
    { key: 'contract', header: `Contract of ${serviceName}` },
    { key: 'data', header: 'Data passing' }
  ]);
  const API_STEPS = $derived([
    { title: 'Open a session', method: 'POST', path: '/api/arazzo/sessions', text: 'Your test sends the workflow, its inputs and the steps to fail. Microcks answers 201 with the session id.' },
    {
      title: 'The application calls the mocks',
      method: undefined,
      path: `x-microcks-session: ${sessionId}`,
      text: `It calls the ordinary mocks of ${serviceName} with this header, or microcks-session in its W3C baggage. Later steps are answered with the outputs of earlier ones and the values the app sent.`
    },
    {
      title: 'Verify the calls',
      method: 'POST',
      path: '/api/arazzo/sessions/{id}/verification',
      text: 'Checks each call against the steps: order (relaxed by dependsOn), data passing, inputs, the contract of its API, and unexpected calls. Creates a run with the origin Session.'
    },
    { title: 'Close the session', method: 'DELETE', path: '/api/arazzo/sessions/{id}', text: '204 No Content. The calls stay with the verification run.' }
  ]);

  const verdictOf = (s: Session): { tone: Tone; text: string } =>
    s.verification
      ? { tone: s.verification.result === 'SUCCESS' ? 'pass' : 'fail', text: `Verification #${s.verification.runNumber} ${s.verification.result === 'SUCCESS' ? 'passed' : 'failed'}` }
      : { tone: 'pending', text: 'Not verified yet' };

  function metaOf(s: Session) {
    if (s.state === 'open') {
      const left = Math.max(0, Math.round((new Date(s.openedAt).getTime() + SESSION_TTL_S * 1000 - Date.now()) / 60_000));
      return `Opened ${formatRelative(s.openedAt)} · closes by itself in ${left} min`;
    }
    const secs = s.closedAt ? Math.round((new Date(s.closedAt).getTime() - new Date(s.openedAt).getTime()) / 1000) : undefined;
    return `Opened ${formatRelative(s.openedAt)} · ${formatShortClock(s.openedAt)}${secs !== undefined ? ` · closed by the test after ${secs} s` : ''}`;
  }

  async function copy(id: string, text: string) {
    if (!valid) return;
    try {
      await navigator.clipboard.writeText(text);
      copied = id;
    } catch {
      /* clipboard blocked */
    }
  }
</script>

{#snippet mark(tone: Tone, text: string, size = 12)}
  <span class="wf-se-mark" data-tone={tone}><Icon name={STATUS_ICON[tone]} {size} />{text}</span>
{/snippet}

<div class="wf-se-tab">
  <section aria-labelledby="wf-sess-title" class="wf-se-panel">
    <div class="wf-se-intro">
      <h2 id="wf-sess-title">Verification sessions</h2>
      <p>
        A verification session checks that your application calls the APIs the way <span class="wf-se-mono">{workflow.workflowId}</span> says, while it runs
        against the ordinary Microcks mocks. Your test opens a session, the application makes its calls carrying the session id, then the test asks Microcks to
        verify the calls it received.
      </p>
    </div>
    <ol class="wf-se-api">
      {#each API_STEPS as a, i (a.title)}
        <li>
          <span class="wf-se-api-head"><span class="wf-se-n" aria-hidden="true">{i + 1}</span><span class="wf-se-api-title"><span class="sr-only">{i + 1}. </span>{a.title}</span></span>
          <span class="wf-se-api-path">{#if a.method}<MethodTag size="xs" method={a.method} />{/if}<span>{a.path}</span></span>
          <span class="wf-se-api-text">{a.text}</span>
        </li>
      {/each}
    </ol>
    <span class="wf-se-note">A session closes by itself after {SESSION_TTL_S} s and records at most 1000 calls.</span>
  </section>

  {#if workflow.runnability.runnable}
    <div class="wf-se-pair">
      <section aria-labelledby="wf-builder-title" class="wf-se-panel" data-size="half">
        <div class="wf-se-intro">
          <h2 id="wf-builder-title">Session builder</h2>
          <span class="wf-se-sub">Build the request your test sends to open a session.</span>
        </div>
        <fieldset class="wf-se-inputs">
          <legend>Inputs</legend>
          {#each workflow.inputs as input (input.name)}
            <div class="wf-se-input">
              <Field error={errors[input.name]}>
                {#snippet label()}<span class="wf-se-mono wf-se-name">{input.name}</span> · {input.name === 'size' ? 'S, M or L' : input.type}{/snippet}
                {#snippet children(c)}
                  {#if input.name === 'size'}
                    <Select {...c} bind:value={values[input.name]}>{#each ['S', 'M', 'L'] as s (s)}<option>{s}</option>{/each}</Select>
                  {:else}
                    <Input {...c} inputmode={input.type === 'number' ? 'decimal' : undefined} bind:value={values[input.name]} />
                  {/if}
                {/snippet}
              </Field>
            </div>
          {/each}
          {#if workflow.inputs.length === 0}<p class="wf-se-sub">This workflow has no input.</p>{/if}
        </fieldset>
        <div class="wf-se-fail">
          <Choice bind:checked={fail}>{#snippet label()}Make a step fail on the mocks to test your compensation{/snippet}</Choice>
          {#if fail}
            <div class="wf-se-fail-fields">
              <div class="wf-se-input">
                <Field label="Step">
                  {#snippet children(c)}
                    <Select {...c} bind:value={failStep}>{#each workflow.steps as s, i (s.stepId)}<option value={s.stepId}>{i + 1} · {s.stepId}</option>{/each}</Select>
                  {/snippet}
                </Field>
              </div>
              <div class="wf-se-input" data-size="wide">
                <Field>
                  {#snippet label()}Answered with the example of <span class="wf-se-mono wf-se-name">{step?.operationId}</span>{/snippet}
                  {#snippet children(c)}
                    <Select {...c} value={example} onchange={(e) => (failExample = e.currentTarget.value)} disabled={exampleList.length === 0}>
                      {#each exampleList as e (e.value)}<option value={e.value}>{e.value} ({e.code})</option>{/each}
                    </Select>
                  {/snippet}
                </Field>
              </div>
            </div>
            <span class="wf-se-note">
              In the session, the mocks answer <span class="wf-se-mono">{failStep}</span> with this existing example of {serviceName}, so your application meets
              the failure and runs its compensation.
            </span>
          {/if}
        </div>
      </section>

      <section aria-labelledby="wf-req-title" class="wf-se-panel" data-size="half" data-gap="tight">
        <div class="wf-se-req-head">
          <h2 id="wf-req-title"><MethodTag size="xs" method="POST" /><span class="wf-se-mono wf-se-req-path">/api/arazzo/sessions</span></h2>
          <div class="wf-se-req-actions">
            <Button size="sm" disabled={!valid} onclick={() => copy('body', JSON.stringify(body, null, 2))}>{copied === 'body' ? 'Copied' : 'Copy body'}</Button>
            <Button size="sm" disabled={!valid} onclick={() => copy('curl', curl)}>{copied === 'curl' ? 'Copied' : 'Copy curl'}</Button>
          </div>
        </div>
        <pre aria-label="Request body" aria-live="polite" class="wf-se-pre">{JSON.stringify(body, null, 2)}</pre>
        <div class="wf-se-answer">
          <span class="wf-se-answer-line"><span class="wf-se-code">201</span><span class="wf-se-mono wf-se-sub">Location: /api/arazzo/sessions/{sessionId}</span></span>
          <span class="wf-se-note">400 with the reason in plain text when Microcks refuses the session, for an unknown step or example.</span>
        </div>
      </section>
    </div>
  {:else}
    <Callout tone="warning" title="Sessions are not available">{workflow.runnability.message}.</Callout>
  {/if}

  <section aria-labelledby="wf-recent-title" class="wf-se-panel wf-se-recent">
    <div class="wf-se-list">
      <h2 id="wf-recent-title">Recent sessions</h2>
      {#if sessions.length === 0}
        <p class="wf-se-sub">No session yet for this workflow.</p>
      {:else}
        <ul>
          {#each sessions as s (s.id)}
            {@const v = verdictOf(s)}
            <li>
              <button type="button" class="wf-se-item" aria-pressed={s.id === selected?.id} onclick={() => (selectedId = s.id)}>
                <span class="wf-se-item-head">
                  <span class="wf-se-mono wf-se-strong">{s.id}</span>
                  <span class="wf-se-state" data-state={s.state}><span aria-hidden="true" class="wf-se-dot"></span>{s.state}</span>
                </span>
                <span class="wf-se-sub">{s.app}</span>
                <span class="wf-se-item-foot">
                  <span class="wf-se-muted">{s.calls.length} {s.calls.length === 1 ? 'call' : 'calls'} received</span>
                  {@render mark(v.tone, v.text)}
                </span>
              </button>
            </li>
          {/each}
        </ul>
      {/if}
    </div>

    {#if selected}
      <div class="wf-se-detail">
        <div class="wf-se-detail-head">
          <h3>Session <span class="wf-se-mono">{selected.id}</span></h3>
          <span class="wf-se-muted">{metaOf(selected)}</span>
        </div>
        <dl class="wf-se-facts">
          <div><dt>Inputs</dt><dd class="wf-se-mono">{Object.entries(selected.inputs).map(([k, v]) => `${k}=${String(v)}`).join(' · ') || 'none'}</dd></div>
          <div>
            <dt>Steps set to fail</dt>
            <dd class="wf-se-mono">{selected.failureInjection ? `${selected.failureInjection.stepId} → ${selected.failureInjection.example}` : 'none'}</dd>
          </div>
          <div>
            <dt>Verification</dt>
            <dd>
              {#if selected.verification}
                {@render mark(selected.verification.result === 'SUCCESS' ? 'pass' : 'fail', `${selected.verification.result === 'SUCCESS' ? 'Passed' : 'Failed'} · #${selected.verification.runNumber}`, 13)}
              {:else}
                {@render mark('pending', 'Not verified yet', 13)}
              {/if}
            </dd>
          </div>
        </dl>
        {#if selected.verification?.result === 'FAILURE'}
          <div class="wf-se-alert">
            <span class="wf-se-alert-title"><Icon name="x-circle" size={15} />The calls of the session do not follow the workflow</span>
            <ul>
              {#each selected.verification.problems as p (p.lead + p.text)}<li>{p.lead} <span class="wf-se-problem">{p.text}</span></li>{/each}
            </ul>
          </div>
        {/if}
        <div class="wf-se-calls">
          <span aria-hidden="true" class="wf-se-caption">Received calls, in the order of receipt, each mapped to its step</span>
          <DataTable
            captionHidden
            headerCase="sentence"
            density="compact"
            caption="Received calls, in the order of receipt, each mapped to its step"
            {columns}
            rows={selected.calls}
            rowKey={(c) => String(c.n)}
            emptyText="No call received yet."
          >
            {#snippet cell(c, col)}
              {#if col.key === 'n'}
                <span class="wf-se-strong">#{c.n}</span>
              {:else if col.key === 'req'}
                <span class="wf-se-stack"><span class="wf-se-req"><MethodTag size="xs" method={c.method} />{c.path}</span><span class="wf-se-status">→ {c.httpStatus}</span></span>
              {:else if col.key === 'step'}
                {#if c.stepId === 'unexpected'}
                  <span class="wf-se-step" data-tone="fail"><Icon name="x-circle" size={12} />unexpected</span>
                {:else}
                  <span class="wf-se-step">{c.stepId}</span>
                {/if}
              {:else if col.key === 'contract'}
                {@render mark(c.contract.state, c.contract.text)}
              {:else}
                {@render mark(c.dataPassing.state, c.dataPassing.text)}
              {/if}
            {/snippet}
          </DataTable>
        </div>
        <div class="wf-se-foot">
          {#if selectedRun}<Button size="sm" href={runPath(selectedRun.id)}>Open verification #{selectedRun.number}</Button>{/if}
          {#if selected.state === 'open'}<span class="wf-se-mono wf-se-sub">POST /api/arazzo/sessions/{selected.id}/verification</span>{/if}
          <span class="wf-se-muted">
            {selected.verification
              ? `Verified ${formatRelative(selectedRun?.startedAt ?? selected.closedAt ?? selected.openedAt)} against the Microcks mocks.`
              : `Not verified yet · ${selected.calls.length} of 1000 calls recorded.`}
          </span>
        </div>
      </div>
    {/if}
  </section>
</div>
