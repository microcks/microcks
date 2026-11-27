<script lang="ts">
  import { goto } from '$app/navigation';
  import { page } from '$app/state';
  import { untrack } from 'svelte';
  import { ApiBadge, Button, Callout, Chip, Choice, CodeBlock, EmptyState, Field, Icon, Input, MethodTag, Select, Switch } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import { api } from '#lib/api.js';
  import {
    CI_FORMATS,
    CI_STEPS,
    TEST_RUNNERS,
    buildCiSnippet,
    ciDiffLegend,
    diffCiLines,
    parseTimeout,
    recommendedRunner,
    summarizeSelection,
    targetFor,
    timeoutHelp,
    validateTestForm,
    type CiFormat,
    type CiHeader
  } from '#lib/testing.js';
  import type { Service } from '#lib/types.js';

  interface HeaderRow extends CiHeader {
    id: number;
  }

  let { data } = $props();

  const services = $derived<Service[]>(data.services);
  const serviceParam = $derived(page.url.searchParams.get('service'));
  const service = $derived(services.find((s) => s.id === serviceParam) ?? services[0]);
  const target = $derived(service ? targetFor(service) : undefined);
  const serviceLabel = $derived(service ? `${service.name} ${service.version}`.trim() : 'Service');

  const uid = $props.id();

  let endpoint = $state('');
  let runner = $state('');
  let timeout = $state('10000');
  let secret = $state('');
  let ops = $state<string[]>([]);
  let oauth = $state(false);
  let tokenUri = $state('');
  let clientId = $state('');
  let clientSecret = $state('');
  let scopes = $state('');
  let headers = $state<HeaderRow[]>([]);
  let nextId = 1;
  let ciTab = $state<CiFormat>('cli');
  let secretTouched = $state(false);
  let endpointTouched = $state(false);
  let busy = $state(false);
  let submitError = $state('');
  let launched = $state<{ number: number; serviceLabel: string }>();

  // The form restarts from the service defaults whenever another service is picked.
  $effect.pre(() => {
    const t = target;
    untrack(() => {
      endpoint = '';
      runner = t ? recommendedRunner(t) : '';
      timeout = '10000';
      secret = '';
      ops = t?.type === 'EVENT' ? [t.operations[0]?.id ?? ''].filter(Boolean) : (t?.operations.map((o) => o.id) ?? []);
      oauth = false;
      tokenUri = '';
      clientId = '';
      clientSecret = '';
      scopes = '';
      headers = [];
      secretTouched = false;
      endpointTouched = false;
      submitError = '';
      launched = undefined;
    });
  });

  const isEvent = $derived(target?.type === 'EVENT');
  const allSelected = $derived(target ? ops.length === target.operations.length : false);
  const errors = $derived(
    target
      ? validateTestForm(target, { endpoint, runner, timeout, selectedOperations: ops, oauth, clientSecret })
      : {}
  );
  const blockReason = $derived(
    errors.endpoint ??
      errors.runner ??
      (errors.operations ? 'Select at least one operation.' : undefined) ??
      (errors.timeout ? 'Fix the timeout.' : undefined) ??
      (errors.clientSecret ? 'OAuth2 client secret is required.' : undefined)
  );
  const waitSec = $derived(Math.round((parseTimeout(timeout) ?? 10_000) / 1000));

  const lines = $derived.by(() => {
    if (!service || !target) return [];
    const input = {
      apiRef: `${service.name}:${service.version}`,
      apiName: service.name,
      endpoint,
      runner,
      waitSec,
      secret,
      allOperations: allSelected,
      selectedOperations: target.operations.filter((o) => ops.includes(o.id)).map((o) => o.name),
      headers: headers.map(({ scope, name, values }) => ({ scope, name, values })),
      isEvent
    };
    const defaults = { ...input, endpoint: '', runner: recommendedRunner(target), waitSec: 10, secret: '', allOperations: true, headers: [] };
    return diffCiLines(buildCiSnippet(input, ciTab), buildCiSnippet(defaults, ciTab));
  });
  const changed = $derived(lines.filter((l) => l.changed).length);

  let endpointEl = $state<HTMLInputElement | null>(null);
  let timeoutEl = $state<HTMLInputElement | null>(null);
  let secretEl = $state<HTMLInputElement | null>(null);
  let opsEl = $state<HTMLElement | null>(null);

  function focusFirstError() {
    if (errors.endpoint) endpointEl?.focus();
    else if (errors.operations) opsEl?.querySelector('input')?.focus();
    else if (errors.timeout) timeoutEl?.focus();
    else if (errors.clientSecret) secretEl?.focus();
  }

  async function submit(e: SubmitEvent) {
    e.preventDefault();
    submitError = '';
    if (blockReason || !service || !target) {
      secretTouched = true;
      endpointTouched = true;
      focusFirstError();
      return;
    }
    busy = true;
    try {
      const run = await api<{ id: string; number: number }>('/api/tests', {
        method: 'POST',
        json: {
          serviceId: service.id,
          endpoint: endpoint.trim(),
          runner,
          timeoutMs: parseTimeout(timeout) ?? 10_000,
          secretName: secret || undefined,
          operations: ops,
          headers: headers.filter((h) => h.name.trim()).map((h) => ({ scope: h.scope, name: h.name.trim(), value: h.values }))
        }
      });
      launched = { number: run.number, serviceLabel };
      await goto(`/tests/runner/${encodeURIComponent(run.id)}`);
    } catch (err) {
      submitError = err instanceof Error ? err.message : 'The test could not be launched.';
    } finally {
      busy = false;
    }
  }

  const pickService = (id: string) => goto(`/tests/create?service=${encodeURIComponent(id)}`, { replaceState: true });
  const setOps = (id: string, on: boolean) => (ops = on ? [...ops, id] : ops.filter((x) => x !== id));
  const addHeader = (h: Partial<HeaderRow> = {}) => (headers = [...headers, { id: nextId++, scope: 'globals', name: '', values: '', ...h }]);
  const samplesText = (n: number) => `${n} ${n === 1 ? 'operation' : 'operations'}`;
