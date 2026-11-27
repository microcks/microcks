<script lang="ts">
  import { invalidate } from '$app/navigation';
  import { ApiBadge, Button, Icon, MethodTag, Segmented, Switch, SummaryStrip, SummaryStripSep } from '@microcks/deep-ink';
  import CatalogPageHeader from '#lib/components/catalog/CatalogPageHeader.svelte';
  import DispatcherPanel from '#lib/components/catalog/DispatcherPanel.svelte';
  import NotFound from '#lib/components/catalog/NotFound.svelte';
  import Panel from '#lib/components/catalog/Panel.svelte';
  import SimulatorPanel from '#lib/components/catalog/SimulatorPanel.svelte';
  import SplitPane from '#lib/components/catalog/SplitPane.svelte';
  import { isValidRegExp, scriptSample, storedSummary } from '#lib/components/catalog/dispatch.js';
  import type { Behavior, ParameterConstraint } from '#lib/components/catalog/types.js';

  let { data } = $props();

  const DELAY_PRESETS = [0, 100, 250, 500, 1000];
  const svc = $derived(data.detail?.service);
  const op = $derived(data.detail?.operations.find((o) => o.id === data.operationId));
  const saved = $derived(op?.behavior);
  let edits = $state<{ key: string; value: Behavior } | undefined>();
  let justSaved = $state<{ key: string; value: Behavior } | undefined>();
  let savedAt = $state(false);
  let switched = $state(false);
  let error = $state('');
  let status = $state('');
  const key = $derived(`${data.detail?.service.id}/${data.operationId}`);
  const current = $derived(justSaved?.key === key ? justSaved.value : saved);
  const draft = $derived<Behavior | undefined>(edits?.key === key ? edits.value : current);
  const dirty = $derived(Boolean(draft && current && JSON.stringify(draft) !== JSON.stringify(current)));
  const pathParams = $derived([...(op?.path.matchAll(/\{([^}]+)\}/g) ?? [])].map((m) => m[1]));
  const inferred = $derived(current?.dispatcher === 'URI_PARTS' && current.rule === pathParams.join(' && '));
  const headerCount = $derived(draft?.constraints.filter((c) => c.in === 'header').length ?? 0);

  function edit(patch: Partial<Behavior>) {
    if (!draft) return;
    edits = { key, value: { ...draft, ...patch } };
    savedAt = false;
  }
  const setConstraint = (i: number, patch: Partial<ParameterConstraint>) =>
    edit({ constraints: draft!.constraints.map((c, j) => (j === i ? { ...c, ...patch } : c)) });

  function toggleStateful(on: boolean) {
    if (!draft) return;
    if (on) {
      const lang = draft.scriptLanguage ?? 'GROOVY';
      edit({ stateful: true, dispatcher: 'SCRIPT', scriptLanguage: lang, script: scriptSample(lang, true) });
      switched = true;
    } else {
      edit({ stateful: false, script: scriptSample(draft.scriptLanguage ?? 'GROOVY', false) });
      switched = false;
    }
  }

  async function save() {
    if (!draft || !svc || !op) return;
    error = '';
    const res = await fetch(`/api/services/${svc.id}/operations/${op.id}/behavior`, {
      method: 'PUT',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(draft)
    });
    if (!res.ok) {
      error = ((await res.json().catch(() => ({}))) as { message?: string }).message ?? `Save failed (${res.status}).`;
      return;
    }
    justSaved = { key, value: draft };
    savedAt = true;
    status = 'Behavior saved.';
    await invalidate('catalog:service');
  }
</script>

