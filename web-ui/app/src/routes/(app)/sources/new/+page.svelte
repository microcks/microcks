<script lang="ts">
  import { invalidate } from '$app/navigation';
  import {
    ApiBadge,
    Button,
    Callout,
    Choice,
    Chip,
    CopyButton,
    DropZone,
    Field,
    Icon,
    Input,
    MethodTag,
    Segmented,
    Select,
    Tabs,
    Textarea
  } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import { api } from '#lib/api.js';
  import { formatRelative, pluralize } from '#lib/format.js';
  import { originLabel, sourceStatus } from '#lib/sources.js';
  import {
    ACCEPTED_EXTENSIONS,
    MICROCKS_BASE,
    DIRECT_DEFAULTS,
    GIT_DEFAULTS,
    RECOGNIZED_FORMATS,
    SAMPLE_URL,
    mockEndpoint,
    or,
    validateArtifactUrl,
    validateDirectApi,
    validateGitForm,
    type DirectKind
  } from '#lib/importing.js';
  import type { Preview, Service, Source, SourceOrigin } from '#lib/types.js';

  type Step = 'idle' | 'analyzing' | 'detected' | 'importing' | 'done';
  type Tab = 'file' | 'url' | 'git' | 'direct';
  interface Result {
    /** Absent for a one-off upload, which creates no importer. */
    source?: Source;
    service: Service;
    direct?: DirectKind;
  }

  let { data } = $props();

  const SAMPLES = [
    { label: 'Sample: API Pastry', file: 'APIPastry-openapi.yaml' },
    { label: 'Example: Swagger Petstore', file: 'PetstoreAPI-swagger.json' },
    { label: 'Example: User signed-up (AsyncAPI)', file: 'UserSignedUpAPI-asyncapi.yml' }
  ];
  const samples = $derived(SAMPLES.filter((s) => data.samples.includes(s.file)));
  const servicesById = $derived(new Map<string, Service>(data.services.map((s: Service) => [s.id, s])));

  let step = $state<Step>('idle');
  let tab = $state<Tab>('file');
  let preview = $state<Preview>();
  let gitName = $state('');
  let insecure = $state(false);
  let secret = $state('');
  let result = $state<Result>();
  let role = $state<'primary' | 'secondary'>('primary');
  let error = $state<string>();
  let touched = $state(false);
  let announcement = $state('');
  let url = $state('');
  let git = $state({ name: '', url: '' });
  let direct = $state({ kind: 'rest' as DirectKind, name: '', version: '', resource: '', payload: '' });

  const resolvedUrl = $derived(or(url, SAMPLE_URL));
  const resolvedGit = $derived({ name: or(git.name, GIT_DEFAULTS.name), url: or(git.url, GIT_DEFAULTS.url) });
  const resolvedDirect = $derived({
    kind: direct.kind,
    name: or(direct.name, DIRECT_DEFAULTS.name),
    version: or(direct.version, DIRECT_DEFAULTS.version),
    resource: or(direct.resource, DIRECT_DEFAULTS.resource),
    payload: direct.kind === 'event' ? or(direct.payload, DIRECT_DEFAULTS.payload) : direct.payload.trim()
  });
  const urlError = $derived(touched && tab === 'url' ? validateArtifactUrl(resolvedUrl) : undefined);
  const gitErrors = $derived(touched && tab === 'git' ? validateGitForm(resolvedGit) : {});
  const directErrors = $derived(touched && tab === 'direct' ? validateDirectApi(resolvedDirect) : {});

  const stepIndex = $derived(step === 'idle' || step === 'analyzing' ? 0 : step === 'done' ? 2 : 1);
  const directDone = $derived(step === 'done' && result?.direct !== undefined);
  const steps = $derived(['Source', directDone ? 'Preview (no contract)' : 'Preview', 'Live mock']);
  const recent = $derived(data.sources.slice(0, 3));

  const secondaryOnly = $derived(preview?.artifact.role === 'SECONDARY');

  async function analyze(origin: SourceOrigin, body: { url: string } | { fileName: string; content: string }, name?: string) {
    const withSecret = 'url' in body && secret ? { secret } : {};
    error = undefined;
    step = 'analyzing';
    announcement = 'Analyzing the artifact…';
    try {
      const p = await api<Preview>('/api/analyze', { method: 'POST', json: { origin, ...body, ...withSecret } });
      if (p.artifact.kind === 'unknown') throw new Error(`${p.artifact.fileName} is not a recognised contract. ${p.artifact.warnings[0] ?? ''}`.trim());
      preview = p;
      gitName = name ?? '';
      role = p.artifact.role === 'SECONDARY' ? 'secondary' : 'primary';
      step = 'detected';
      announcement = 'Preview ready. Nothing has been created yet.';
    } catch (e) {
      step = 'idle';
      error = e instanceof Error ? e.message : 'The artifact could not be analyzed.';
    }
  }

  async function onFiles(files: File[]) {
    const file = files[0];
    if (!file) return;
    await analyze('upload', { fileName: file.name, content: await file.text() });
  }

  async function useSample(file: string) {
    const res = await fetch(`/api/samples/${file}`);
    await analyze('upload', { fileName: file, content: await res.text() });
  }

  function submitUrl(e: SubmitEvent) {
    e.preventDefault();
    touched = true;
    if (!validateArtifactUrl(resolvedUrl)) void analyze('url', { url: resolvedUrl });
  }

  function submitGit(e: SubmitEvent) {
    e.preventDefault();
    touched = true;
    if (!Object.keys(validateGitForm(resolvedGit)).length) void analyze('git', { url: resolvedGit.url }, resolvedGit.name);
  }

  async function doImport() {
    if (!preview) return;
    step = 'importing';
    announcement = 'Importing…';
    try {
      const res = await api<{ source?: Source; service?: Service }>('/api/sources', {
        method: 'POST',
        json: {
          origin: preview.origin,
          fileName: preview.artifact.fileName,
          name: gitName || undefined,
          content: preview.content,
          url: preview.url || undefined,
          role: role === 'primary' ? 'PRIMARY' : 'SECONDARY',
          secret: secret || undefined,
          tlsValidation: !insecure
        }
      });
      if (!res.service) throw new Error(res.source?.error ?? 'No matching API: a secondary artifact only enriches an existing Service.');
      result = { source: res.source, service: res.service };
      step = 'done';
      announcement = res.source ? 'Source created. The mock is live.' : 'Contract imported. The mock is live.';
      await invalidate('app:sources');
    } catch (e) {
      step = 'detected';
      error = e instanceof Error ? e.message : 'The import failed. Try again.';
    }
  }

  async function createDirect(e: SubmitEvent) {
    e.preventDefault();
    touched = true;
    if (Object.keys(validateDirectApi(resolvedDirect)).length) return;
    step = 'importing';
    error = undefined;
    try {
      const res = await api<{ source: Source; service: Service }>('/api/direct', { method: 'POST', json: resolvedDirect });
      result = { ...res, direct: resolvedDirect.kind };
      preview = undefined;
      step = 'done';
      announcement = 'Mock created and live.';
      await invalidate('app:sources');
    } catch (err) {
      step = 'idle';
      error = err instanceof Error ? err.message : 'The mock could not be created.';
    }
  }

  function reset() {
    step = 'idle';
    preview = undefined;
    result = undefined;
    role = 'primary';
    error = undefined;
    touched = false;
    insecure = false;
    secret = '';
    direct = { kind: 'rest', name: '', version: '', resource: '', payload: '' };
  }

  const endpoint = $derived.by(() => {
    if (!result) return undefined;
    const { service, direct: kind } = result;
    if (kind === 'event') {
      const topic = `${service.name.replace(/\s+/g, '')}-${service.version}-${resolvedDirect.resource}`;
      return { label: 'Mock Kafka topic', value: topic, tryIt: `kcat -b microcks-kafka:9092 -C -t ${topic}` };
    }
    return mockEndpoint({ protocol: service.protocol, mockUrl: service.mockUrl, name: service.name, base: MICROCKS_BASE });
  });

  const doneSub = $derived.by(() => {
    if (!result) return '';
    const { service, source, direct: kind } = result;
    const full = `${service.name} ${service.version}`;
    if (kind === 'event') return `${full} publishes the reference payload of the “${resolvedDirect.resource}” resource at a regular interval.`;
    if (kind) return `${full} exposes the “${resolvedDirect.resource}” resource: create, read, filter and delete with free-form JSON.`;
    if (!source) {
      return role === 'secondary'
        ? `Secondary artifact: non-conflicting examples were added to ${full}.`
        : `${full} is imported: ${pluralize(service.operations.length, 'Operation')} served by the mock. One-off upload: no source was created.`;
    }
    if (source.role === 'SECONDARY') return `Secondary artifact: non-conflicting examples were added to ${full} (name + version key).`;
    const scan = source.origin === 'upload' ? '' : ' It is re-scanned on the global schedule.';
    return `${full} is imported: ${pluralize(service.operations.length, 'Operation')} served by the mock.${scan}`;
  });

  const recentWhen = (s: Source) => formatRelative(s.lastImportAt ?? s.createdAt);
