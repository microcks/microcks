<script lang="ts">
  import { tick } from 'svelte';
  import { Button, Field, Icon, Input, Segmented, Textarea } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';
  import {
    SECRET_KINDS,
    SECRET_KIND_HINT,
    SECRET_KIND_LABEL,
    secretFormHint,
    validateSecretForm,
    type SecretInput,
    type SecretKind,
    type SecretSummary
  } from '#lib/admin.js';

  interface Props {
    /** `null` creates a secret; otherwise the secret being edited (its name is fixed). */
    secret: SecretSummary | null;
    names: string[];
    oncancel: () => void;
    onsaved: (message: string) => void;
  }
  let { secret, names, oncancel, onsaved }: Props = $props();

  const editing = $derived(secret !== null);
  let section: HTMLElement | undefined = $state();
  let first: HTMLInputElement | null = $state(null);

  let name = $state(secret?.name ?? '');
  let description = $state(secret && secret.description !== 'No description' ? secret.description : '');
  let kind = $state<SecretKind>(secret?.kind ?? 'basic');
  let user = $state('');
  let value = $state('');
  let ca = $state('');
  let clearCa = $state(false);
  let show = $state(false);
  let saving = $state(false);
  let failure = $state('');

  const hint = $derived(secretFormHint(validateSecretForm({ name, secret: value }, names, editing)));
  const keep = $derived(editing ? '•••••• unchanged' : undefined);
  const pw = $derived(show ? 'text' : 'password');

  // The form opens with focus on its first field (Description when editing: the name is fixed).
  $effect(() => {
    void tick().then(() => first?.focus());
  });

  function onkeydown(e: KeyboardEvent) {
    if (e.key === 'Escape') {
      e.preventDefault();
      oncancel();
    }
  }

  async function submit(e: SubmitEvent) {
    e.preventDefault();
    if (hint || saving) return;
    saving = true;
    failure = '';
    const body: Omit<SecretInput, 'name'> = { kind, description, secret: value, user, ca, clearCa };
    try {
      if (secret) {
        await api(`/api/admin/secrets/${secret.id}`, { method: 'PUT', json: body });
        onsaved(`Secret ${secret.name} updated.`);
      } else {
        await api('/api/admin/secrets', { method: 'POST', json: { ...body, name } });
        onsaved(`Secret ${name} created. Reference it from an importer or a test.`);
      }
    } catch (err) {
      failure = (err as Error).message;
      saving = false;
    }
  }
</script>

{#snippet descriptionLabel()}Description <span class="ad-label-note">· visible to every user</span>{/snippet}
{#snippet headerLabel()}Token header <span class="ad-label-note">· optional</span>{/snippet}
{#snippet caLabel()}CA certificate <span class="ad-label-note">· optional, PEM, chain allowed, to trust a private TLS endpoint</span>{/snippet}
{#snippet showButton()}
  <button type="button" class="ad-show-button" aria-pressed={show} onclick={() => (show = !show)}>{show ? 'Hide' : 'Show'}</button>
{/snippet}

<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<section bind:this={section} class="ad-secret-form" aria-labelledby="ad-secret-form-title" {onkeydown}>
  <div class="ad-secret-form-head">
    <h2 id="ad-secret-form-title">{editing ? `Edit secret ${secret?.name}` : 'New secret'}</h2>
    <button type="button" class="ad-close-button" aria-label="Close form" onclick={oncancel}><Icon name="close" size={13} /></button>
  </div>
  <form class="ad-form" onsubmit={submit} novalidate>
    {#if editing}
      <div class="ad-readonly">
        <span class="label">Name <span class="ad-label-note">· cannot be changed, importers reference it</span></span>
        <span class="ad-readonly-value">{secret?.name}</span>
      </div>
    {:else}
      <Field label="Name">
        {#snippet children(c)}
          <Input {...c} bind:ref={first} mono autocomplete="off" spellcheck={false} placeholder="gitlab-bakery" aria-describedby={hint && hint !== 'Credential required' ? 'ad-secret-hint' : undefined} bind:value={name} />
        {/snippet}
      </Field>
    {/if}
    <Field label={descriptionLabel}>
      {#snippet children(c)}
        <Input {...c} bind:ref={first} autocomplete="off" placeholder="What this secret gives access to" bind:value={description} />
      {/snippet}
    </Field>
    <div class="field">
      <span class="label">Type</span>
      <Segmented label="Type" variant="joined" class="ad-type" options={SECRET_KINDS.map((k) => ({ id: k, label: SECRET_KIND_LABEL[k] }))} value={kind} onchange={(k) => (kind = k as SecretKind)} />
      <p class="help">{SECRET_KIND_HINT[kind]}</p>
    </div>

    {#if kind === 'basic'}
      <Field label="Username">
        {#snippet children(c)}<Input {...c} mono autocomplete="off" placeholder={keep} bind:value={user} />{/snippet}
      </Field>
      <Field label="Password">
        {#snippet children(c)}
          <span class="ad-secret-input"><Input {...c} mono type={pw} autocomplete="new-password" placeholder={keep} bind:value />{@render showButton()}</span>
        {/snippet}
      </Field>
    {:else if kind === 'token'}
      <Field label="Token">
        {#snippet children(c)}
          <span class="ad-secret-input"><Input {...c} mono type={pw} autocomplete="off" placeholder={keep} bind:value />{@render showButton()}</span>
        {/snippet}
      </Field>
      <Field label={headerLabel}>
        {#snippet children(c)}<Input {...c} mono autocomplete="off" placeholder="Authorization: Bearer (default)" bind:value={user} />{/snippet}
      </Field>
    {/if}
    <Field label={caLabel}>
      {#snippet children(c)}
        <Textarea {...c} mono rows={4} placeholder={secret?.hasCa ? '•••••• unchanged' : '-----BEGIN CERTIFICATE-----\nMIIDdzCCAl+gAwIBAgIE…\n-----END CERTIFICATE-----'} disabled={clearCa} bind:value={ca} />
      {/snippet}
    </Field>
    {#if secret?.hasCa}
      <label class="ad-check"><input type="checkbox" bind:checked={clearCa} /> Remove the stored CA certificate</label>
    {/if}

    <div class="ad-lock-note ad-lock-note-sm">
      <Icon name="lock" size={14} />
      <span>Values are stored on the Microcks server and never shown again. Importers and tests reference the Secret by its name.</span>
    </div>
    <div class="ad-form-footer">
      {#if failure}<span class="ad-form-hint" role="alert">{failure}</span>{:else if hint}<span class="ad-form-hint" id="ad-secret-hint">{hint}</span>{/if}
      <Button onclick={oncancel}>Cancel</Button>
      <Button variant="primary" type="submit" disabled={Boolean(hint) || saving} aria-describedby={hint ? 'ad-secret-hint' : undefined}>
        {editing ? 'Save changes' : 'Create secret'}
      </Button>
    </div>
  </form>
</section>
