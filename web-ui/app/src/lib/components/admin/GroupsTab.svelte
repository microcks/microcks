<script lang="ts">
  import { tick } from 'svelte';
  import { Button, Chip, EmptyState, Field, Icon, Input, Select } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';
  import { memberLabel, plural, type AdminGroup, type AdminMember, type MemberRole } from '#lib/admin.js';

  interface Props {
    groups: AdminGroup[];
    notify: (message: string) => void;
    onchanged: () => void;
  }
  let { groups, notify, onchanged }: Props = $props();

  let creating = $state(false);
  let name = $state('');
  let description = $state('');
  let confirming = $state('');
  let error = $state('');
  /** Draft of the "add member" row, by group name. */
  let who = $state<Record<string, string>>({});
  let role = $state<Record<string, MemberRole>>({});

  const groupUrl = (g: string) => `/api/admin/groups/${encodeURIComponent(g)}`;
  const nameError = $derived(name && !/^[^/\s]+$/.test(name) ? 'No space and no slash' : '');

  async function run(action: () => Promise<unknown>, message: string, after?: () => void): Promise<boolean> {
    error = '';
    try {
      await action();
    } catch (e) {
      error = (e as Error).message;
      return false;
    }
    after?.();
    notify(message);
    onchanged();
    return true;
  }

  async function create(e: SubmitEvent) {
    e.preventDefault();
    const n = name.trim();
    if (!n || nameError) return;
    await run(() => api('/api/admin/groups', { method: 'POST', json: { name: n, description } }), `Group ${n} created.`, () => {
      creating = false;
      name = '';
      description = '';
    });
  }

  const remove = (g: AdminGroup) =>
    run(() => api(groupUrl(g.name), { method: 'DELETE' }), `Group ${g.name} deleted.`, () => (confirming = ''));

  async function add(g: AdminGroup, e: SubmitEvent) {
    e.preventDefault();
    const target = (who[g.name] ?? '').trim();
    if (!target) return;
    const r = role[g.name] ?? 'user';
    const ok = await run(
      () => api(`${groupUrl(g.name)}/members`, { method: 'POST', json: { who: target, role: r } }),
      `${target} added to ${g.name} as ${r}.`,
      () => (who[g.name] = '')
    );
    if (ok) {
      await tick();
      document.getElementById(`ad-who-${g.name}`)?.focus();
    }
  }

  const removeMember = (g: AdminGroup, m: AdminMember) =>
    run(() => api(`${groupUrl(g.name)}/members/${encodeURIComponent(m.id)}`, { method: 'DELETE' }), `${memberLabel(m)} removed from ${g.name}.`);

  const noManager = (g: AdminGroup) => !g.members.some((m) => m.role === 'manager');
  const uncovered = $derived(groups.filter(noManager).length);
</script>

