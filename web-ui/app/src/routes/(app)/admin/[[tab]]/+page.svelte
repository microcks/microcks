<script lang="ts">
  import { goto, invalidate } from '$app/navigation';
  import { page } from '$app/state';
  import { ApiBadge, Icon, Tabs } from '@microcks/deep-ink';
  import PageHeader from '#lib/components/PageHeader.svelte';
  import ConfigurationTab from '#lib/components/admin/ConfigurationTab.svelte';
  import GroupsTab from '#lib/components/admin/GroupsTab.svelte';
  import SecretsTab from '#lib/components/admin/SecretsTab.svelte';
  import SnapshotsTab from '#lib/components/admin/SnapshotsTab.svelte';
  import UsersTab from '#lib/components/admin/UsersTab.svelte';

  let { data } = $props();

  const TABS = ['users', 'groups', 'secrets', 'snapshots', 'configuration'] as const;
  type Tab = (typeof TABS)[number];
  /** The prototype's toast hides itself after 4 s. */
  const TOAST_MS = 4000;

  const param = $derived(page.params.tab ?? '');
  const tab = $derived<Tab>((TABS as readonly string[]).includes(param) ? (param as Tab) : 'users');

  let toast = $state('');
  let timer: ReturnType<typeof setTimeout> | undefined;
  let region: HTMLDivElement | undefined = $state();

  // Closing the toast removes its × button: focus goes back to the selected tab instead of the page body.
  function hideToast() {
    const hadFocus = region?.contains(document.activeElement) ?? false;
    toast = '';
    if (hadFocus) document.querySelector<HTMLElement>('[role="tab"][aria-selected="true"]')?.focus();
  }
  function notify(message: string) {
    toast = message;
    clearTimeout(timer);
    timer = setTimeout(hideToast, TOAST_MS);
  }
  const reload = () => invalidate('app:admin');
</script>

<PageHeader
  title="Administration"
  description="Manage groups and their members, keep the Secrets used by importers and tests, and export or restore repository Snapshots."
  descriptionTone="secondary"
  descriptionWidth={760}
  align="start"
>
  {#snippet actions()}
    <ApiBadge label="memberships" value="internal" status="neutral" />
    <ApiBadge label="tenancy" value="label domain" status="neutral" />
  {/snippet}
</PageHeader>

<Tabs
  label="Administration sections"
  value={tab}
  onchange={(id) => goto(`/admin/${id}`)}
  items={[
    { id: 'users', label: 'Users', count: data.users.length },
    { id: 'groups', label: 'Groups', count: data.groups.length },
    { id: 'secrets', label: 'Secrets', count: data.secrets.length },
    { id: 'snapshots', label: 'Snapshots' },
    { id: 'configuration', label: 'Configuration' }
  ]}
>
  <div role="status" class="ad-toast-region" bind:this={region}>
    {#if toast}
      <div class="ad-toast">
        <Icon name="check" size={15} class="ad-toast-icon" />
        <span class="ad-toast-text">{toast}</span>
        <button type="button" class="ad-toast-close" aria-label="Dismiss message" onclick={hideToast}><Icon name="close" size={12} /></button>
      </div>
    {/if}
  </div>
  {#if tab === 'users'}
    <UsersTab users={data.users} {notify} onchanged={reload} />
  {:else if tab === 'groups'}
    <GroupsTab groups={data.groups} {notify} onchanged={reload} />
  {:else if tab === 'secrets'}
    <SecretsTab secrets={data.secrets} {notify} onchanged={reload} />
  {:else if tab === 'snapshots'}
    <SnapshotsTab services={data.snapshotServices} {notify} />
  {:else}
    <ConfigurationTab flags={data.flags} />
  {/if}
</Tabs>
