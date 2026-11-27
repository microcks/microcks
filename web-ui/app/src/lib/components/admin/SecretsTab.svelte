<script lang="ts">
  import { tick } from 'svelte';
  import { Button, EmptyState, Icon } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';
  import { SECRET_KIND_LABEL, plural, type SecretSummary } from '#lib/admin.js';
  import SecretForm from './SecretForm.svelte';

  interface Props {
    secrets: SecretSummary[];
    notify: (message: string) => void;
    onchanged: () => void;
  }
  let { secrets, notify, onchanged }: Props = $props();

  /** `null`: closed, 'new': creating, otherwise the id of the secret being edited. */
  let formFor = $state<'new' | string | null>(null);
  let confirming = $state('');
  let error = $state('');

  const editing = $derived(secrets.find((s) => s.id === formFor) ?? null);

  // Focus after the next render: a removed control must not drop focus to the page body.
  async function focusId(id: string) {
    await tick();
    document.getElementById(id)?.focus();
  }
  /** Keeps each "••••••••" mask on one line. */
  const parts = (text: string) => text.split(/(•+)/).map((t) => ({ t, mask: t.startsWith('•') }));

  async function remove(s: SecretSummary) {
    const at = secrets.findIndex((x) => x.id === s.id);
    const next = secrets[at + 1] ?? secrets[at - 1];
    error = '';
    try {
      await api(`/api/admin/secrets/${s.id}`, { method: 'DELETE' });
    } catch (e) {
      error = (e as Error).message;
      return;
    }
    confirming = '';
    if (formFor === s.id) formFor = null;
    notify(`Secret ${s.name} deleted.`);
    onchanged();
    void focusId(next ? `ad-del-${next.id}` : 'ad-secrets-title');
  }

  function closeForm() {
    const target = formFor && formFor !== 'new' ? `ad-edit-${formFor}` : 'ad-new-secret';
    formFor = null;
    void focusId(target);
  }
</script>

<div class="split-pane ad-secrets" style="--split-main: 560px; --split-side: 360px; --split-side-max: none">
  <section class="split-pane-main ad-secrets-list" aria-labelledby="ad-secrets-title">
    <div class="ad-section-head ad-section-head-center">
      <div class="ad-section-title">
        <h2 id="ad-secrets-title" tabindex="-1">Authentication secrets</h2>
        <p>Credentials for Git repositories, remote endpoints and brokers. Users only see a Secret’s name and description.</p>
      </div>
      {#if formFor === null}
        <Button id="ad-new-secret" variant="primary" icon="plus" onclick={() => { confirming = ''; formFor = 'new'; }}>New secret</Button>
      {/if}
    </div>
    {#if error}<p class="ad-form-hint" role="alert">{error}</p>{/if}
    {#if secrets.length === 0}
      <EmptyState title="No secret yet">
        Create a Secret to reach private Git repositories, secured endpoints or brokers. Values are never shown again.
      </EmptyState>
    {:else}
      <div class="table-wrap ad-secrets-table">
        <table class="table">
          <caption class="sr-only">Secrets. Values are never shown.</caption>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Type</th>
              <th scope="col">Credentials</th>
              <th scope="col">Used by</th>
              <th scope="col" data-align="end">Actions</th>
            </tr>
          </thead>
          <tbody>
            {#each secrets as s (s.id)}
              <tr data-selected={formFor === s.id ? '' : undefined} data-confirming={confirming === s.id ? '' : undefined}>
                <th scope="row">
                  <span class="ad-secret-name">
                    <Icon name="key" size={16} />
                    <span class="ad-secret-text">
                      <span class="ad-secret-id">{s.name}</span>
                      <span class="ad-secret-desc">{s.description}</span>
                    </span>
                  </span>
                </th>
                <td><span class="ad-secret-type">{SECRET_KIND_LABEL[s.kind]}</span></td>
                <td class="ad-secret-detail">{#each parts(s.detail) as p, i (i)}{#if p.mask}<span class="ad-nowrap">{p.t}</span>{:else}{p.t}{/if}{/each}</td>
                <td class="ad-secret-used" data-unused={s.usedBy === 0 ? '' : undefined}>{s.usedBy === 0 ? 'unused' : plural(s.usedBy, 'source')}</td>
                <td data-align="end">
                  <span class="ad-secret-actions">
                    <button type="button" id="ad-edit-{s.id}" class="ad-text-button" aria-label={`Edit secret ${s.name}`} onclick={() => { confirming = ''; formFor = s.id; }}>Edit</button>
                    <button type="button" id="ad-del-{s.id}" class="ad-danger-button" aria-label={`Delete secret ${s.name}`} aria-expanded={confirming === s.id} onclick={() => (confirming = s.id)}>Delete</button>
                  </span>
                </td>
              </tr>
              {#if confirming === s.id}
                <tr class="ad-confirm-row">
                  <td colspan="5">
                    <div role="alert" class="ad-confirm">
                      <Icon name="warning" size={14} />
                      <span class="ad-confirm-text">
                        Delete {s.name}? {s.usedBy ? `${plural(s.usedBy, 'source')} still reference it and will fail on next run.` : 'No source references it.'}
                      </span>
                      <button type="button" class="ad-small-button" onclick={() => { confirming = ''; void focusId(`ad-del-${s.id}`); }}>Cancel</button>
                      <button type="button" class="ad-small-button" data-danger="" onclick={() => remove(s)}>Delete secret</button>
                    </div>
                  </td>
                </tr>
              {/if}
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </section>
  {#if formFor !== null}
    <div class="split-pane-side">
      {#key formFor}
        <SecretForm
          secret={editing}
          names={secrets.map((s) => s.name)}
          oncancel={closeForm}
          onsaved={(message) => {
            closeForm();
            notify(message);
            onchanged();
          }}
        />
      {/key}
    </div>
  {/if}
</div>
