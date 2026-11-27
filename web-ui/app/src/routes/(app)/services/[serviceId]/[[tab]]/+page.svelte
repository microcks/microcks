<script lang="ts">
  import { microcksBaseUrl } from '#lib/base-url.js';
  import { goto, invalidateAll } from '$app/navigation';
  import { page } from '$app/state';
  import { ApiBadge, Button, Callout, Chip, CodeBlock, CopyButton, Icon, Input, LiveIndicator, MethodTag, Tabs } from '@microcks/deep-ink';
  import EditLabelsDialog from '#lib/components/catalog/EditLabelsDialog.svelte';
  import CatalogPageHeader from '#lib/components/catalog/CatalogPageHeader.svelte';
  import ExchangeCard from '#lib/components/catalog/ExchangeCard.svelte';
  import ExchangePair from '#lib/components/catalog/ExchangePair.svelte';
  import Menu from '#lib/components/catalog/Menu.svelte';
  import NotFound from '#lib/components/catalog/NotFound.svelte';
  import OpPickList from '#lib/components/catalog/OpPickList.svelte';
  import Sparkline from '#lib/components/catalog/Sparkline.svelte';
  import TestHistory from '#lib/components/testing/TestHistory.svelte';
  import { runConformance, summarize } from '#lib/components/testing/model.js';
  import SplitPane from '#lib/components/catalog/SplitPane.svelte';
  import { buildSnippet, deriveInputSchema, exampleUrlPath, mcpBaseUrl, mcpServerName, mcpToolName, paramLines, requestHead, type SnippetFormat } from '#lib/components/catalog/snippets.js';
  import { fmt, formatDay, formatHourMinute, formatRelative, pluralize } from '#lib/format.js';

  let { data } = $props();

  const TABS = ['operations', 'properties', 'tests'];
  const SHOW_WORKFLOW_USAGE = false;
  const detail = $derived(data.detail);
  const svc = $derived(detail?.service);
  const tab = $derived(TABS.includes(page.params.tab ?? '') ? (page.params.tab as string) : 'operations');
  const ops = $derived(detail?.operations ?? []);
  const currentOp = $derived(ops.find((o) => o.id === page.url.searchParams.get('op')) ?? ops[0]);
  const examples = $derived(currentOp?.examples ?? []);
  const selected = $derived(examples.find((e) => e.id === page.url.searchParams.get('example')) ?? examples[0]);
  const fromContract = $derived(examples.filter((e) => !e.aiGenerated));
  const aiAccepted = $derived(examples.filter((e) => e.aiGenerated));
  const totalAi = $derived(ops.reduce((n, o) => n + o.examples.filter((e) => e.aiGenerated).length, 0));
  const totalExamples = $derived(ops.reduce((n, o) => n + o.examples.length, 0));
  let format = $state<SnippetFormat>('curl');

  const runs = $derived((data.runs ?? []).map(summarize));
  const latestRun = $derived(runs.reduce<(typeof runs)[number] | undefined>((a, r) => (!a || r.startedAt > a.startedAt ? r : a), undefined));
  const conformance = $derived(latestRun ? runConformance(latestRun) : null);

  const mcpBase = $derived(svc ? mcpBaseUrl(svc, microcksBaseUrl()) : '');
  const toolOf = (o: { method: string; path: string }) => mcpToolName(o.method, o.path);
  const spec = $derived(
    currentOp && selected && detail
      ? { method: currentOp.method, pathTemplate: currentOp.path, example: selected, mockBase: detail.mockBase, mcpBase, mcpTool: toolOf(currentOp) }
      : undefined
  );
  const mockUrl = $derived(spec ? `${detail!.mockBase}${exampleUrlPath(spec.pathTemplate, spec.example.request.params)}` : (detail?.mockBase ?? ''));
  const mcpConfig = $derived(
    svc ? JSON.stringify({ mcpServers: { [mcpServerName(svc.name)]: { url: mcpBase, transport: 'streamable-http' } } }, null, 2) : ''
  );
  const labels = $derived(Object.entries(svc?.labels ?? {}).map(([k, v]) => (v ? `${k}=${v}` : k)));
  const exampleSub = (params: Record<string, string>) => Object.entries(params).map(([k, v]) => `${k}=${v}`).join(' · ') || 'no parameter';
  const calls = $derived(detail?.calls ?? []);
  const calls7d = $derived(calls.reduce((a, b) => a + b, 0));
  let status = $state('');
  let editingLabels = $state(false);

  function select(patch: Record<string, string | undefined>) {
    const next = new URLSearchParams(page.url.searchParams.toString());
    for (const [k, v] of Object.entries(patch)) {
      if (v === undefined) next.delete(k);
      else next.set(k, v);
    }
    const qs = next.toString();
    goto(`${page.url.pathname}${qs ? `?${qs}` : ''}`, { replace: true, reset: false });
  }

  const goTab = (id: string) => goto(`/services/${svc!.id}/${id}${page.url.search}`, { reset: false });
