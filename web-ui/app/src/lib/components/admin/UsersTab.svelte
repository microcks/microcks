<script lang="ts">
  import { Chip, DataTable, EmptyState, FilterSearch, type DataTableColumn } from '@microcks/deep-ink';
  import { api } from '#lib/api.js';
  import { plural, type AdminUser } from '#lib/admin.js';

  interface Props {
    users: AdminUser[];
    notify: (message: string) => void;
    onchanged: () => void;
  }
  let { users, notify, onchanged }: Props = $props();

  let query = $state('');
  let confirming = $state('');
  let error = $state('');

  const q = $derived(query.trim().toLowerCase());
  const rows = $derived(
    users.filter((u) => !q || `${u.id} ${u.name} ${u.email ?? ''}`.toLowerCase().includes(q))
  );
  const pending = $derived(users.filter((u) => u.pending).length);
  const disabled = $derived(users.filter((u) => u.disabled).length);

  const columns: DataTableColumn<AdminUser>[] = [
    { key: 'user', header: 'User' },
    { key: 'roles', header: 'Roles' },
    { key: 'status', header: 'Status' },
    { key: 'groups', header: 'Groups' },
    { key: 'actions', header: 'Actions' }
  ];

  const userUrl = (u: AdminUser) => `/api/admin/users/${encodeURIComponent(u.userId ?? '')}`;
  const lastLogin = (iso?: string) => (iso ? new Date(iso).toLocaleString() : 'never');

  async function run(action: () => Promise<unknown>, message: string) {
    error = '';
    try {
      await action();
    } catch (e) {
      error = (e as Error).message;
      return;
    }
    confirming = '';
    notify(message);
    onchanged();
  }

  const toggle = (u: AdminUser) =>
    run(
      () => api(userUrl(u), { method: 'PUT', json: { disabled: !u.disabled } }),
      `${u.id} ${u.disabled ? 'enabled' : 'disabled'}.`
    );
  const remove = (u: AdminUser) => run(() => api(userUrl(u), { method: 'DELETE' }), `${u.id} removed.`);
</script>

<div class="ad-tab">
  <div class="ad-section-head">
    <div class="ad-section-title">
      <h2>Users</h2>
      <p>
        An account is created the first time a person signs in through your identity provider. Identity and the global roles
        <span class="ad-mono">admin</span>, <span class="ad-mono">manager</span> and <span class="ad-mono">user</span> are refreshed from the provider at each sign-in;
        group roles are managed on the Groups tab. Disabling an account blocks it immediately.
      </p>
    </div>
    <span class="ad-meta">{plural(users.length, 'person', 'people')} · {pending} pending · {disabled} disabled</span>
  </div>
  {#if error}<p class="ad-form-hint" role="alert">{error}</p>{/if}
  {#if users.length === 0}
    <EmptyState title="No user yet">Accounts appear here after the first sign-in. When authentication is disabled, there is no user list.</EmptyState>
  {:else}
    <div class="ad-toolbar">
      <FilterSearch label="Filter users" placeholder="Filter by name, username or email…" bind:value={query} />
    </div>
    <DataTable
      class="ad-users"
      caption="People known to Microcks"
      captionHidden
      {columns}
      {rows}
      rowKey={(u) => u.id}
      emptyText={`No user matches “${query.trim()}”.`}
    >
      {#snippet cell(u, c)}
        {#if c.key === 'user'}
          <span class="ad-user">
            <span class="ad-avatar" aria-hidden="true">{u.initials}</span>
            <span class="ad-user-text">
              <span class="ad-user-name">{u.name}</span>
              <span class="ad-user-id">{u.pending ? 'invited' : u.id}</span>
              {#if u.email}<span class="ad-user-email">{u.email}</span>{/if}
            </span>
          </span>
        {:else if c.key === 'roles'}
          <span class="ad-pills">
            {#each u.roles as r (r)}<Chip size="sm" variant="outline">{r}</Chip>{/each}
          </span>
        {:else if c.key === 'status'}
          {#if u.pending}
            <Chip size="sm" tone="warning" title="Joins the group at first sign-in, if the email is verified">pending</Chip>
          {:else if u.disabled}
            <Chip size="sm" tone="danger">disabled</Chip>
          {:else}
            <Chip size="sm" tone="success">active</Chip>
          {/if}
          {#if !u.pending}<span class="ad-user-id">last sign-in {lastLogin(u.lastLoginAt)}</span>{/if}
        {:else if c.key === 'groups'}
          <span class="ad-pills">
            {#each u.memberships as m (m.group)}
              <Chip size="sm" variant={m.role === 'manager' ? 'outline' : 'solid'}>{m.group} · {m.role}</Chip>
            {/each}
          </span>
        {:else if u.userId}
          {#if confirming === u.id}
            <span role="alert" class="ad-confirm">
              <span class="ad-confirm-text">Remove {u.id}? It is created again at next sign-in.</span>
              <button type="button" class="ad-danger-button" onclick={() => remove(u)}>Remove</button>
              <button type="button" class="ad-small-button" onclick={() => (confirming = '')}>Cancel</button>
            </span>
          {:else}
            <span class="ad-pills">
              <button type="button" class="ad-small-button" onclick={() => toggle(u)}>{u.disabled ? 'Enable' : 'Disable'}</button>
              <button type="button" class="ad-small-button" aria-label={`Remove ${u.id}`} onclick={() => (confirming = u.id)}>Remove</button>
            </span>
          {/if}
        {/if}
      {/snippet}
    </DataTable>
  {/if}
  <div class="ad-legend">
    <span><span class="ad-legend-key">user</span> browses APIs, runs tests in its groups</span>
    <span><span class="ad-legend-key">manager</span> imports contracts, edits mocks and members of its groups</span>
    <span><span class="ad-legend-key">pending</span> invited by email, never signed in</span>
  </div>
</div>
