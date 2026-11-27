<script lang="ts">
  import { Chip, FactRow, Facts } from '@microcks/deep-ink';
  import { judgeExample } from '#lib/workflows/describe.js';
  import { workflowPath } from '#lib/workflows/paths.js';
  import type { WorkflowExample, WorkflowRun } from '#lib/workflows/types.js';
  import { J, implementationsText, mockSources, originText } from '#lib/runs-workflow.js';

  interface Props {
    run: WorkflowRun;
    documentLabel?: string;
    example?: WorkflowExample;
  }

  let { run, documentLabel, example }: Props = $props();

  const onMocks = $derived(run.target.kind === 'mocks');
  const firstBaggage = $derived(run.steps.find((s) => s.status !== 'not-run')?.baggage);
  const outputs = $derived(Object.entries(run.outputs));
  const verdict = $derived(
    example ? (run.status === 'RUNNING' ? 'Judged once the run ends' : judgeExample(run, example).matches ? 'Matches' : 'Does not match') : undefined
  );
  const inputsText = $derived(
    Object.entries(run.options.inputs)
      .map(([k, v]) => `${k} = ${String(v)}`)
      .join(' · ') || 'none'
  );
</script>

<section aria-labelledby="wfr-props-h" class="wfr-card wfr-props">
  <h2 id="wfr-props-h" class="wfr-card-title">Properties</h2>
  <Facts layout="inline">
    <FactRow term="Workflow">
      {#snippet children()}
        <a href={workflowPath(run.documentId, run.workflowId)} class="wfr-link-mono">{run.workflowId}</a> in {documentLabel ?? run.documentId}
      {/snippet}
    </FactRow>
    <FactRow term="Target">
      {#snippet children()}
        <span class="wfr-target">
          <strong>{run.target.kind === 'mocks' ? 'Microcks mocks' : run.target.kind === 'implementations' ? 'Implementations' : 'Mixed'}</strong>
          {#if run.target.kind === 'implementations'}<span class="wfr-target-url">{implementationsText(run.target.baseUrls)}</span>{/if}
          {#if run.target.kind === 'mocks'}<span class="wfr-target-url">{mockSources(run)}</span>{/if}
          {#if run.target.kind === 'mixed'}<Chip size="sm" variant="dashed" tone="warning" class="wfr-proposed">Proposed · beyond POC</Chip>{/if}
        </span>
      {/snippet}
    </FactRow>
    <FactRow term="Secret">{#snippet children()}<span class="wfr-mono-sm">{!onMocks && run.options.secret ? run.options.secret : 'None'}</span>{/snippet}</FactRow>
    <FactRow term="Inputs">{#snippet children()}<span class="wfr-mono-sm">{inputsText}</span>{/snippet}</FactRow>
    <FactRow term="Schema validation">{#snippet children()}{run.options.validateSchemas ? 'On' : 'Off'}{/snippet}</FactRow>
    <FactRow term="Timeout per step">{#snippet children()}<span class="wfr-mono-sm">{run.options.timeoutMs} ms</span>{/snippet}</FactRow>
    {#if onMocks && firstBaggage}
      <FactRow term="Baggage">
        {#snippet children()}
          <span class="wfr-baggage-block">
            <code class="wfr-baggage">{firstBaggage}</code>
            <span class="wfr-note">Carried by each call to the mocks: Live traces show which run, step and attempt a mock call comes from.</span>
            <a href={`/traces?run=${run.id}`} class="wfr-link">See in Live traces</a>
          </span>
        {/snippet}
      </FactRow>
    {/if}
    {#if outputs.length > 0}
      <FactRow term="Outputs">{#snippet children()}<span class="wfr-mono-sm">{outputs.map(([k, v]) => `${k} = ${J(v)}`).join(' · ')}</span>{/snippet}</FactRow>
    {/if}
    {#if run.options.example}
      <FactRow term="Example">
        {#snippet children()}<span class="wfr-mono-sm">{run.options.example}</span>{verdict ? ` · ${verdict}` : ''}{/snippet}
      </FactRow>
    {/if}
    <FactRow term="Origin">{#snippet children()}{originText(run)}{/snippet}</FactRow>
  </Facts>
</section>