</script>

{#if !svc || !detail}
  <NotFound status={data.status} />
{:else}
  <div class="ex-stack service-page">
    <CatalogPageHeader
      titleText={`${svc.name} ${svc.version}`}
      variant="badge"
      align="start"
      breadcrumb={[{ label: 'APIs & Services', href: '/services' }, { label: `${svc.name} ${svc.version}` }]}
      breadcrumbSeparator="/"
      description={detail.description}
      descriptionTone="secondary"
    >
      {#snippet title()}<ApiBadge protocol={svc.protocol} name={`${svc.name} ${svc.version}`} size="xl" />{/snippet}
      {#snippet badge()}<LiveIndicator label="mock live" />{/snippet}
      {#snippet actions()}
        <Button variant="primary" icon="play" href={`/tests/create?service=${svc.id}`}>Run a test</Button>
        <Button variant="secondary" icon="spark" class="service-ai-action" href={`/services/${svc.id}/copilot${currentOp ? `?op=${currentOp.id}` : ''}`}>Generate examples (AI)</Button>
        <Menu
          label="More actions"
          icon="more-vertical"
          align="end"
          items={[
            { id: 'labels', label: 'Edit labels', onselect: () => (editingLabels = true) },
            { id: 'contract', label: 'Open source', href: svc.sourceIds[0] ? `/sources/${svc.sourceIds[0]}` : '/sources' },
            { id: 'delete', label: 'Delete service', disabled: true, description: 'Remove its source from Sources.' }
          ]}
        />
      {/snippet}
      {#snippet children()}
        <div class="service-head-row">
          {#each labels as l (l)}<Chip variant="outline">{l}</Chip>{/each}
          <span class="service-head-dates">Created {formatDay(svc.createdAt)} · imported {formatDay(svc.updatedAt)} at {formatHourMinute(svc.updatedAt)}</span>
        </div>
        <div class="service-head-proofs">
          {#if latestRun && conformance !== null}
            <ApiBadge label="conformance" value="{conformance}%" status={conformance >= 80 ? 'success' : conformance >= 50 ? 'warning' : 'danger'} />
          {:else}
            <ApiBadge label="conformance" value="untested" status="neutral" />
          {/if}
          <ApiBadge label="mcp" value={svc.protocol === 'REST' ? 'agent-ready' : 'not exposed'} status={svc.protocol === 'REST' ? 'info' : 'neutral'} />
          <ApiBadge label="samples" value={String(totalExamples - totalAi)} status="action" values={totalAi > 0 ? [{ value: `+${totalAi} AI`, status: 'success' }] : undefined} class="service-samples" />
        </div>
      {/snippet}
    </CatalogPageHeader>
    <p class="sr-only" role="status">{status}</p>
    {#if editingLabels}
      <EditLabelsDialog
        endpoint={`/api/services/${encodeURIComponent(svc.id)}/labels`}
        resourceName={`${svc.name} ${svc.version}`}
        labels={svc.labels}
        onclose={() => (editingLabels = false)}
        onsaved={async () => {
          await invalidateAll();
          status = 'Labels have been updated.';
        }}
      />
    {/if}

    <Tabs
      label="Service sections"
      value={tab}
      onchange={goTab}
      items={[
        { id: 'operations', label: 'Operations', count: ops.length },
        { id: 'properties', label: 'Properties & MCP' },
        { id: 'tests', label: 'Tests' }
      ]}
    >
      {#snippet children()}
        {#if tab === 'operations'}
          {@render operationsTab()}
        {:else if tab === 'properties'}
          {@render propertiesTab()}
        {:else}
          {@render testsTab()}
        {/if}
      {/snippet}
    </Tabs>
  </div>
{/if}

{#snippet operationsTab()}
  {#if !currentOp}
    <Callout tone="info" title="No operation">This service has no operation yet.</Callout>
  {:else}
    <SplitPane sideMax="360px" keepSideMax class="op-split">
      {#snippet side()}
        <div class="op-rail">
          <p class="section-label op-rail-head" data-size="sm" data-weight="regular" data-split><span>{pluralize(ops.length, 'operation')}</span><span>calls · 7 d (demo)</span></p>
          <OpPickList
            label={`Operations of ${svc!.name} ${svc!.version}`}
            value={currentOp.id}
            onchange={(id) => select({ op: id, example: undefined })}
            options={ops.map((o) => {
              const ai = o.examples.filter((e) => e.aiGenerated).length;
              return {
                id: o.id,
                method: o.method,
                protocol: svc!.protocol,
                path: o.path,
                summary: o.summary,
                meta: `${pluralize(o.examples.length - ai, 'example')}${ai ? ` · +${ai} AI` : ''}`,
              };
            })}
          />
          <p class="op-rail-note">Every operation is served as soon as the contract is imported. No code, no deployment.</p>
        </div>
      {/snippet}
      <section aria-label="Operation detail" class="op-detail">
        <div class="op-detail-head">
          <div class="op-detail-title">
            <div class="op-detail-title-row">
              <MethodTag method={currentOp.method} protocol={svc!.protocol} />
              <h2>{currentOp.path}</h2>
            </div>
            <span class="op-detail-summary">{currentOp.summary || currentOp.name}{svc!.protocol === 'REST' ? ' · MCP tool ' : ''}{#if svc!.protocol === 'REST'}<span class="op-detail-tool">{toolOf(currentOp)}</span>{/if}</span>
          </div>
          {#if calls.length}<Sparkline values={calls} total={calls7d} />{/if}
        </div>

        <div class="op-mock">
          <label class="section-label" for="mock-url">Mock URL</label>
          <div class="op-mock-url">
            <Input id="mock-url" mono readonly value={mockUrl} />
            <CopyButton value={mockUrl} size="md" label="Copy" />
          </div>
          {#if spec}
            <CodeBlock
              code={buildSnippet(format, spec)}
              language="shell"
              tabsLabel="Call format"
              tabs={[{ id: 'curl', label: 'curl' }, { id: 'httpie', label: 'HTTPie' }, ...(svc!.protocol === 'REST' ? [{ id: 'mcp', label: 'MCP tool call' }] : [])]}
              tab={format}
              onTabChange={(id) => (format = id as SnippetFormat)}
              copy
            />
          {/if}
        </div>

        <div class="dispatch-strip">
          <span class="dispatch-strip-item"><span class="dispatch-strip-key">Dispatcher</span><span class="dispatch-strip-value">{currentOp.behavior.dispatcher}{currentOp.behavior.rule ? ` · rule: ${currentOp.behavior.rule}` : ''}</span></span>
          <span class="dispatch-strip-sep" aria-hidden="true"></span>
          <span class="dispatch-strip-item"><span class="dispatch-strip-key">Delay</span><span class="dispatch-strip-value">{currentOp.behavior.delayMs} ms</span><span class="dispatch-strip-key">{currentOp.behavior.delayStrategy}</span></span>
          <Button size="sm" variant="secondary" icon="pencil" class="dispatch-strip-edit" href={`/services/${svc!.id}/operations/${currentOp.id}`}>Edit behavior</Button>
        </div>

        <!-- Hidden for now (see IDEAS.md M2-009): re-enable by setting SHOW_WORKFLOW_USAGE to true. -->
        {#if SHOW_WORKFLOW_USAGE}
        <div role="group" aria-labelledby="wf-used-title" class="wf-usage">
          <div class="wf-usage-head"><h3 id="wf-used-title"><Icon name="dispatcher" size={15} />Used in 0 workflows</h3></div>
          <span class="wf-usage-line"><span class="wf-usage-op">{currentOp.method} {currentOp.path}</span> is not called by any workflow step. Workflows are not part of this preview.</span>
        </div>
        {/if}

        <div class="op-examples">
          <div class="op-examples-head">
            <h3>Examples</h3>
            <span>{pluralize(fromContract.length, 'example')} from the contract{aiAccepted.length ? ` · ${pluralize(aiAccepted.length, 'AI example')} accepted` : ''}</span>
          </div>
          {#if examples.length === 0}
            <Callout tone="info" title="No example in the contract">
              Microcks has nothing to serve for this operation yet.
              <a href={`/services/${svc!.id}/copilot?op=${currentOp.id}`}>Generate examples with AI</a> or add them to the contract.
            </Callout>
          {:else}
            <SplitPane sideWidth="220px" sideMax="300px" mainWidth="420px" gap="16px" keepSideMax>
              {#snippet side()}
                <div class="example-col">
                  <ul class="example-list" aria-label="Examples of the operation">
                    {#each examples as e (e.id)}
                      <li class="example-pick" data-selected={e.id === selected?.id ? '' : undefined}>
                        <button type="button" aria-pressed={e.id === selected?.id} onclick={() => select({ example: e.id })}>
                          <span class="example-pick-status">{e.response.status}</span>
                          <span class="example-pick-main"><span class="example-pick-name">{e.name}</span><span class="example-pick-sub">{exampleSub(e.request.params)}</span></span>
                        </button>
                        {#if e.aiGenerated}<div class="example-pick-accepted"><Icon name="check" size={13} />AI example accepted by {e.acceptedBy ?? 'MA'}</div>{/if}
                      </li>
                    {/each}
                  </ul>
                </div>
              {/snippet}
              {#if selected && spec}
                <div class="example-detail">
                  <ExchangePair>
                    {#snippet request()}
                      <ExchangeCard
                        title="Request"
                        label={`Request of ${selected.name}`}
                        meta={selected.name}
                        blocks={[
                          { label: 'Request line and headers', text: requestHead(spec), tone: 'secondary' },
                          ...(Object.keys(selected.request.params).length ? [{ label: 'Parameters', text: paramLines(spec.pathTemplate, selected.request.params), tone: 'params' as const }] : []),
                          ...(selected.request.body ? [{ label: 'Request body', text: selected.request.body }] : [])
                        ]}
                      />
                    {/snippet}
                    {#snippet response()}
                      <ExchangeCard
                        title="Response"
                        label={`Response of ${selected.name}`}
                        status={`${selected.response.status}${selected.response.status === 200 ? ' OK' : ''}`}
                        blocks={[
                          { label: 'Response headers', text: `Content-Type: ${selected.response.contentType}\nContent-Length: ${selected.response.body.length}`, tone: 'headers' },
                          { label: 'Response body', text: selected.response.body, grow: true }
                        ]}
                      />
                    {/snippet}
                  </ExchangePair>
                </div>
              {/if}
            </SplitPane>
          {/if}
        </div>
      </section>
    </SplitPane>
  {/if}
{/snippet}

{#snippet propertiesTab()}
  <div class="service-props">
    <section aria-labelledby="props-title" class="service-card props-card">
      <h2 id="props-title">Properties</h2>
      <dl class="facts" data-size="lg" style="--facts-term:150px">
        <div class="facts-row"><dt>Version</dt><dd data-mono>{svc!.version}</dd></div>
        <div class="facts-row"><dt>Type</dt><dd><span class="facts-inline"><MethodTag method={svc!.protocol} protocol={svc!.protocol} class="facts-proto" /><span class="ex-secondary">{svc!.format}</span></span></dd></div>
        {#each detail!.artifacts as a, i (a.file + i)}
          <div class="facts-row"><dt>{i === 0 ? 'Primary artifact' : 'Secondary artifact'}</dt><dd><span>{a.file}</span><span class="facts-sub"><span class="facts-kind">{a.kind} · {a.format}</span></span></dd></div>
        {/each}
        <div class="facts-row"><dt>Last import</dt><dd>{formatDay(svc!.updatedAt)} at {formatHourMinute(svc!.updatedAt)} · {formatRelative(svc!.updatedAt)}</dd></div>
        <div class="facts-row"><dt>Created</dt><dd>{formatDay(svc!.createdAt)}</dd></div>
        <div class="facts-row"><dt>Statistics</dt><dd><span class="facts-inline ex-secondary">{fmt(calls7d)} calls over 7 days</span></dd></div>
      </dl>
    </section>
    {#if svc!.protocol === 'REST'}
      <section aria-labelledby="agent-ready-title" class="service-card agent-card">
        <div class="agent-card-head">
          <div class="agent-card-title">
            <h2 id="agent-ready-title">Agent-ready</h2>
            <ApiBadge label="mcp" value={pluralize(ops.length, 'tool')} status="info" />
          </div>
          <CopyButton value={mcpConfig} label="Copy MCP config" size="md" variant="primary" />
        </div>
        <p class="agent-card-lead">Every mock operation can be published as an MCP tool. An AI agent discovers it via <span class="ex-mono">tools/list</span> and calls it with arguments derived from the contract: path, query and body grouped under <span class="ex-mono">params.arguments</span>. Tools and schemas below are derived from the contract; this preview does not serve the MCP endpoint yet.</p>
        <div class="agent-card-block">
          <h3 class="section-label">Endpoints</h3>
          <div class="endpoint-row">
            <span class="endpoint-row-name"><span>Streamable HTTP</span><span class="endpoint-row-spec">MCP 2025-03-26</span></span>
            <span class="endpoint-row-url" title={mcpBase}>{mcpBase}</span>
            <CopyButton value={mcpBase} label="Copy" variant="link" class="endpoint-row-copy" />
          </div>
        </div>
        <div class="agent-card-block">
          <h3 class="section-label">Exposed tools, as an agent sees them</h3>
          {#each ops as o (o.id)}
            <div class="tool-card">
              <div class="tool-card-main">
                <span class="tool-card-name">{toolOf(o)}</span>
                <span class="tool-card-desc">{o.summary || o.name}</span>
                <span class="tool-card-op"><MethodTag method={o.method} protocol={svc!.protocol} class="tool-card-method" /><span class="ex-mono">{o.path}</span></span>
              </div>
              <pre tabindex="0" role="region" aria-label={`Input schema of ${toolOf(o)}`} class="tool-card-schema">{deriveInputSchema({ method: o.method, path: o.path, dispatcher: o.behavior.dispatcher, rule: o.behavior.rule })}</pre>
            </div>
          {/each}
        </div>
        <div class="agent-card-block">
          <h3 class="section-label">MCP client config</h3>
          <pre tabindex="0" role="region" aria-label="MCP client config" class="agent-card-config">{mcpConfig}</pre>
        </div>
      </section>
    {:else}
      <Callout tone="info" title="Not exposed to agents">This {svc!.protocol} service is not published as MCP tools.</Callout>
    {/if}
  </div>
{/snippet}

{#snippet testsTab()}
  <section aria-labelledby="runs-title" class="service-card runs-card">
    <div class="runs-card-head">
      <div class="runs-card-title"><h2 id="runs-title">Test runs</h2></div>
      <div class="runs-card-actions">
        <Button variant="primary" icon="plus" class="runs-card-new" href={`/tests/create?service=${svc!.id}`}>New test</Button>
      </div>
    </div>
    {#if runs.length}
      {#key svc!.id}
        <TestHistory {runs} operations={ops.map((o) => ({ id: o.id, verb: o.method, path: o.path }))} serviceId={svc!.id} />
      {/key}
    {:else}
      <p>No test run yet. Run a first test to get a conformance score.</p>
    {/if}
  </section>
{/snippet}
