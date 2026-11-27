<script lang="ts">
  import { Button, CodeBlock, CopyButton } from '@microcks/deep-ink';
  import { isAvailable, unavailableReason } from '#lib/workflows/availability.js';
  import type { RunApiBody } from '#lib/runs-workflow.js';

  interface Props {
    hint: string;
    documentApiId: string;
    documentLabel: string;
    workflowId: string;
    mockUrl: string;
    body: RunApiBody;
    mixed?: boolean;
    /** Mixed is a proposal of the mockup (placeholder data); false for a workflow Microcks runs. */
    proposed?: boolean;
  }

  let { hint, documentApiId, documentLabel, workflowId, mockUrl, body, mixed, proposed = true }: Props = $props();

  // The placeholder documents show a proposed endpoint on a made-up host; a real document shows the Microcks of its Mock URL.
  const mockOn = $derived(isAvailable('mock-url', proposed));
  const host = $derived(proposed ? 'http://microcks.local:8080' : (URL.canParse(mockUrl) ? new URL(mockUrl).origin : 'http://localhost:8080'));
  const runsUrl = $derived(`${host}/api/arazzo/documents/${documentApiId}/workflows/${workflowId}/runs`);
  const curl = $derived(`curl -X POST \\\n  '${runsUrl}' \\\n  -H 'Content-Type: application/json' \\\n  -d '${JSON.stringify(body, null, 2)}'`);
  const curlOne = $derived(`curl -X POST '${runsUrl}' -H 'Content-Type: application/json' -d '${JSON.stringify(body)}'`);
  const mockBody = $derived(JSON.stringify(body.inputs));
  const mockCurl = $derived(`curl -X POST \\\n  '${mockUrl}' \\\n  -H 'Content-Type: application/json' \\\n  -d '${mockBody}'`);
  const mockOne = $derived(`curl -X POST '${mockUrl}' -H 'Content-Type: application/json' -d '${mockBody}'`);
</script>

<section aria-labelledby="run-api-h" class="wfr-card wfr-api">
  <h2 id="run-api-h" class="wfr-card-title">Run it from the API</h2>
  <p class="wfr-note">{hint}</p>
  <CodeBlock class="wfr-pre" variant="plain" copy={false} language="bash" caption="curl command that launches the run" code={curl} />
  <div class="wfr-api-foot">
    <p class="wfr-note">
      Answers 201 with the run; read it at <span class="wfr-mono">GET /api/arazzo/runs/{'{id}'}</span>. {documentApiId.slice(0, 8)}… is the documentId of {documentLabel}.
    </p>
    <CopyButton value={curlOne} label="Copy command" variant="outline" class="wfr-copy" />
  </div>
  {#if mixed && !proposed}
    <p class="wfr-note">
      <span class="wfr-mono">target.perSource</span> says where each source goes; a source it does not name stays on its mocks.
    </p>
  {:else if mixed}
    <p class="wfr-note" data-tone="warning">
      Proposed · beyond POC: a source left out of <span class="wfr-mono">implementations</span> stays on its mocks.
    </p>
  {/if}
  <div class="wfr-api-mock">
    <h3 class="wfr-card-subtitle">Or play it on the mocks through its Mock URL</h3>
    {#if mockOn}
      <p class="wfr-note">Microcks plays {workflowId} on its mocks and answers 200 with the workflow outputs. Each call creates a run with origin Mock URL.</p>
      <CodeBlock class="wfr-pre" variant="plain" copy={false} language="bash" caption="curl command for the Mock URL" code={mockCurl} />
      <CopyButton value={mockOne} label="Copy command" variant="outline" class="wfr-copy" />
    {:else}
      <p class="wfr-note">{unavailableReason('mock-url', proposed)}.</p>
      <Button size="sm" disabled title={unavailableReason('mock-url', proposed)} class="wfr-copy">Copy command</Button>
    {/if}
  </div>
</section>
