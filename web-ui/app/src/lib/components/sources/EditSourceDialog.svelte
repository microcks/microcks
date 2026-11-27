<script lang="ts">
  import { Button, Choice, Field, Input, Select } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';
  import { validateArtifactUrl } from '#lib/importing.js';
  import type { SecretSummary } from '#lib/admin.js';
  import type { Source } from '#lib/types.js';

  interface Props {
    source: Source;
    onclose: () => void;
    onsaved: () => void;
  }
  let { source, onclose, onsaved }: Props = $props();

  let dialog = $state<HTMLDialogElement>();
  let name = $state(source.name);
  let url = $state(source.url);
  let secret = $state(source.secret ?? '');
  let role = $state<'PRIMARY' | 'SECONDARY'>(source.role);
  let insecure = $state(!source.tlsValidation);
  let secrets = $state<string[]>([]);
  let busy = $state(false);
  let error = $state<string | null>(null);

  const urlError = $derived(validateArtifactUrl(url));
  // A Secret deleted since the importer was saved must still be shown, to be replaced or removed.
  const options = $derived(secret && !secrets.includes(secret) ? [...secrets, secret] : secrets);

  $effect(() => {
    dialog?.showModal();
  });
  $effect(() => {
    api<SecretSummary[]>('/api/admin/secrets')
      .then((list) => (secrets = list.map((s) => s.name)))
      .catch(() => {});
  });

  async function save() {
    if (urlError || !name.trim()) return;
    busy = true;
    error = null;
    try {
      await api(`/api/sources/${encodeURIComponent(source.id)}`, {
        method: 'PATCH',
        json: { name, url, secret, role, tlsValidation: !insecure }
      });
      onsaved();
      dialog?.close();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      busy = false;
    }
  }
</script>

<dialog class="dialog" bind:this={dialog} aria-labelledby="edit-source-title" onclose={onclose}>
  <form
    onsubmit={(e) => {
      e.preventDefault();
      void save();
    }}
  >
    <div class="dialog-header">
      <h2 class="dialog-title" id="edit-source-title">Edit source</h2>
      <Button type="button" variant="ghost" size="sm" iconOnly icon="close" aria-label="Close" onclick={() => dialog?.close()} />
    </div>
    <div class="dialog-body es-body">
      <Field label="Name" error={name.trim() ? undefined : 'A name is required.'}>
        {#snippet children(c)}<Input {...c} bind:value={name} />{/snippet}
      </Field>
      <Field label="Artifact URL" help="The next import fetches this URL. Use Import now to apply it immediately." error={urlError}>
        {#snippet children(c)}<Input {...c} mono type="url" bind:value={url} />{/snippet}
      </Field>
      <Field label="Authentication Secret" help="Secrets are managed in Administration.">
        {#snippet children(c)}
          <Select {...c} bind:value={secret}>
            <option value="">None (public resource)</option>
            {#each options as n (n)}<option value={n}>{n}</option>{/each}
          </Select>
        {/snippet}
      </Field>
      <Field label="Artifact role">
        {#snippet children(c)}
          <Select {...c} bind:value={role}>
            <option value="PRIMARY">Primary: creates or updates Services</option>
            <option value="SECONDARY">Secondary: merges examples into existing Services</option>
          </Select>
        {/snippet}
      </Field>
      <Choice bind:checked={insecure}>{#snippet label()}Disable SSL validation{/snippet}</Choice>
      {#if error}<p role="alert" class="es-error">{error}</p>{/if}
    </div>
    <div class="dialog-footer">
      <Button type="button" variant="ghost" onclick={() => dialog?.close()}>Cancel</Button>
      <Button type="submit" variant="primary" loading={busy} disabled={Boolean(urlError) || !name.trim()}>Save</Button>
    </div>
  </form>
</dialog>

<style>
  .es-body { display: grid; gap: var(--space-3); }
  .es-error { margin: 0; color: var(--danger); font: var(--type-small); }
</style>