{#if !svc || !op || !draft}
  <NotFound status={data.status === 200 ? 404 : data.status} what="The operation" />
{:else}
  <div class="behavior-page">
    <div class="behavior-head">
      <CatalogPageHeader
        titleText={op.path}
        variant="mono"
        align="start"
        breadcrumb={[{ label: 'APIs & Services', href: '/services' }, { label: `${svc.name} ${svc.version}`, href: `/services/${svc.id}` }, { label: `${op.method} ${op.path}` }]}
        description="Mock behavior: how Microcks picks a response for each incoming call, how long it waits, which parameters it checks and whether it keeps state. Try every change in the simulator before saving."
        descriptionTone="secondary"
      >
        {#snippet leading()}<MethodTag method={op.method} protocol={svc.protocol} />{/snippet}
        {#snippet badge()}<a href={`/services/${svc.id}`} class="behavior-api-link"><ApiBadge protocol={svc.protocol} name={svc.name} version={svc.version} size="sm" /></a>{/snippet}
        {#snippet actions()}
          {#if dirty}<span class="behavior-unsaved"><Icon name="alert-circle" size={14} />Unsaved changes</span>{/if}
          <Button variant="ghost" onclick={() => { edits = undefined; justSaved = undefined; switched = false; }}>Reset</Button>
          <Button variant="secondary" href={`/services/${svc.id}`}>Cancel</Button>
          <Button variant="primary" icon="check" onclick={save}>Save behavior</Button>
        {/snippet}
      </CatalogPageHeader>
      {#if error}<p class="rules-error" role="alert"><Icon name="cross" size={13} />{error}</p>{/if}
      <SummaryStrip role="note" aria-label="Saved behavior">
        <span class="section-label">Saved</span>
        <span>{current ? storedSummary(current) : ''}</span>
        <SummaryStripSep />
        <span>delay {current?.delayMs} ms {current?.delayStrategy ?? 'fixed'}</span>
        <SummaryStripSep />
        <span>{current?.constraints.length} constraints</span>
        <SummaryStripSep />
        <span class="section-label">{op.examples.length} examples{inferred ? ' · dispatcher inferred from the OpenAPI path parameter' : ''}</span>
        {#if savedAt}<span role="status" class="behavior-saved"><Icon name="check" size={14} />Behavior saved</span>{/if}
      </SummaryStrip>
    </div>

    <SplitPane class="behavior-layout" sideWidth="360px" sideMax="480px" mainWidth="600px" keepSideMax mainClass="behavior-config">
      {#snippet side()}
        <SimulatorPanel {op} protocol={svc.protocol} mockBase={data.detail!.mockBase} {draft} />
      {/snippet}

      <DispatcherPanel {draft} {op} examples={op.examples} {pathParams} pathPrefix={op.path.split('{')[0] || '/'} {edit} />

      <Panel padding="lg" title="Delay" class="delay">
        <p class="panel-lead">Simulates the dependency's response time. Applies to every call unless the caller overrides it.</p>
        <div class="delay-row">
          <label for="delay-range" class="field-inline-label">Default delay</label>
          <input id="delay-range" class="delay-range" type="range" min="0" max="2000" step="50" value={draft.delayMs} oninput={(e) => edit({ delayMs: Number(e.currentTarget.value) })} />
          <span class="delay-ms">
            <input class="delay-number" type="number" min="0" max="60000" step="50" aria-label="Default delay in milliseconds" value={draft.delayMs} oninput={(e) => edit({ delayMs: Math.max(0, parseInt(e.currentTarget.value, 10) || 0) })} />
            <span aria-hidden="true">ms</span>
          </span>
        </div>
        <div class="delay-presets" role="group" aria-labelledby="delay-presets">
          <span id="delay-presets" class="delay-presets-label">Presets</span>
          {#each DELAY_PRESETS as v (v)}<button type="button" class="mono-chip" aria-pressed={draft.delayMs === v} onclick={() => edit({ delayMs: v })}>{v} ms</button>{/each}
        </div>
        <div class="delay-strategy">
          <span class="field-inline-label" aria-hidden="true">Strategy</span>
          <Segmented variant="joined" size="sm" label="Strategy" value={draft.delayStrategy ?? 'fixed'} onchange={(id) => edit({ delayStrategy: id as 'fixed' | 'random' })} options={[{ id: 'fixed', label: 'fixed' }, { id: 'random', label: 'random' }]} />
          <span class="delay-help">
            {draft.delayMs === 0 ? 'No delay: responses return immediately.' : (draft.delayStrategy ?? 'fixed') === 'fixed' ? `Every response waits exactly ${draft.delayMs} ms.` : `The wait varies on every call, based on ${draft.delayMs} ms.`}
          </span>
        </div>
        <p class="delay-overrides">
          <span>Per endpoint: <code>?delay=500</code></span>
          <span>Per request: <code>x-microcks-delay: 800</code> header, wins over all others</span>
        </p>
      </Panel>

      <Panel padding="lg" title="Parameter constraints" class="constraints">
        {#snippet actions()}<span class="constraints-count">{headerCount} header · {draft.constraints.length - headerCount} query</span>{/snippet}
        <p class="panel-lead">Expectations the contract cannot express. A violated constraint returns 400 before dispatching.</p>
        <div class="constraints-frame">
          <table class="constraints-table" aria-label="Parameter constraints">
            <thead>
              <tr>
                <th scope="col">In</th><th scope="col">Name</th><th scope="col" class="is-center">Required</th><th scope="col" class="is-center">Recopy</th>
                <th scope="col">Must match regexp</th><th scope="col"><span class="sr-only">Remove</span></th>
              </tr>
            </thead>
            <tbody>
              {#each draft.constraints as c, i (i)}
                {@const invalid = Boolean(c.mustMatchRegexp) && !isValidRegExp(c.mustMatchRegexp ?? '')}
                {@const who = c.name || `constraint ${i + 1}`}
                <tr>
                  <td>
                    <select class="cell-control" aria-label={`Location of ${who}`} value={c.in} onchange={(e) => setConstraint(i, { in: e.currentTarget.value as 'header' | 'query' })}>
                      <option value="header">header</option><option value="query">query</option>
                    </select>
                  </td>
                  <td><input class="cell-control" type="text" aria-label={`Name of ${who}`} placeholder="parameter name" value={c.name} oninput={(e) => setConstraint(i, { name: e.currentTarget.value })} /></td>
                  <td class="is-center"><input type="checkbox" class="checkbox" aria-label={`${who} is required`} checked={c.required} onchange={(e) => setConstraint(i, { required: e.currentTarget.checked })} /></td>
                  <td class="is-center"><input type="checkbox" class="checkbox" aria-label={`Recopy ${who}`} checked={c.recopy} onchange={(e) => setConstraint(i, { recopy: e.currentTarget.checked })} /></td>
                  <td>
                    <input class="cell-control cell-regexp" type="text" aria-label={`Regular expression for ${who}`} placeholder="no pattern" aria-invalid={invalid || undefined} aria-describedby={invalid ? `regexp-error-${i}` : undefined} value={c.mustMatchRegexp ?? ''} oninput={(e) => setConstraint(i, { mustMatchRegexp: e.currentTarget.value || undefined })} />
                    {#if invalid}<span id={`regexp-error-${i}`} class="cell-error">Invalid regular expression</span>{/if}
                  </td>
                  <td>
                    <button type="button" class="cell-remove" aria-label={`Remove ${who}`} onclick={() => edit({ constraints: draft.constraints.filter((_, j) => j !== i) })}><Icon name="trash" size={15} /></button>
                  </td>
                </tr>
              {/each}
              {#if draft.constraints.length === 0}<tr><td colspan="6" class="constraints-empty">No constraint: every header and query value is accepted.</td></tr>{/if}
            </tbody>
          </table>
        </div>
        <div class="constraints-foot">
          <Button size="sm" variant="secondary" icon="plus" onclick={() => edit({ constraints: [...draft.constraints, { in: 'header', name: '', required: false, recopy: false }] })}>Add constraint</Button>
          <span class="constraints-hint"><code>recopy</code> echoes the value in a response header, e.g. a correlation id.</span>
        </div>
      </Panel>

      <Panel padding="lg" title="Stateful mock" class="stateful">
        {#snippet actions()}<Switch size="lg" checked={draft.stateful} onchange={toggleStateful}>{#snippet label()}{draft.stateful ? 'On' : 'Off'}{/snippet}</Switch>{/snippet}
        <span class="stateful-desc">Persist state between calls, e.g. count views, keep a cart, reflect a PATCH in later GETs.</span>
        <ul class="stateful-cards">
          <li><span class="stateful-card-title">Runs in a SCRIPT dispatcher</span><span>Turning it on switches the dispatcher to SCRIPT and loads a sample that reads and writes the <code>store</code>.</span></li>
          <li><span class="stateful-card-title">Scoped to {svc.name} {svc.version}</span><span>The key/value store is shared by its {data.detail!.operations.length} operations, never by other APIs.</span></li>
          <li><span class="stateful-card-title">Values expire</span><span>Default time-to-live is 10 s; set another with <code>store.put(key, value, ttl)</code>.</span></li>
        </ul>
        {#if switched && draft.stateful}
          <p role="status" class="stateful-note"><Icon name="info" size={14} />Dispatcher switched to SCRIPT with a view-counter sample.</p>
        {/if}
      </Panel>
    </SplitPane>
    <p class="sr-only" role="status" aria-live="polite">{status}</p>
  </div>
{/if}
