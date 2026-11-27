<script lang="ts">
  import { ApiBadge, Icon } from '@microcks/deep-ink';
  import StepExchangePanel from './StepExchangePanel.svelte';
  import { buildContractDiff, hostOf, type DiffRow, type TestRunDto, type TestStepResult } from './model.js';

  interface Props {
    id: string;
    step: TestStepResult;
    run: TestRunDto;
  }

  let { id, step, run }: Props = $props();

  const diff = $derived(buildContractDiff(step));
  const ko = $derived(step.result === 'failed');
  const isArray = $derived(Array.isArray(step.payload));
  const n = $derived(isArray ? (step.payload as unknown[]).length : 1);
  const code = $derived(step.httpStatus ?? 200);
  const schema = $derived(step.schemaRef?.split('/').pop() ?? 'the schema');
  const propCount = $derived(step.contract?.length ?? 0);
  const newEnum = $derived(step.violations.map((v) => /not found in enum.*\("([^"]+)"\)|Enum value "([^"]+)"/.exec(v.raw ?? v.message)?.slice(1).find(Boolean)).find(Boolean));
  const label = $derived(run.details.operationLabels.find((o) => o.id === step.operationId));
  const opText = $derived(label ? `${label.method} ${label.path}` : `${step.method} ${step.path}`);
  const vCount = $derived(`${step.violations.length} ${step.violations.length === 1 ? 'violation' : 'violations'}`);
</script>

{#snippet codeCard(title: string, source: string, rows: DiffRow[], aria: string, bad: boolean)}
  <div class="tr-code-card" data-bad={bad ? '' : undefined}>
    <div class="tr-code-head"><span class="tr-code-title">{title}</span><span class="tr-code-source">{source}</span></div>
    <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
    <pre class="tr-code" tabindex="0" aria-label={aria}><code>{#each rows as r (r.n)}<span class="tr-line" data-bad={r.bad ? '' : undefined}><span class="tr-line-n" aria-hidden="true">{r.n}</span><span class="tr-line-mark">{#if r.bad}<span aria-hidden="true">✕</span><span class="sr-only">Mismatch: </span>{/if}</span><span class="tr-line-text">{r.text}</span></span>{/each}</code></pre>
  </div>
{/snippet}

<div {id} role="region" aria-label="Contract versus actual response for {step.name}" class="tr-diff">
  <div class="tr-diff-head">
    <div class="tr-diff-heading">
      <h4 class="tr-diff-title">Contract <span aria-hidden="true" class="tr-diff-swap">⇄</span> Actual response</h4>
      {#if step.schemaRef}<span class="tr-diff-ref">{isArray ? 'array of ' : ''}{step.schemaRef} · {code} application/json</span>{/if}
    </div>
    <ApiBadge label="schema" value={ko ? vCount : 'Conformant'} status={ko ? 'danger' : 'success'} />
  </div>

  {#if !step.contract}
    <StepExchangePanel {run} {step} />
  {/if}

  <div class="tr-request">
    <span><span class="tr-request-key">{'sent '}</span><span>{step.method} {run.testedEndpoint}{step.path}</span></span>
    {#if step.requestBody}<span><span class="tr-request-key">{'body '}</span><span class="tr-request-body">{step.requestBody}</span></span>{/if}
    <span><span class="tr-request-key">{'got '}</span><span>{code} OK · application/json · {step.ms} ms</span></span>
  </div>

  {#if step.contract}
    <div class="tr-code-pair">
      {@render codeCard('Contract · expected', run.details.contractFile ?? 'contract', diff.left, 'Contract schema', false)}
      <div aria-hidden="true" class="tr-code-swap">⇄</div>
      {@render codeCard('Actual response', hostOf(run.testedEndpoint), diff.right, 'Actual response', ko)}
    </div>
  {/if}

  {#if ko}
    <div class="tr-violations">
      <ul class="tr-violation-list">
        {#each step.violations as v (v.path)}
          <li class="tr-violation">
            <span aria-hidden="true" class="tr-violation-mark">✕</span>
            <span class="tr-violation-body">
              <span class="tr-violation-text"><span class="tr-violation-path">{v.path}</span> {v.message}</span>
              {#if v.raw}<span class="tr-violation-raw">validator: {v.raw}</span>{/if}
            </span>
          </li>
        {/each}
      </ul>
      <p class="tr-diff-note">
        The endpoint answered <span class="tr-mono">{code}</span> as the contract declares, but its body breaks the <span class="tr-mono">{schema}</span> schema on {vCount.replace(/violations?/, step.violations.length === 1 ? 'property' : 'properties')}. Fix the implementation{#if newEnum}, or if <span class="tr-mono">{newEnum}</span> is an intended new status, add it to the contract and re-import{/if}.
        <a href="/services/{run.serviceId}" class="tr-diff-link">Open {opText}</a>
      </p>
    </div>
  {:else}
    <p class="tr-diff-ok">
      <Icon name="check" size={14} />
      <span>
        {#if step.contract}
          {isArray ? `Conformant. ${n} ${n === 1 ? 'item' : 'items'} checked against ${schema}, ${propCount} properties each, 0 violations. Status ${code} is declared by the contract.` : `Conformant. ${propCount} properties checked against ${schema}, 0 violations. Status ${code} is declared by the contract.`}
        {:else}
          Conformant. Status {code} is declared by the contract.
        {/if}
      </span>
    </p>
  {/if}
</div>
