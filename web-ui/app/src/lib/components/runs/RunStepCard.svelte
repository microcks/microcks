<script module lang="ts">
  import type { Phase } from '#lib/runs-workflow.js';
  import type { StepResult } from '#lib/workflows/types.js';

  export type Look = 'pass' | 'fail' | 'run' | 'pend' | 'notrun';

  export function lookOf(step: StepResult, phase: Phase): Look {
    if (phase === 'running') return 'run';
    if (phase === 'waiting') return 'pend';
    return step.status === 'passed' ? 'pass' : step.status === 'failed' ? 'fail' : 'notrun';
  }
</script>

<script lang="ts">
  import { isAvailable, unavailableReason } from '#lib/workflows/availability.js';
  import { Chip, CodeBlock, Icon, MethodTag, StatusBadge } from '@microcks/deep-ink';
  import RunVerdict from './RunVerdict.svelte';
  import { J, badFieldText } from '#lib/runs-workflow.js';
  import { criteriaState } from '#lib/workflows/criteria-state.js';
  import { formatDuration } from '#lib/workflows/time.js';
  import type { Step, WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    index: number;
    step: StepResult;
    def?: Step;
    look: Look;
    run: WorkflowRun;
    offset: number;
    stop: string;
  }

  let { index, step, def, look, run, offset, stop }: Props = $props();

  const bodyId = $props.id();
  const WORD: Record<Look, string> = { pass: 'Passed', fail: 'Failed', run: 'Running', pend: 'Queued', notrun: 'Not run' };
  const ms = (n: number) => `${n} ms`;

  const finished = $derived(look === 'pass' || look === 'fail');
  let collapsed = $state(false);
  const open = $derived(!collapsed);
  const running = $derived(look === 'run');
  const showBody = $derived((finished && open) || running);
  const lastAttempt = $derived(step.attempts[step.attempts.length - 1]);
  const retry = $derived(def?.onFailure.find((a) => a.type === 'retry'));
  const end = $derived(def?.onSuccess.find((a) => a.type === 'end'));
  const endsOnSuccess = $derived(step.status === 'passed' && step.flow.kind === 'end');

  const tags = $derived.by(() => {
    const out: { text: string; tone: 'neutral' | 'warning' | 'success' }[] = [];
    if (end) {
      const cond = end.criteria?.[0]?.condition.replace('$response.body#/', '') ?? '';
      out.push({ text: `onSuccess end${cond ? ` · ${cond}` : ''}`, tone: endsOnSuccess ? 'success' : 'neutral' });
    }
    if (retry && retry.type === 'retry') {
      const retried = step.attempts.length > 1;
      out.push({
        text: `${retried ? 'retried' : 'onFailure retry'} ×${retried ? step.attempts.length - 1 : retry.retryLimit} · ${retry.retryAfter} s`,
        tone: retried ? 'warning' : 'neutral'
      });
    }
    return out;
  });
  const msLabel = $derived(
    finished && lastAttempt
      ? step.attempts.length > 1
        ? `${step.attempts.length} attempts · ${ms(lastAttempt.ms)}`
        : `${lastAttempt.httpStatus} · ${ms(lastAttempt.ms)}`
      : running
        ? '…'
        : '—'
  );
  const runLabel = $derived(
    run.current && run.current.attempt > 1
      ? `Retrying · attempt ${run.current.attempt} of ${(retry && retry.type === 'retry' ? retry.retryLimit : 0) + 1}`
      : WORD.run
  );

  const onMocks = $derived(run.target.kind === 'mocks');
  const criteriaOk = $derived(step.criteria.every((c) => c.held));
  const criteriaNotEvaluated = $derived(criteriaState(step) === 'not-evaluated');
  const schema = $derived(step.schema);
  const coherence = $derived(run.coherence.find((c) => c.stepId === step.stepId));
  const listName = $derived(coherence ? String(run.steps[0]?.outputs['firstName'] ?? '') : '');
  const lines = $derived(step.response?.lines ?? []);
</script>