<div class="ad-tab">
  <div class="ad-section-head ad-section-head-center">
    <div class="ad-section-title">
      <h2>Groups</h2>
      <p>
        One group per value of the master label <span class="ad-mono">domain</span>. Its members manage the Services carrying that value. Groups and memberships are stored by Microcks, not by the identity provider.
      </p>
    </div>
    <span class="ad-meta">{plural(groups.length, 'group')}{uncovered ? ` · ${uncovered} without manager` : ''}</span>
    {#if !creating}<Button variant="primary" icon="plus" onclick={() => (creating = true)}>New group</Button>{/if}
  </div>

  {#if creating}
    <form class="ad-form ad-group-form" onsubmit={create} novalidate>
      <Field label="Group name">
        {#snippet children(c)}
          <Input {...c} mono autocomplete="off" spellcheck={false} placeholder="pastry" bind:value={name} />
        {/snippet}
      </Field>
      <Field label="Description">
        {#snippet children(c)}<Input {...c} autocomplete="off" placeholder="Optional" bind:value={description} />{/snippet}
      </Field>
      <div class="ad-form-footer">
        {#if nameError}<span class="ad-form-hint">{nameError}</span>{/if}
        <Button onclick={() => (creating = false)}>Cancel</Button>
        <Button variant="primary" type="submit" disabled={!name.trim() || Boolean(nameError)}>Create group</Button>
      </div>
    </form>
  {/if}
  {#if error}<p class="ad-form-hint" role="alert">{error}</p>{/if}

  {#if groups.length === 0}
    <EmptyState title="No group yet">Create a group, then add the people who manage its Services.</EmptyState>
  {/if}

  {#each groups as g (g.name)}
    <section class="ad-card ad-group" aria-label={`Group ${g.name}`}>
      <div class="ad-group-head">
        <div class="ad-group-title">
          <span class="ad-matrix-path">{g.name}</span>
          <span class="ad-matrix-sub">
            domain={g.name} · {plural(g.services.length, 'Service')}{g.description ? ` · ${g.description}` : ''}
          </span>
        </div>
        <span class="ad-coverage" data-ok={noManager(g) ? undefined : ''}>
          <Icon name={noManager(g) ? 'warning' : 'check'} size={13} />
          {noManager(g) ? 'no manager' : plural(g.members.filter((m) => m.role === 'manager').length, 'manager')}
        </span>
        <button type="button" class="ad-danger-button" aria-label={`Delete group ${g.name}`} aria-expanded={confirming === g.name} onclick={() => (confirming = g.name)}>Delete</button>
      </div>
      {#if confirming === g.name}
        <div role="alert" class="ad-confirm ad-group-confirm">
          <Icon name="warning" size={14} />
          <span class="ad-confirm-text">
            Delete {g.name} and its {plural(g.members.length, 'membership')}? Services keep their label but nobody manages them through this group.
          </span>
          <button type="button" class="ad-small-button" onclick={() => (confirming = '')}>Cancel</button>
          <button type="button" class="ad-small-button" data-danger="" onclick={() => remove(g)}>Delete group</button>
        </div>
      {/if}
      {#if g.members.length > 0}
        <ul class="ad-members">
          {#each g.members as m (m.id)}
            <li>
              <span class="ad-member-id">{memberLabel(m)}</span>
              <span class="ad-pills">
                <Chip size="sm" variant={m.role === 'manager' ? 'outline' : 'solid'}>{m.role}</Chip>
                {#if m.pending}<Chip size="sm" tone="warning" title="Resolved to an account at first sign-in, if the email is verified">pending</Chip>{/if}
              </span>
              <button type="button" class="ad-danger-button" aria-label={`Remove ${memberLabel(m)} from ${g.name}`} onclick={() => removeMember(g, m)}>Remove</button>
            </li>
          {/each}
        </ul>
      {:else}
        <p class="ad-note ad-members-empty">No member yet.</p>
      {/if}
      <form class="ad-member-add" onsubmit={(e) => add(g, e)} novalidate>
        <Input
          id={`ad-who-${g.name}`}
          aria-label={`Username or email to add to ${g.name}`}
          mono
          autocomplete="off"
          spellcheck={false}
          placeholder="username (sub) or email"
          bind:value={() => who[g.name] ?? '', (v) => (who[g.name] = v)}
        />
        <Select
          aria-label={`Role in ${g.name}`}
          size="sm"
          value={role[g.name] ?? 'user'}
          onchange={(e) => (role[g.name] = e.currentTarget.value as MemberRole)}
        >
          <option value="user">User</option>
          <option value="manager">Manager</option>
        </Select>
        <Button type="submit" disabled={!(who[g.name] ?? '').trim()}>Add member</Button>
      </form>
    </section>
  {/each}

  <div class="ad-legend ad-legend-sm">
    <span>An email adds a <span class="ad-legend-key">pending</span> member, resolved to its account at first sign-in if the provider reports the email as verified.</span>
    <span>Memberships apply without signing in again. Creating or deleting a group requires the admin role.</span>
  </div>
</div>
