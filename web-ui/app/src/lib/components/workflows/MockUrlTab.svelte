<script lang="ts">
  import { Button, Callout, Icon, MethodTag } from '@microcks/deep-ink';
  import { formatRelative } from '#lib/format.js';
  import { callTotals, stoppedStep } from '#lib/workflows/describe.js';
  import { sampleInputs } from '#lib/workflows/mock-url.js';
  import { workflowPath } from '#lib/workflows/paths.js';
  import { formatDuration } from '#lib/workflows/time.js';
  import type { ArazzoDocument, Workflow, WorkflowExample, WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    workflow: Workflow;
    document: ArazzoDocument;
    runs: WorkflowRun[];
    examples: WorkflowExample[];
    onshowruns: () => void;
  }

  interface Answer {
    code: string;
    tone: 'success' | 'warning' | 'danger';
    title: string;
    when: string;
    head: string;
    body: unknown;
  }

  let { workflow, document, runs, examples, onshowruns }: Props = $props();

  const SAMPLE_RUN_ID = '6543f0c2a9e1b7d4c3f20a11';
  let pick = $state('200');
  let copied = $state<string>();

  const mockRuns = $derived(runs.filter((r) => r.origin.kind === 'mock-url').sort((a, b) => b.number - a.number));
  const last = $derived(mockRuns[0]);
  const lastMockRun = last;
  /** The run of the last call on a real workflow, else the sample id of the mockup. */
  const RUN_ID = $derived(last?.id ?? SAMPLE_RUN_ID);
  const sample = $derived(
    (lastMockRun?.status === 'SUCCESS' ? lastMockRun : runs.find((r) => r.status === 'SUCCESS' && Object.keys(r.outputs).length > 0))?.outputs ?? {}
  );
  const failedStep = $derived(
    runs.map(stoppedStep).find((s) => s?.status === 'failed')?.stepId ?? workflow.steps[workflow.steps.length - 1]?.stepId ?? 'step'
  );
  const body = $derived(examples[0]?.inputs ?? sampleInputs(workflow.inputs));
  const curl = $derived(`curl -X POST '${workflow.mockUrl}' \\\n  -H 'Content-Type: application/json' \\\n  -d '${JSON.stringify(body)}'`);
  const answers = $derived<Answer[]>([
    { code: '200', tone: 'success', title: 'The workflow outputs', when: 'The workflow passed on the mocks: the body is exactly its outputs.', head: `200 OK · X-Microcks-Workflow-Run-Id: ${RUN_ID}`, body: sample },
    { code: '400', tone: 'warning', title: 'Refused', when: 'The body is not a JSON object of inputs. No run is created.', head: '400 Bad Request', body: { reason: 'The inputs must be a JSON object' } },
    {
      code: '504',
      tone: 'danger',
      title: 'Timeout',
      when: 'The run did not end within 30000 ms. It goes on; open it to see how it ends.',
      head: `504 Gateway Timeout · X-Microcks-Workflow-Run-Id: ${RUN_ID}`,
      body: { runId: RUN_ID, reason: `Run #${(runs[0]?.number ?? 0) + 1} did not end within 30000 ms` }
    },
    {
      code: '409',
      tone: 'danger',
      title: 'A step failed',
      when: 'The status the mock of the failed step answered, else 502, naming the failed step.',
      head: `409 Conflict · X-Microcks-Workflow-Run-Id: ${RUN_ID}`,
      body: { runId: RUN_ID, failedStep, reason: `Step ${failedStep} failed: unmet success criteria $statusCode == 200` }
    }
  ]);
  const chosen = $derived(answers.find((a) => a.code === pick) ?? answers[0]);

  async function copy(id: string, text: string) {
    try {
      await navigator.clipboard.writeText(text);
      copied = id;
    } catch {
      /* clipboard blocked */
    }
  }
</script>

