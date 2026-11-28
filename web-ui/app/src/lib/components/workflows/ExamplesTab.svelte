<script lang="ts">
  import { isAvailable } from '#lib/workflows/availability.js';
  import { Button, Icon } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';
  import { judgedAs } from '#lib/workflows/examples.js';
  import { isDemo } from '#lib/workflows/availability.js';
  import { formatRelative } from '#lib/format.js';
  import { invalidate } from '$app/navigation';
  import { runPath, runSetupPath } from '#lib/workflows/paths.js';
  import { originWord } from '#lib/workflows/run-facts.js';
  import type { ArazzoDocument, Workflow, WorkflowExample, WorkflowRun } from '#lib/workflows/types.js';

  interface Props {
    workflow: Workflow;
    document: ArazzoDocument;
    runs: WorkflowRun[];
    examples: WorkflowExample[];
    serviceLabel: (serviceId: string) => string | undefined;
  }

  let { workflow, document, runs, examples, serviceLabel }: Props = $props();

  let showFile = $state(false);
  let file = $state<string | null | undefined>();
  let fileError = $state(false);

  async function toggle() {
    showFile = !showFile;
    if (showFile && file === undefined) {
      fileError = false;
      try {
        const res = await fetch(`/api/workflows/documents/${encodeURIComponent(document.id)}/examples`);
        if (!res.ok) throw new Error(String(res.status));
        file = await res.text();
      } catch {
        fileError = true;
      }
    }
  }

  /** Microcks plays every recorded example in one call (CI style); the demo documents play them one at a time. */
  const canPlayAll = $derived(!isDemo(document) && workflow.runnability.runnable);
  let playing = $state(false);
  let playError = $state<string>();
  let launched = $state<{ example: string; runId?: string; number?: number; refusal?: string }[]>();

  async function playAll() {
    playing = true;
    playError = undefined;
    launched = undefined;
    try {
      const res = await api<{ launches: NonNullable<typeof launched> }>(`/api/runs/workflow-examples/${document.id}/${workflow.workflowId}`, { method: 'POST', json: {} });
      launched = res.launches;
      await invalidate('app:workflows');
    } catch (e) {
      playError = e instanceof Error ? e.message : String(e);
    } finally {
      playing = false;
    }
  }

  function lastJudged(ex: WorkflowExample) {
    const run = runs.find((r) => r.options.example === ex.id && (r.exampleCheck !== undefined || r.target.kind === 'mocks') && r.status !== 'RUNNING');
    if (run) return { run, number: run.number, at: run.startedAt, matched: judgedAs(run), where: originWord(run) };
    return ex.lastJudged ? { run: undefined, number: ex.lastJudged.runNumber, at: ex.lastJudged.at, matched: ex.lastJudged.matched, where: 'Microcks mocks' } : undefined;
  }
</script>