</script>

<div class="import" data-main-pad="roomy">
  <PageHeader
    title="Add a source"
    variant="plain"
    descriptionTone="secondary"
    breadcrumb={[{ label: 'Sources', href: '/sources' }, { label: 'Add a source' }]}
    description="Upload a file once, or point Microcks to a URL or a Git repository it keeps in sync. You see what it understood before anything is created."
  >
    {#snippet actions()}
      <ol class="import-stepper" aria-label="Progress">
        {#each steps as label, i (label)}
          {@const done = i < stepIndex || (step === 'done' && i === 2)}
          <li data-state={done ? 'done' : i === stepIndex ? 'current' : 'upcoming'} aria-current={i === stepIndex ? 'step' : undefined}>
            <span class="import-stepper-dot" aria-hidden="true">{i + 1}</span>
            {label}
            {#if done}<span class="sr-only"> (done)</span>{/if}
          </li>
        {/each}
      </ol>
    {/snippet}
  </PageHeader>

  <p class="sr-only" role="status" aria-live="polite">{announcement}</p>
  {#if error}
    <Callout tone="danger" live="assertive" title="Something went wrong">{error}</Callout>
  {/if}

  <div class="import-layout">
    <section aria-label="Add a source" class="import-workspace" aria-busy={step === 'analyzing' || step === 'importing'}>
      {#if step === 'idle' || step === 'analyzing'}
        <div class="source-box">
          <Tabs
            label="Source type"
            value={tab}
            onchange={(id) => {
              tab = id as Tab;
              touched = false;
              error = undefined;
            }}
            items={[
              { id: 'file', label: 'Upload a file', icon: 'document', hint: 'one-off' },
              { id: 'url', label: 'URL', icon: 'link' },
              { id: 'git', label: 'Git repository', icon: 'git-branch', hint: 'scheduled' },
              { id: 'direct', label: 'Direct API', icon: 'zap' }
            ]}
          >
            <div class="source-panel">
              {#if tab === 'file'}
                <DropZone heading="Drop a contract here" chooseLabel="Browse" multiple={false} accept={ACCEPTED_EXTENSIONS} {onFiles}>
                  {#snippet hint()}One file, anywhere on the page. Formats .yaml, .json, .xml, .wsdl, .proto, .graphql, .har{/snippet}
                </DropZone>
                <div class="source-samples">
                  <span>No contract at hand?</span>
                  {#each samples as s (s.file)}
                    <Button variant="link" icon="arrow-right" onclick={() => useSample(s.file)}>{s.label}</Button>
                  {/each}
                </div>
                <p class="source-help">One-off upload: relative references are only resolved if a default artifact repository is configured. To follow a contract as it evolves, prefer a Git repository.</p>
              {:else if tab === 'url'}
                <form class="source-form" onsubmit={submitUrl} novalidate>
                  <Field label="Artifact URL" help="Microcks downloads the file now, then fetches it again on the global schedule to keep the mock in sync." error={urlError}>
                    {#snippet children(c)}<Input {...c} mono type="url" placeholder={SAMPLE_URL} bind:value={url} />{/snippet}
                  </Field>
                  <div class="source-row">
                    <Field label="Authentication Secret" help={data.secretNames.length ? 'Secrets are managed in Administration.' : 'No secrets are configured yet.'}>
                      {#snippet children(c)}
                        <Select {...c} bind:value={secret}>
                          <option value="">None (public resource)</option>
                          {#each data.secretNames as n (n)}<option value={n}>{n}</option>{/each}
                        </Select>
                      {/snippet}
                    </Field>
                    <Choice bind:checked={insecure}>{#snippet label()}Disable SSL validation{/snippet}</Choice>
                  </div>
                  <div class="source-actions"><Button type="submit" variant="primary" loading={step === 'analyzing'}>Analyze URL</Button></div>
                </form>
              {:else if tab === 'git'}
                <form class="source-form" onsubmit={submitGit} novalidate>
                  <div class="source-row">
                    <Field label="Source name" error={gitErrors.name}>
                      {#snippet children(c)}<Input {...c} placeholder={GIT_DEFAULTS.name} bind:value={git.name} />{/snippet}
                    </Field>
                    <div data-grow="2">
                      <Field label="File URL in the repository" error={gitErrors.url}>
                        {#snippet children(c)}<Input {...c} mono type="url" placeholder={GIT_DEFAULTS.url} bind:value={git.url} />{/snippet}
                      </Field>
                    </div>
                  </div>
                  <div class="source-row">
                    <Field label="Authentication Secret" help={data.secretNames.length ? 'Secrets are managed in Administration.' : 'No secrets are configured yet.'}>
                      {#snippet children(c)}
                        <Select {...c} bind:value={secret}>
                          <option value="">None (public repository)</option>
                          {#each data.secretNames as n (n)}<option value={n}>{n}</option>{/each}
                        </Select>
                      {/snippet}
                    </Field>
                    <Choice bind:checked={insecure}>{#snippet label()}Disable SSL validation{/snippet}</Choice>
                  </div>
                  <div class="note-box">
                    <Icon name="clock" size={16} />
                    <span>Scheduled source: Microcks re-scans the file on the global <span class="mono">{data.schedule.envVar}</span> (every hour by default) and resolves external references. <a href="/sources">Manage sources</a></span>
                  </div>
                  <div class="source-actions"><Button type="submit" variant="primary" loading={step === 'analyzing'}>Analyze repository</Button></div>
                </form>
              {:else}
                <form class="source-form" onsubmit={createDirect} novalidate>
                  <p class="source-lead">No contract yet? Describe a resource: Microcks generates the artifact and serves a mock that accepts any JSON.</p>
                  <div role="group" aria-label="Direct API type" class="type-cards">
                    <button type="button" class="type-card" aria-pressed={direct.kind === 'rest'} onclick={() => (direct.kind = 'rest')}>
                      <span class="type-card-tag" data-protocol="direct">REST</span>
                      <span class="type-card-title">Generic REST API</span>
                      <span class="type-card-sub">CRUD on a resource, dynamic storage</span>
                    </button>
                    <button type="button" class="type-card" aria-pressed={direct.kind === 'event'} onclick={() => (direct.kind = 'event')}>
                      <span class="type-card-tag" data-protocol="event">EVENT</span>
                      <span class="type-card-title">Generic event-driven API</span>
                      <span class="type-card-sub">Publishes a reference payload on a topic</span>
                    </button>
                  </div>
                  <div class="source-row">
                    <div data-grow="2">
                      <Field label="API name" error={directErrors.name}>
                        {#snippet children(c)}<Input {...c} placeholder={DIRECT_DEFAULTS.name} bind:value={direct.name} />{/snippet}
                      </Field>
                    </div>
                    <div data-basis="version">
                      <Field label="Version" error={directErrors.version}>
                        {#snippet children(c)}<Input {...c} mono placeholder={DIRECT_DEFAULTS.version} bind:value={direct.version} />{/snippet}
                      </Field>
                    </div>
                    <div data-basis="resource">
                      <Field label="Resource" error={directErrors.resource}>
                        {#snippet children(c)}<Input {...c} mono placeholder={DIRECT_DEFAULTS.resource} bind:value={direct.resource} />{/snippet}
                      </Field>
                    </div>
                  </div>
                  <Field
                    label={`Reference JSON payload ${direct.kind === 'event' ? '(required, published as is)' : '(optional, used as a template)'}`}
                    error={directErrors.payload}
                  >
                    {#snippet children(c)}<Textarea {...c} mono rows={4} spellcheck={false} placeholder={DIRECT_DEFAULTS.payload} bind:value={direct.payload} />{/snippet}
                  </Field>
                  <div class="source-actions"><Button type="submit" variant="primary">Create mock</Button></div>
                </form>
              {/if}
            </div>
          </Tabs>
          {#if tab !== 'direct'}
            <div class="formats">
              <span class="formats-label">Recognized formats</span>
              {#each RECOGNIZED_FORMATS as f (f)}<Chip>{f}</Chip>{/each}
            </div>
          {/if}
        </div>
      {/if}

      {#if (step === 'detected' || step === 'importing') && preview}
        {@const p = preview}
        {@const svc = p.service}
        <div class="step-card">
          <div class="step-head">
            <span class="step-head-badge"><Icon name="eye" size={18} /></span>
            <div class="step-head-text">
              <h2>Preview before creating the source</h2>
              <p>Nothing has been created yet. Here is what Microcks detected in the artifact.</p>
            </div>
          </div>

          <div class="step-section" style="gap: 20px">
            <div class="artifact-card">
              <span class="artifact-card-icon"><Icon name="document" size={20} /></span>
              <div class="artifact-card-text">
                <span class="artifact-card-name">{p.artifact.fileName}</span>
                <span class="artifact-card-origin">{p.url || (p.origin === 'upload' ? 'Uploaded file' : originLabel(p.origin))}</span>
              </div>
              <div class="artifact-card-badges">
                <Chip><Icon name="check" size={12} /> {p.artifact.format}</Chip>
                <ApiBadge protocol={svc.protocol} name={svc.name} version={svc.version} />
              </div>
            </div>
            {#each p.artifact.warnings as w (w)}
              <div role="note" class="note-box" data-tone="warning"><Icon name="alert-circle" size={16} /><span>{w}</span></div>
            {/each}
            <div class="step-section">
              <h3>{pluralize(p.artifact.operations.length, 'Operation')} discovered{svc.exists ? ` · updates the existing ${svc.name} ${svc.version}` : ''}</h3>
              {#if p.artifact.operations.length}
                <ul class="boxed-list">
                  {#each p.artifact.operations as o (o.method + o.name)}
                    <li>
                      <MethodTag method={o.method} protocol={svc.protocol} />
                      <span class="boxed-list-path">{o.resource ?? o.name}</span>
                      {#if o.resource && !o.name.endsWith(o.resource)}<span class="boxed-list-meta">{o.name}</span>{/if}
                    </li>
                  {/each}
                </ul>
              {:else}
                <p class="source-help">This artifact carries no operation: it only enriches Services that already exist.</p>
              {/if}
            </div>
          </div>

          <div class="step-section">
            <h3 id="role-title">Artifact role</h3>
            <Segmented
              class="role-toggle"
              label="Artifact role"
              variant="joined"
              tone="primary"
              value={role}
              onchange={(id) => (role = id as 'primary' | 'secondary')}
              options={[
                { id: 'primary', label: 'Primary' },
                { id: 'secondary', label: 'Secondary' }
              ]}
            />
            <p style="max-width: 720px">
              {#if role === 'secondary'}
                Secondary: merges only non-conflicting Examples into an existing API, matched by name + version (here {svc.name} {svc.version}). Ignored if no API matches.
              {:else}
                Primary: brings the Service structure, its Operations and its Examples. This is the default; then add secondary Artifacts (Postman, HAR, APIMetadata) to enrich the Examples.
              {/if}
            </p>
          </div>

          {#if p.origin === 'git'}
            <div class="note-box">
              <Icon name="clock" size={16} />
              <span>The source “{gitName}” will be created, scanned and imported immediately, then re-scanned on the global schedule. <a href="/sources">Manage sources</a></span>
            </div>
          {/if}
          {#if secondaryOnly && role === 'primary'}
            <div role="note" class="note-box" data-tone="info"><Icon name="info" size={16} /><span>This file is normally a secondary artifact. Keep it Primary only if it should create its own Service.</span></div>
          {/if}

          <div class="step-actions">
            <Button variant="primary" icon="download" loading={step === 'importing'} onclick={doImport}>{p.origin === 'upload' ? 'Import' : 'Create source'}</Button>
            <Button variant="secondary" onclick={reset}>Cancel</Button>
          </div>
        </div>
      {/if}

      {#if step === 'importing' && !preview}<p role="status">Creating the mock…</p>{/if}

      {#if step === 'done' && result}
        {@const r = result}
        <div class="step-card" data-step="done">
          <div role="status" class="step-head">
            <Icon name="check-circle" size={32} />
            <div class="step-head-text">
              <h2>{r.direct ? 'Mock created and live' : r.source ? 'Source created' : 'Contract imported'}</h2>
              <p>{doneSub}</p>
            </div>
          </div>

          {#if r.source}
          <div class="created-source">
            <Icon name="database" size={16} />
            <span class="created-source-text">
              <span class="created-source-name">{r.source.name}</span>
              <span class="created-source-detail">{r.source.url || r.source.artifact}</span>
            </span>
            <span class="created-source-origin">{originLabel(r.source.origin)}</span>
            <span class="created-source-schedule">{r.source.origin === 'upload' ? 'No schedule · one-off' : "Re-scanned every hour"}</span>
            <a href="/sources/{r.source.id}">View in Sources</a>
          </div>
          {/if}

          <div class="done-badges">
            <ApiBadge protocol={r.service.protocol} name={r.service.name} version={r.service.version} />
            <ApiBadge label="operations" value={String(r.service.operations.length)} status="success" />
          </div>
          {#if endpoint}
            <div class="done-field">
              <span id="mock-url-label" class="done-label">{endpoint.label}</span>
              <div class="mock-url-field" role="group" aria-labelledby="mock-url-label">
                <code>{endpoint.value}</code>
                <CopyButton value={endpoint.value} label="Copy" />
              </div>
            </div>
            {#if endpoint.tryIt}
              <div class="done-field">
                <span id="try-it-label" class="done-label">Try it now</span>
                <pre class="try-it" aria-labelledby="try-it-label">{endpoint.tryIt}</pre>
              </div>
            {/if}
          {/if}

          <div class="step-actions">
            <Button variant="primary" icon="arrow-right" href={`/services?q=${encodeURIComponent(r.service.name)}`}>Open service</Button>
            <Button variant="secondary" icon="play" href={`/tests/create?service=${encodeURIComponent(r.service.id)}`}>Run a test</Button>
            <Button variant="secondary" href="/sources">Back to Sources</Button>
            <Button variant="ghost" onclick={reset}>Add another source</Button>
          </div>
        </div>
      {/if}
    </section>

    <aside aria-labelledby="recent-title" class="recent">
      <div class="recent-head">
        <h2 id="recent-title">Recent sources</h2>
        <a href="/services">Full catalog</a>
      </div>
      <ul class="recent-list">
        {#each recent as r (r.id)}
          {@const svc = servicesById.get(r.serviceIds[0])}
          {@const st = sourceStatus(r)}
          <li>
            <div class="recent-line">
              {#if svc}<ApiBadge protocol={svc.protocol} name={svc.name} size="sm" />{:else}<span>{r.name}</span>{/if}
              <span class="recent-when">{recentWhen(r)}</span>
            </div>
            <div class="recent-line">
              <span class="recent-via">{r.origin === 'upload' ? 'Upload' : originLabel(r.origin)}</span>
              <span class="recent-status" data-status={st === 'warning' || st === 'failing' ? 'warning' : 'ok'}>
                <Icon name={st === 'warning' || st === 'failing' ? 'warning' : 'check-circle'} size={14} />
                {st === 'failing' ? 'Failed' : st === 'warning' ? 'Imported · 1 warning' : 'Imported'}
              </span>
            </div>
            <span class="recent-note">{r.timeline[0]?.summary ?? r.name}</span>
          </li>
        {/each}
      </ul>
      <p class="recent-foot">Every upload, URL and repository is listed in Sources. <a href="/sources">Back to Sources</a></p>
    </aside>
  </div>
</div>