</script>

<div data-main-pad="roomy">
  <PageHeader
    title="Run a test"
    breadcrumb={[
      { label: 'APIs & Services', href: '/services' },
      { label: serviceLabel, href: '/services' },
      { label: 'New test' }
    ]}
    description="Replay the contract's examples against a deployed implementation and check that it conforms. Every choice below is mirrored, line by line, in the CI snippet on the right."
    descriptionTone="secondary"
  >
    {#snippet actions()}
      {#if target}<ApiBadge label="last test" value="none" status="neutral" />{/if}
    {/snippet}
  </PageHeader>

  {#if !service || !target}
    <EmptyState title="No service to test" headingLevel={2} icon="search-x">
      <p>Import an API contract first: a test replays the examples of a service against its implementation.</p>
      {#snippet actions()}<Button variant="primary" href="/sources/new">Add a source</Button>{/snippet}
    </EmptyState>
  {:else}
    <div class="tc-layout">
      <form class="tc-form" aria-label="Test configuration" onsubmit={submit} novalidate>
        <fieldset class="tc-section">
          <legend><span class="tc-step">01</span>Target</legend>
          <Field label="Service under test">
            {#snippet children(control)}
              <div class="tc-service-row">
                <span class="tc-svc-picker">
                  <ApiBadge protocol={service.protocol} name={service.name} version={service.version} />
                  <span class="tc-svc-meta">{samplesText(target.operations.length)}</span>
                  <Icon name="chevron-down" size={14} />
                  <!-- The native select carries the keyboard and screen reader behaviour; the styled box above is its face. -->
                  <select id={control.id} aria-describedby="{uid}-contract" class="tc-svc-select" value={service.id} onchange={(e) => pickService(e.currentTarget.value)}>
                    {#each services as s (s.id)}
                      <option value={s.id}>{s.name} {s.version} · {s.operations.length} ops</option>
                    {/each}
                  </select>
                </span>
                <span class="tc-contract" id="{uid}-contract">Contract: <span class="tc-contract-value">{target.artifacts}</span></span>
              </div>
            {/snippet}
          </Field>
          <div class="tc-endpoint-block">
            <Field label="Test endpoint" required help={target.endpointHelp} error={endpointTouched ? errors.endpoint : undefined} class="tc-endpoint">
              {#snippet children(control)}
                <Input {...control} bind:ref={endpointEl} mono spellcheck={false} autocomplete="off" placeholder={target.endpointPlaceholder} onblur={() => (endpointTouched = true)} bind:value={endpoint} />
              {/snippet}
            </Field>
          </div>
        </fieldset>

        <fieldset class="tc-section" data-gap="12">
          <legend><span class="tc-step">02</span>Test runner<span class="tc-legend-hint">What "conformant" means for this run</span></legend>
          <div role="radiogroup" aria-label="Test runner" class="tc-runner-grid">
            {#each target.runners as r (r.id)}
              <Choice kind="radio" variant="card" name="runner" value={r.id} bind:group={runner} disabled={Boolean(r.unavailableReason)}>
                {#snippet label()}
                  <span class="tc-runner-name"><span class="tc-runner-id">{r.id}</span>{#if r.recommended}<span class="tc-bestfit">best fit</span>{/if}</span>
                {/snippet}
                {#snippet description()}
                  <span class="tc-runner-desc">{TEST_RUNNERS[r.id].description}</span>
                  {#if r.unavailableReason}<span class="tc-reason">{r.unavailableReason}</span>{/if}
                {/snippet}
              </Choice>
            {/each}
          </div>
          {#if errors.runner}
            <p class="error"><Icon name="cross" size={13} /><span><span class="sr-only">Error: </span>{errors.runner}</span></p>
          {/if}
        </fieldset>

        <fieldset class="tc-section">
          <legend><span class="tc-step">03</span>Scope</legend>
          <div class="tc-scope">
            <div class="tc-ops" bind:this={opsEl} role={isEvent ? 'radiogroup' : 'group'} aria-labelledby="{uid}-ops-label" aria-describedby={errors.operations ? `${uid}-ops-error` : undefined}>
              <div class="tc-ops-head">
                <span id="{uid}-ops-label" class="tc-ops-label">{isEvent ? 'Operation to listen to' : `Operations · ${ops.length} of ${target.operations.length}`}</span>
                {#if !isEvent}
                  <Button variant="link" size="sm" class="tc-ops-toggle" onclick={() => (ops = allSelected ? [] : target.operations.map((o) => o.id))}>{allSelected ? 'Clear all' : 'Select all'}</Button>
                {/if}
              </div>
              {#each target.operations as o (o.id)}
                {#snippet opLabel()}
                  <span class="tc-op-label"><MethodTag method={o.verb} protocol={service.protocol} /><span class="tc-op-path">{o.path}</span></span>
                {/snippet}
                {#if isEvent}
                  <Choice kind="radio" variant="card" class="tc-op" name="operation" value={o.id} group={ops[0]} onchange={() => (ops = [o.id])} label={opLabel} />
                {:else}
                  <Choice variant="card" class="tc-op" checked={ops.includes(o.id)} onchange={(e) => setOps(o.id, (e.currentTarget as HTMLInputElement).checked)} label={opLabel} />
                {/if}
              {/each}
              {#if target.operations.length === 0}<p class="tc-empty">This service has no operation to test.</p>{/if}
              {#if errors.operations}
                <p class="error" id="{uid}-ops-error"><Icon name="cross" size={13} /><span><span class="sr-only">Error: </span>{errors.operations}</span></p>
              {/if}
            </div>
            <Field label="Timeout" help={timeoutHelp(timeout, isEvent)} error={errors.timeout} class="tc-timeout-field">
              {#snippet children(control)}
                <div class="tc-timeout">
                  <Input {...control} bind:ref={timeoutEl} type="number" mono min={1000} max={30000} step={1000} bind:value={timeout} />
                  <span class="tc-unit" aria-hidden="true">ms</span>
                </div>
              {/snippet}
            </Field>
          </div>
        </fieldset>

        <fieldset class="tc-section">
          <legend><span class="tc-step">04</span>Authentication<span class="tc-legend-hint">Optional · to reach a secured endpoint</span></legend>
          <div class="tc-auth">
            <Field label="Secret" help="Credentials or CA certificate managed in Administration › Secrets. No secret store in this UI yet.">
              {#snippet children(control)}
                <Select id={control.id} aria-describedby={control['aria-describedby']} class="tc-mono-select" bind:value={secret} disabled>
                  <option value="">None</option>
                </Select>
              {/snippet}
            </Field>
            <div class="tc-oauth-toggle" role="group" aria-labelledby="{uid}-oauth-label">
              <span id="{uid}-oauth-label" class="label">OAuth2</span>
              <Switch bind:checked={oauth}>
                {#snippet label()}Client credentials grant{/snippet}
              </Switch>
              <p class="help" id="{uid}-oauth-help">Microcks fetches a token before each request. <span class="demo-mono">openid</span> is always included in scopes.</p>
            </div>
          </div>
          {#if oauth}
            <div class="tc-oauth">
              <div class="tc-wide">
                <Field label="Token URI">
                  {#snippet children(control)}<Input {...control} mono class="tc-link-input" spellcheck={false} placeholder="https://auth.example.com/realms/acme/protocol/openid-connect/token" bind:value={tokenUri} />{/snippet}
                </Field>
              </div>
              <Field label="Client Id">
                {#snippet children(control)}<Input {...control} mono spellcheck={false} bind:value={clientId} />{/snippet}
              </Field>
              <Field label="Client Secret" required error={secretTouched ? errors.clientSecret : undefined}>
                {#snippet children(control)}
                  <Input {...control} aria-invalid={errors.clientSecret ? true : undefined} onblur={() => (secretTouched = true)} bind:ref={secretEl} type="password" mono placeholder="Required" autocomplete="off" bind:value={clientSecret} />
                {/snippet}
              </Field>
              <div class="tc-wide">
                <Field label="Scopes">
                  {#snippet children(control)}<Input {...control} mono spellcheck={false} placeholder="space-separated" bind:value={scopes} />{/snippet}
                </Field>
              </div>
            </div>
          {/if}
        </fieldset>

        {#if !isEvent}
          <fieldset class="tc-section" data-gap="12">
            <legend><span class="tc-step">05</span>Request headers<span class="tc-legend-hint">Optional · added to or overriding the examples' headers</span></legend>
            {#if headers.length === 0}<p class="tc-empty">No extra header. Use a comma-separated list for several values of the same header.</p>{/if}
            {#if headers.length > 0}
              <div class="tc-headers">
                <div class="tc-header-row tc-header-head" aria-hidden="true"><span>Applies to</span><span>Name</span><span>Values</span><span></span></div>
                {#each headers as h, i (h.id)}
                  <div class="tc-header-row">
                    <Select aria-label="Header {i + 1} applies to" class="tc-mono-select" bind:value={h.scope}>
                      <option value="globals">All operations</option>
                      {#each target.operations as o (o.id)}<option value={o.name}>{o.name}</option>{/each}
                    </Select>
                    <Input aria-label="Header {i + 1} name" mono spellcheck={false} placeholder="x-api-key" bind:value={h.name} />
                    <Input aria-label="Header {i + 1} values" mono spellcheck={false} placeholder="value1, value2" bind:value={h.values} />
                    <Button iconOnly icon="cross" size="sm" aria-label="Remove header {h.name || i + 1}" onclick={() => (headers = headers.filter((x) => x.id !== h.id))} />
                  </div>
                {/each}
              </div>
            {/if}
            <div class="tc-header-actions">
              <Button variant="dashed" size="compact" icon="plus" onclick={() => addHeader()}>Add header</Button>
              {#if headers.length === 0}
                <Button variant="outline" size="compact" class="tc-apikey" onclick={() => addHeader({ name: 'x-api-key' })}>+ x-api-key (globals)</Button>
              {/if}
            </div>
          </fieldset>
        {/if}

        {#if submitError}
          <div class="tc-submit-error">
            <Callout tone="danger" live="assertive" title="The test could not be launched">{submitError}</Callout>
          </div>
        {/if}
        {#if launched}
          <div class="tc-submit-error">
            <Callout tone="success" live="polite" title="Test #{launched.number} recorded for {launched.serviceLabel}">
              The configuration is valid and saved, but no test engine is connected to this Web UI yet, so nothing was run. Use the CI snippet on the right to run the same test with the microcks CLI.
            </Callout>
          </div>
        {/if}

        <div class="tc-actions">
          <span class="tc-summary">{summarizeSelection(ops.length, runner, waitSec)}</span>
          <div class="tc-actions-buttons">
            <Button variant="outline" size="lg" href="/services">Cancel</Button>
            <Button type="submit" variant="primary" size="lg" icon="play" loading={busy} aria-disabled={blockReason ? true : undefined} aria-describedby={blockReason ? `${uid}-block` : undefined} title={blockReason}>Launch test</Button>
            {#if blockReason}<span id="{uid}-block" class="sr-only">{blockReason}</span>{/if}
          </div>
        </div>
      </form>

      <aside class="tc-ci" aria-labelledby="{uid}-ci-title">
        <div class="tc-ci-head">
          <h2 id="{uid}-ci-title" class="tc-ci-title">Run it from CI</h2>
          <Chip size="sm" variant="outline"><Icon name="refresh-arrows" size={12} /> synced with the form</Chip>
        </div>
        <p class="tc-ci-intro">The same test, as code. Once it passes here, paste it in your pipeline so every build proves the contract.</p>
        <CodeBlock class="tc-ci-code" tabsLabel="CI format" tabs={CI_FORMATS} tab={ciTab} onTabChange={(id) => (ciTab = id as CiFormat)} lines={lines.map((l) => ({ text: l.text || ' ', changed: l.changed }))} />
        <p class="tc-ci-legend" role="status">
          <span class="tc-ci-swatch" aria-hidden="true"></span>
          <span>{ciDiffLegend(changed, serviceLabel)}</span>
        </p>
        {#if oauth}
          <div class="tc-callout-outline" role="note">
            <Icon name="warning" size={14} />
            <span>OAuth2 settings only apply to this run from the UI: the CLI, the GitHub Action and the Tekton task shown here do not carry them.</span>
          </div>
        {/if}
        <div class="tc-ci-steps">
          <h3>Before you paste</h3>
          <ol>
            {#each CI_STEPS[ciTab] as step, i (step)}
              <li><span class="tc-ci-step-n" aria-hidden="true">{i + 1}</span><span>{step}</span></li>
            {/each}
          </ol>
        </div>
      </aside>
    </div>
  {/if}
</div>