{#if examples.length === 0}
  <section aria-labelledby="wf-ex-title" class="wf-ex-panel">
    <h2 id="wf-ex-title">No recorded example</h2>
    <p class="wf-ex-lead">Examples come from an ArazzoExamples: they give the inputs of a run and what it must show. Import one for {document.title} {document.version} from Sources.</p>
  </section>
{:else}
  <section aria-labelledby="wf-ex-title" class="wf-ex-panel">
    <div class="wf-ex-head">
      <div class="wf-ex-intro">
        <h2 id="wf-ex-title">{examples.length} recorded {examples.length === 1 ? 'example' : 'examples'}</h2>
        <p class="wf-ex-lead">
          From the ArazzoExamples of {document.title} {document.version}. An example gives
          inputs and what the run must show: step statuses, where it ends and its outputs. Running an example judges the run against it.
        </p>
      </div>
      <div class="wf-ex-head-actions">
        {#if canPlayAll}
          <Button size="sm" variant="primary" icon="play" loading={playing} onclick={playAll}>Run every example</Button>
        {/if}
        <Button size="sm" aria-expanded={showFile} aria-controls="wf-examples-file" onclick={toggle}>{showFile ? 'Hide the ArazzoExamples' : 'Show the ArazzoExamples'}</Button>
      </div>
    </div>
    {#if playError}<p role="alert" class="wf-ex-lead">{playError}</p>{/if}
    {#if launched}
      <ul class="wf-ex-launched" role="status" aria-label="Runs launched for the examples">
        {#each launched as l (l.example)}
          <li>
            <span class="wf-ex-mono">{l.example}</span>
            {#if l.runId}<a href={runPath(l.runId, true)}>Run #{l.number}</a>{:else}<span class="wf-ex-muted">not run: {l.refusal}</span>{/if}
          </li>
        {/each}
      </ul>
    {/if}
    <div id="wf-examples-file" hidden={!showFile}>
      {#if showFile}
        {#if fileError}
          <p role="alert" class="wf-ex-lead">The ArazzoExamples could not be loaded.</p>
        {:else if file}
          <pre aria-label="ArazzoExamples" class="wf-ex-yaml">{file.replace(/\n$/, '')}</pre>
        {:else if file === undefined}
          <p class="wf-ex-lead" role="status">Loading the ArazzoExamples…</p>
        {/if}
      {/if}
    </div>

    <div class="wf-ex-list">
      {#each examples as ex (ex.id)}
        {@const judged = lastJudged(ex)}
        {@const outputs = Object.entries(ex.outputs)}
        <article aria-labelledby="wf-ex-{ex.id}" class="wf-ex-card">
          <div class="wf-ex-card-head">
            <div class="wf-ex-card-title">
              <h3 id="wf-ex-{ex.id}">{ex.id}</h3>
              {#if ex.description}<span class="wf-ex-desc">{ex.description}</span>{/if}
            </div>
            {#if workflow.runnability.runnable}
              <Button variant="primary" size="compact" icon="play" href="{runSetupPath(document.id, workflow.workflowId)}?example={ex.id}" aria-label="Run this example {ex.id}">Run this example</Button>
            {/if}
          </div>
          <dl class="wf-ex-facts">
            <div>
              <dt>Inputs</dt>
              <dd class="wf-ex-tags">
                {#if Object.keys(ex.inputs).length}
                  {#each Object.entries(ex.inputs) as [k, v] (k)}<span class="wf-ex-tag">{k} = {v}</span>{/each}
                {:else}none{/if}
              </dd>
            </div>
            <div>
              <dt>Expected steps</dt>
              <dd class="wf-ex-steps">
                {#each workflow.steps as s (s.stepId)}
                  {@const exp = ex.steps.find((x) => x.stepId === s.stepId)}
                  <span class="wf-ex-step" data-run={exp ? undefined : 'no'}>
                    <Icon name={exp ? 'check-circle' : 'minus-circle'} size={12} />
                    <span class="wf-ex-step-id">{s.stepId}</span>
                    <span class="wf-ex-muted">{exp ? [exp.status < 400 ? `Passed · ${exp.status}` : `Responds ${exp.status}`, ...Object.entries(exp.outputs ?? {}).map(([k, v]) => `${k} = ${v}`)].join(' · ') : 'Not run'}</span>
                  </span>
                {/each}
              </dd>
            </div>
            <div>
              <dt>Expected end</dt>
              <dd class="wf-ex-mono">{ex.stopsAt ? `Stops at ${ex.stopsAt}` : 'Runs to its end'}</dd>
              <dt class="wf-ex-dt-next">Expected outputs</dt>
              <dd class="wf-ex-mono" data-empty={outputs.length ? undefined : ''}>{outputs.length ? outputs.map(([k, v]) => `${k} = ${v}`).join(', ') : 'None expected'}</dd>
            </div>
            <div>
              <dt>Last judged</dt>
              <dd class="wf-ex-judged">
                {#if judged}
                  <span class="wf-ex-verdict" data-tone={judged.matched ? 'pass' : 'fail'}>
                    <Icon name={judged.matched ? 'check-circle' : 'x-circle'} size={13} />{judged.matched ? 'Matches the example' : 'Does not match the example'}
                  </span>
                  <span class="wf-ex-muted">
                    {#if judged.run}<a href={runPath(judged.run.id)}>Run #{judged.number}</a>{:else}Run #{judged.number}{/if} · {formatRelative(judged.at)} · {judged.where}
                  </span>
                {:else}
                  <span class="wf-ex-muted">Not run yet</span>
                {/if}
              </dd>
            </div>
          </dl>
        </article>
      {/each}
    </div>
    {#if document.metadata}
      <p class="wf-ex-foot">
        The ArazzoMetadata links the source descriptions:
        {#each document.sources as s, i (s.name)}{i > 0 ? ', ' : ''}<span class="wf-ex-mono">{s.name}</span>{s.link.status === 'linked' ? ` → ${serviceLabel(s.link.serviceId) ?? s.link.serviceId}` : ' (not linked)'}{/each}.
        {#if isAvailable('mock-url', document)}The Mock URL curl uses the inputs of the first example.{/if}
      </p>
    {/if}
  </section>
{/if}

<style>
  .wf-ex-head-actions { display: flex; flex-wrap: wrap; gap: var(--space-2); align-items: flex-start; }
  .wf-ex-launched { list-style: none; margin: 0 0 var(--space-3); padding: 0; display: grid; gap: 4px; font: var(--type-small); }
  .wf-ex-launched li { display: flex; flex-wrap: wrap; gap: 6px 14px; align-items: baseline; }
</style>