{#if !workflow.runnability.runnable}
  <Callout tone="warning" title="Mock URL not available">{workflow.runnability.message}. Its Mock URL answers once the workflow can run.</Callout>
{:else}
  <div class="wf-mu-tab">
    <section aria-labelledby="wf-mu-title" class="wf-mu-panel" data-size="wide">
      <div class="wf-mu-intro">
        <h2 id="wf-mu-title">Mock URL</h2>
        <p>
          POST the inputs as JSON: Microcks plays the workflow on its mocks and answers with its outputs. An application can call one business operation instead
          of chaining the {workflow.steps.length} calls itself. Each call is a run of <span class="wf-mu-mono">{workflow.workflowId}</span> with the origin
          Mock URL, listed in Runs.
        </p>
      </div>

      <div class="wf-mu-field">
        <label for="workflow-mock-url">Mock URL</label>
        <div class="wf-mu-row">
          <MethodTag method="POST" class="wf-mu-method" />
          <input id="workflow-mock-url" type="text" readonly value={workflow.mockUrl} class="wf-mu-input" />
          <Button size="sm" class="wf-mu-copy" aria-label="Copy the Mock URL of {workflow.workflowId}" onclick={() => copy('url', workflow.mockUrl)}>
            {copied === 'url' ? 'Copied' : 'Copy'}
          </Button>
        </div>
        <span class="wf-mu-help"><span class="wf-mu-mono">/workflow/&#123;title&#125;/&#123;version&#125;/&#123;workflowId&#125;</span>, encoded like the Mock URLs of APIs.</span>
      </div>

      <div class="wf-mu-boxes">
        <div class="wf-mu-box" data-size="curl">
          <div class="wf-mu-box-head">
            <span class="wf-mu-box-title">curl <span class="wf-mu-box-sub">· inputs of {examples[0] ? `the example ${examples[0].id}` : 'the workflow examples'}</span></span>
            <Button size="sm" class="wf-mu-box-btn" aria-label="{copied === 'curl' ? 'Copied' : 'Copy curl'} of {workflow.workflowId}" onclick={() => copy('curl', curl)}>
              {copied === 'curl' ? 'Copied' : 'Copy curl'}
            </Button>
          </div>
          <pre class="wf-mu-pre" data-tone="secondary">{curl}</pre>
        </div>
        <div class="wf-mu-box">
          <div class="wf-mu-box-head"><span class="wf-mu-box-title">Response</span><span class="wf-mu-code" data-tone="success">200 OK</span></div>
          <pre class="wf-mu-pre">{JSON.stringify(sample, null, 2)}</pre>
        </div>
      </div>

      <div class="wf-mu-last">
        {#if last}
          <span class="wf-mu-last-text">Last call: run #{last.number} · {formatRelative(last.startedAt)}</span>
          <span class="wf-mu-verdict" data-tone={last.status === 'SUCCESS' ? 'success' : 'danger'}>
            <Icon name={last.status === 'SUCCESS' ? 'check-circle' : 'x-circle'} size={13} />{last.status === 'SUCCESS' ? 'Passed' : 'Failed'}
          </span>
          <span class="wf-mu-muted">answered {last.status === 'SUCCESS' ? 200 : 409} in {formatDuration(callTotals(last).ms)}</span>
        {:else}
          <span class="wf-mu-muted">Never called.</span>
        {/if}
        <Button size="sm" class="wf-mu-runs" onclick={onshowruns}>Show the Mock URL runs</Button>
      </div>
    </section>

    <section aria-labelledby="wf-ans-title" class="wf-mu-panel" data-size="narrow">
      <h2 id="wf-ans-title">Possible answers</h2>
      <div role="group" aria-label="Choose an answer" class="wf-mu-answers">
        {#each answers as a (a.code)}
          <button type="button" class="wf-mu-answer" aria-pressed={a.code === chosen.code} onclick={() => (pick = a.code)}>
            <span class="wf-mu-code" data-tone={a.tone}>{a.code}</span>
            <span class="wf-mu-answer-text"><span class="wf-mu-answer-title">{a.title}</span><span class="wf-mu-answer-when">{a.when}</span></span>
          </button>
        {/each}
      </div>
      <div class="wf-mu-box">
        <div class="wf-mu-box-head" data-kind="mono">{chosen.head}</div>
        <pre class="wf-mu-pre">{JSON.stringify(chosen.body, null, 2)}</pre>
      </div>
      <span class="wf-mu-note">Every answer but a refusal carries the header <span class="wf-mu-mono">X-Microcks-Workflow-Run-Id</span>, so the caller can open the run.</span>
    </section>
  </div>
{/if}