{#snippet head()}
  <span class="wfr-step-id-group">
    <span class="wfr-step-id">{step.stepId}</span>
    {#if def}
      <span class="wfr-step-op"><MethodTag method={def.method} size="sm" />{def.path}</span>
      <span class="wfr-step-opid">{def.operationId}</span>
    {/if}
    {#each tags as t (t.text)}<Chip size="sm" variant="outline" tone={t.tone}>{t.text}</Chip>{/each}
  </span>
  {#if finished || running}<span class="wfr-step-at">at +{formatDuration(offset)}</span>{/if}
  <span class="wfr-step-ms">{msLabel}</span>
  {#if running}
    <StatusBadge shape="bare" status="running" class="wfr-step-run" label={runLabel} />
  {:else}
    <span class="wfr-step-status" data-look={look}>
      <Icon
        name={look === 'pass' ? 'check' : look === 'fail' ? 'cross' : 'circle'}
        size={14}
        stroke-width={look === 'pass' || look === 'fail' ? 2.6 : 2}
        stroke-dasharray={look === 'notrun' ? '3 3' : undefined}
      />
      {WORD[look]}
    </span>
  {/if}
{/snippet}

<article class="wfr-step" data-look={look} aria-label={`Step ${index + 1} ${step.stepId}: ${WORD[look]}`}>
  <h3 class="wfr-step-h">
    {#if finished}
      <button type="button" class="wfr-step-head" aria-expanded={open} aria-controls={bodyId} onclick={() => (collapsed = !collapsed)}>
        {@render head()}
        <Icon name="chevron" size={16} class="wfr-step-chevron" />
      </button>
    {:else}
      <span class="wfr-step-head">{@render head()}<span class="wfr-step-chevron-slot"></span></span>
    {/if}
  </h3>
  {#if look === 'notrun' || look === 'pend'}
    <p class="wfr-step-pend">{look === 'notrun' ? `Not run: ${stop}.` : 'Queued.'}</p>
  {/if}
  {#if showBody}
    <div class="wfr-step-body" id={bodyId}>
      {#if !running && schema.state === 'fail'}
        <p class="wfr-step-failure"><Icon name="x-circle" size={14} /><span class="wfr-mono">Step failed: {schema.message}</span></p>
      {/if}
      {#if def}<p class="wfr-step-desc">{def.description}</p>{/if}

      {#if step.request}
        <div class="wfr-block">
          <h4 class="wfr-h4">Request</h4>
          <div class="wfr-request-line">
            <span class="wfr-request-method">{step.request.method}</span>
            <span class="wfr-request-url">{step.request.url}</span>
          </div>
          {#if Object.keys(step.request.params).length > 0}
            <div class="wfr-grid-wrap">
              <table class="wfr-grid" data-cols="params">
                <caption class="sr-only">Parameters of {step.stepId}</caption>
                <thead><tr><th scope="col">Parameter</th><th scope="col">Value</th><th scope="col">From</th></tr></thead>
                <tbody>
                  {#each Object.entries(step.request.params) as [name, value] (name)}
                    {@const p = def?.parameters.find((x) => x.name === name)}
                    <tr>
                      <th scope="row" class="wfr-mono"><strong>{name}</strong> {#if p}<span class="wfr-faint">{p.in}</span>{/if}</th>
                      <td class="wfr-mono wfr-strong">{value}</td>
                      <td class="wfr-mono wfr-secondary">{p?.value ?? ''}</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
          {#if step.request.body && def?.requestBody}
            <dl class="wfr-body-box">
              <div><dt>requestBody · application/json</dt><dd class="wfr-secondary">{def.requestBody}</dd></div>
              <div><dt>sent</dt><dd class="wfr-strong">{step.request.body}</dd></div>
            </dl>
          {/if}
          {#if onMocks && step.baggage}
            <p class="wfr-baggage-line">
              <span class="wfr-faint">Baggage</span>
              <code class="wfr-baggage" title="Marks this call in the Live Traces of the API">{step.baggage}</code>
              <a href={`/traces?run=${run.id}`} class="wfr-link">See in Live traces</a>
            </p>
          {/if}
        </div>
      {/if}

      {#if step.attempts.length > 1 && retry && retry.type === 'retry'}
        <div class="wfr-block">
          <h4 class="wfr-h4">Attempts · onFailure retry, retryAfter {retry.retryAfter} s, retryLimit {retry.retryLimit}</h4>
          <ol class="wfr-attempts">
            {#each step.attempts as a, k (a.n)}
              {@const isLast = k === step.attempts.length - 1}
              {@const outcome = `${criteriaNotEvaluated ? 'Success criteria not evaluated' : criteriaOk ? 'Success criteria held' : 'Success criteria did not hold'}${schema.state === 'fail' ? ` · schema validation failed: ${badFieldText(step)}` : ''}`}
              {@const next = isLast
                ? step.status === 'passed'
                  ? 'the step passes'
                  : 'retry limit reached, the step fails'
                : `onFailure retry ${k + 1} of ${retry.retryLimit} after ${retry.retryAfter} s`}
              {@const failedAttempt = !(isLast && step.status === 'passed')}
              <li>
                <span class="wfr-attempt-no">Attempt {a.n}</span>
                <span class="wfr-code-chip">{a.httpStatus}</span>
                <span class="wfr-attempt-ms">{ms(a.ms)}</span>
                <span class="wfr-attempt-note">
                  <Icon name={failedAttempt ? 'cross' : 'check'} size={12} stroke-width={2.4} />
                  <span>{outcome} → {next}</span>
                </span>
              </li>
            {/each}
          </ol>
        </div>
      {/if}

      <div class="wfr-block">
        <div class="wfr-response-head">
          <h4 class="wfr-h4">Response</h4>
          {#if !running && lastAttempt}
            <span class="wfr-code-chip" data-tone={lastAttempt.httpStatus < 300 ? 'success' : 'danger'}>{lastAttempt.httpStatus}</span>
            <span class="wfr-mono wfr-secondary">
              {step.response?.meta} · {ms(lastAttempt.ms)}{step.attempts.length > 1 ? ` · attempt ${step.attempts.length}` : ''}
            </span>
          {/if}
        </div>
        {#if running}
          <div role="status" aria-label="Waiting for response" class="wfr-waiting">
            <span class="wfr-pulse-bar" style="width:46%"></span>
            <span class="wfr-pulse-bar" style="width:72%"></span>
            <span class="wfr-pulse-bar" style="width:58%"></span>
            <span class="wfr-faint">
              {run.current && run.current.attempt > 1
                ? `Waiting for attempt ${run.current.attempt}…`
                : `Waiting for ${step.request ? `${step.request.method} ${step.request.url}` : 'the response'}…`}
            </span>
          </div>
        {:else if lines.length > 0}
          <CodeBlock
            class="wfr-response"
            tone={schema.state === 'fail' ? 'danger' : undefined}
            language="json"
            {lines}
            lineNumbers
            copy={false}
            variant="plain"
            caption={`Response of ${step.stepId}${step.attempts.length > 1 ? `, attempt ${step.attempts.length}` : ''}`}
          />
        {:else}
          <span class="wfr-faint wfr-small">Body · Empty</span>
        {/if}
      </div>

      {#if !running}
        <div class="wfr-checks">
          <div class="wfr-block">
            <div class="wfr-check-head">
              <h4 class="wfr-h4" data-size="13">Success criteria</h4>
              {#if criteriaNotEvaluated}
                <RunVerdict tone="muted" icon="dash">Not evaluated</RunVerdict>
              {:else if criteriaOk}
                <RunVerdict tone="success" icon="check">Held {step.criteria.length}/{step.criteria.length}</RunVerdict>
              {:else}
                <RunVerdict tone="danger" icon="cross">Did not hold</RunVerdict>
              {/if}
            </div>
            {#if step.criteria.length > 0}
              <div class="wfr-grid-wrap">
                <table class="wfr-grid" data-cols="criteria">
                  <caption class="sr-only">Success criteria of {step.stepId}</caption>
                  <thead><tr><th scope="col">Criterion</th><th scope="col">Evaluated</th><th scope="col">Result</th></tr></thead>
                  <tbody>
                    {#each step.criteria as c (c.condition)}
                      <tr>
                        <th scope="row" class="wfr-mono">{c.condition} {#if c.kind === 'regex'}<span class="wfr-kind">regex</span>{/if}</th>
                        <td class="wfr-mono wfr-secondary">{c.evaluation}</td>
                        <td>
                          <RunVerdict tone={c.held ? 'success' : 'danger'} icon={c.held ? 'check' : 'cross'}>{c.held ? 'Passed' : 'Failed'}</RunVerdict>
                        </td>
                      </tr>
                    {/each}
                  </tbody>
                </table>
              </div>
            {/if}
          </div>
          <div class="wfr-block" data-gap="6">
            <div class="wfr-check-head">
              <h4 class="wfr-h4" data-size="13">Schema validation</h4>
              {#if schema.state === 'pass'}
                <RunVerdict tone="success" icon="check">Passed</RunVerdict>
              {:else if schema.state === 'fail'}
                <RunVerdict tone="danger" icon="cross">Failed</RunVerdict>
              {:else}
                <RunVerdict tone="muted" icon="dash">{schema.state === 'na' ? 'Not applicable' : 'Off'}</RunVerdict>
              {/if}
            </div>
            <span class="wfr-schema-ref">{schema.ref || (schema.state === 'off' ? 'Validate each response against its OpenAPI schema · off' : '')}</span>
            <span class="wfr-schema-text" data-tone={schema.state === 'fail' ? 'text' : undefined}>{schema.message}</span>
          </div>
        </div>

        {#if coherence}
          <div role="note" class="wfr-coherence">
            <Icon name="info" size={16} />
            <div class="wfr-coherence-body">
              <p>
                <strong>The mocks do not form a coherent scenario.</strong>
                {#if listName}
                  GetPastries returns “{listName}”, but <span class="wfr-mono">{coherence.operationId}</span> has no example for
                  <span class="wfr-code-inline">name</span> = {listName}.
                {:else}
                  The mocks lack <span class="wfr-mono">{coherence.missing}</span>.
                {/if}
                Add that example to the OpenAPI document, or let the workflow send a value that has one.
              </p>
              <div class="wfr-coherence-links">
                <a href="/services" class="wfr-link" title={def ? `${def.method} ${def.path}` : undefined}>Open {coherence.operationId}</a>
                {#if isAvailable('ai-copilot', run.documentId)}
                  <a href={`/services?hint=${encodeURIComponent(coherence.copilotHint)}`} class="wfr-link">Generate the example with the AI Copilot</a>
                {:else}
                  <button type="button" class="wfr-link" disabled title={unavailableReason('ai-copilot', run.documentId)}>Generate the example with the AI Copilot</button>
                {/if}
              </div>
            </div>
          </div>
        {/if}

        <div class="wfr-block" data-gap="10">
          <div class="wfr-outputs">
            <span class="wfr-outputs-label">Outputs</span>
            {#each Object.entries(step.outputs) as [k, v] (k)}
              {@const expr = def?.outputs.find((o) => o.name === k)?.expression ?? ''}
              <span class="wfr-output-chip" title={expr}>{k} <span class="wfr-faint">=</span> <strong>{J(v)}</strong><span class="wfr-faint">from {expr}</span></span>
            {:else}
              <span class="wfr-faint wfr-small">{step.status === 'failed' ? 'None: a failed step produces no outputs.' : 'None declared.'}</span>
            {/each}
          </div>
          <div class="wfr-flow" data-kind={step.flow.kind}>
            <Icon name={step.flow.kind === 'next' ? 'arrow-right' : step.flow.kind === 'end' ? 'stop' : 'x-circle'} size={16} />
            <span class="wfr-flow-text">
              <span class="wfr-flow-title">{step.flow.title}</span>
              <span class="wfr-flow-detail">{step.flow.text}</span>
            </span>
          </div>
        </div>
      {/if}
    </div>
  {/if}
</article>
