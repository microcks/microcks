<script lang="ts">
  import { Button, Chip, Icon, Input } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';

  interface Props {
    /** Route that takes `PUT { labels }`, e.g. `/api/services/{id}/labels`. */
    endpoint: string;
    resourceName: string;
    /** Wording only: 'service' (default) or 'source'. */
    kind?: 'service' | 'source';
    labels: Record<string, string>;
    onclose: () => void;
    onsaved: () => void;
  }
  let { endpoint, resourceName, kind = 'service', labels, onclose, onsaved }: Props = $props();

  let dialog = $state<HTMLDialogElement>();
  let draft = $state<Record<string, string>>({ ...labels });
  let entry = $state('');
  let busy = $state(false);
  let error = $state<string | null>(null);

  const keys = $derived(Object.keys(draft));

  $effect(() => {
    dialog?.showModal();
  });

  /** Same syntax as Microcks v1: `key=value`, exactly one `=`. Returns true when the entry was accepted. */
  function add(): boolean {
    const text = entry.trim();
    if (!text) return true;
    const kv = text.split('=');
    if (kv.length !== 2 || !kv[0].trim() || !kv[1].trim()) {
      error = 'Use the key=value format, for example domain=pastry.';
      return false;
    }
    draft[kv[0].trim()] = kv[1].trim();
    entry = '';
    error = null;
    return true;
  }

  function remove(key: string) {
    delete draft[key];
  }

  async function save() {
    if (!add()) return;
    busy = true;
    error = null;
    try {
      await api(endpoint, { method: 'PUT', json: { labels: draft } });
      onsaved();
      dialog?.close();
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      busy = false;
    }
  }
</script>

<dialog class="dialog" bind:this={dialog} aria-labelledby="edit-labels-title" aria-describedby="edit-labels-desc" onclose={onclose}>
  <form
    onsubmit={(e) => {
      e.preventDefault();
      void save();
    }}
  >
    <div class="dialog-header">
      <h2 class="dialog-title" id="edit-labels-title">Edit labels</h2>
      <Button type="button" variant="ghost" size="sm" iconOnly icon="close" aria-label="Close" onclick={() => dialog?.close()} />
    </div>
    <div class="dialog-body">
      <p class="dialog-description" id="edit-labels-desc">
        Labels help you organize and select resources. They let you query {kind === 'source' ? 'sources' : 'services'} that have similar, overlapping or dissimilar labels.
      </p>
      <label class="el-label" for="el-entry">Labels for {kind} {resourceName}</label>
      <div class="el-chips" aria-live="polite">
        {#each keys as k (k)}
          <Chip variant="outline">
            {k}={draft[k]}
            <button type="button" class="el-remove" aria-label={`Remove label ${k}`} onclick={() => remove(k)}><Icon name="close" size={10} /></button>
          </Chip>
        {:else}
          <span class="el-empty">No label yet.</span>
        {/each}
      </div>
      <div class="el-add">
        <Input
          id="el-entry"
          mono
          spellcheck={false}
          autocomplete="off"
          placeholder="domain=pastry"
          bind:value={entry}
          onkeydown={(e: KeyboardEvent) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              add();
            }
          }}
        />
        <Button type="button" variant="secondary" onclick={add}>Add</Button>
      </div>
      {#if error}<p class="el-error" role="alert"><Icon name="alert-circle" size={14} /> {error}</p>{/if}
    </div>
    <div class="dialog-footer">
      <Button type="button" variant="ghost" onclick={() => dialog?.close()}>Cancel</Button>
      <Button type="submit" variant="primary" loading={busy}>Save</Button>
    </div>
  </form>
</dialog>

<style>
  .el-label { display: block; margin-bottom: var(--space-2); font: 600 13px/18px var(--font-ui); }
  .el-chips { display: flex; flex-wrap: wrap; gap: 6px; min-height: 24px; margin-bottom: var(--space-2); }
  .el-empty { color: var(--text-muted); font: var(--type-small); }
  .el-remove { display: inline-grid; place-items: center; margin-left: 4px; padding: 0; border: 0; background: none; color: inherit; cursor: pointer; }
  .el-add { display: flex; gap: var(--space-2); }
  .el-add :global(input) { flex: 1; }
  .el-error { display: flex; align-items: center; gap: 6px; margin: var(--space-2) 0 0; color: var(--danger); font: var(--type-small); }
</style>
