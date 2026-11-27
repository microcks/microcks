<script lang="ts">
  import { isAvailable } from '#lib/workflows/availability.js';
  import { Button, Icon } from '@microcks/deep-ink';
  import { formatRelative } from '#lib/format.js';
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

  let showOverlay = $state(false);
  let overlay = $state<string | null | undefined>();
  let overlayError = $state(false);

  async function toggle() {
    showOverlay = !showOverlay;
    if (showOverlay && overlay === undefined) {
      overlayError = false;
      try {
        const res = await fetch(`/api/workflows/documents/${encodeURIComponent(document.id)}/overlay`);
        if (!res.ok) throw new Error(String(res.status));
        overlay = await res.text();
      } catch {
        overlayError = true;
      }
    }
  }

  function lastJudged(ex: WorkflowExample) {
    const run = runs.find((r) => r.options.example === ex.id && r.target.kind === 'mocks' && r.status !== 'RUNNING');
    if (run) return { run, number: run.number, at: run.startedAt, matched: run.status === 'SUCCESS', where: originWord(run) };
    return ex.lastJudged ? { run: undefined, number: ex.lastJudged.runNumber, at: ex.lastJudged.at, matched: ex.lastJudged.matched, where: 'Microcks mocks' } : undefined;
  }
</script>

{#if examples.length === 0}
  <section aria-labelledby="wf-ex-title" class="wf-ex-panel">
    <h2 id="wf-ex-title">No recorded example</h2>
    <p class="wf-ex-lead">Examples come from an ArazzoSources overlay: they give the inputs of a run and what it must show. {document.overlay ? '' : `${document.file} has no overlay yet.`}</p>
  </section>
{:else}
  <section aria-labelledby="wf-ex-title" class="wf-ex-panel">
    <div class="wf-ex-head">
      <div class="wf-ex-intro">
        <h2 id="wf-ex-title">{examples.length} recorded {examples.length === 1 ? 'example' : 'examples'}</h2>
        <p class="wf-ex-lead">
          From the ArazzoSources overlay <span class="wf-ex-mono">{document.overlay}</span>, identified by {document.title} {document.version}. An example gives
          inputs and what the run must show: step statuses, where it ends and its outputs. Running an example judges the run against it.
        </p>
      </div>
      <Button size="sm" aria-expanded={showOverlay} aria-controls="wf-overlay" onclick={toggle}>{showOverlay ? 'Hide the overlay' : 'Show the overlay'}</Button>
    </div>
    <div id="wf-overlay" hidden={!showOverlay}>
      {#if showOverlay}
        {#if overlayError}
          <p role="alert" class="wf-ex-lead">The overlay could not be loaded.</p>
        {:else if overlay}
          <pre aria-label="ArazzoSources overlay" class="wf-ex-overlay">{overlay.replace(/\n$/, '')}</pre>
        {:else if overlay === undefined}
          <p class="wf-ex-lead" role="status">Loading the overlay…</p>
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
              <span class="wf-ex-desc">{ex.description}</span>
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
                    <span class="wf-ex-muted">{exp ? [`Passed · ${exp.status}`, ...Object.entries(exp.outputs ?? {}).map(([k, v]) => `${k} = ${v}`)].join(' · ') : 'Not run'}</span>
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
    {#if document.overlay}
      <p class="wf-ex-foot">
        The overlay also links the source descriptions:
        {#each document.sources as s, i (s.name)}{i > 0 ? ', ' : ''}<span class="wf-ex-mono">{s.name}</span>{s.link.status === 'linked' ? ` → ${serviceLabel(s.link.serviceId) ?? s.link.serviceId}` : ' (not linked)'}{/each}.
        {#if isAvailable('mock-url', document)}The Mock URL curl uses the inputs of the first example.{/if}
      </p>
    {/if}
  </section>
{/if}
