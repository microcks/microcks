<script lang="ts">
  import { afterNavigate, goto } from '$app/navigation';
  import { page } from '$app/state';
  import { AppShell, Button, CommandBar, Icon, ThemeToggle, type CommandItem } from '@microcks/deep-ink';
  import logo from '@microcks/deep-ink/assets/microcks-logo.svg';
  import { gravatarUrl } from '#lib/admin.js';
  import Can from '#lib/components/auth/Can.svelte';
  import { providePermissions } from '#lib/permissions.js';
  import { NAV_ITEMS, activeNav } from '#lib/nav.js';

  let { data, children } = $props();

  const shortcut = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform || navigator.userAgent) ? '⌘K' : 'Ctrl K';
  let paletteOpen = $state(false);

  const can = providePermissions(() => data.session);
  const visibleNav = $derived(NAV_ITEMS.filter((n) => !n.action || can(n.action)));
  const nav = $derived(visibleNav.map((n) => ({ id: n.id, label: n.label, href: n.href })));
  const commands = $derived.by(() => {
    const byId = new Map<string, string>();
    const items: CommandItem[] = [];
    for (const n of visibleNav) {
      items.push({ id: `nav-${n.id}`, label: `Go to ${n.label}`, kind: 'action', hint: 'Page' });
      byId.set(`nav-${n.id}`, n.href);
    }
    if (can('contract.import')) {
      items.push({ id: 'act-import', label: 'Import a contract', kind: 'action', hint: 'Action' });
      byId.set('act-import', '/sources/new');
    }
    for (const s of data.services) {
      items.push({ id: s.id, label: `${s.name} ${s.version}`, kind: 'service', protocol: s.protocol, hint: s.format });
      byId.set(s.id, `/services/${s.id}`);
    }
    return { items, byId };
  });

  const initials = $derived(
    (data.session?.user?.name ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((w: string) => w[0].toUpperCase())
      .join('')
  );

  let userMenuOpen = $state(false);
  let avatarFailed = $state(false);
  let avatarUrl = $state<string | null>(null);
  $effect(() => {
    avatarFailed = false;
    void gravatarUrl(data.session?.user?.email).then((url) => (avatarUrl = url));
  });

  function dismissOnOutside(node: HTMLElement) {
    const onClick = (e: MouseEvent) => {
      if (!node.contains(e.target as Node)) userMenuOpen = false;
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && userMenuOpen) {
        userMenuOpen = false;
        node.querySelector('button')?.focus();
      }
    };
    document.addEventListener('click', onClick);
    document.addEventListener('keydown', onKey);
    return {
      destroy() {
        document.removeEventListener('click', onClick);
        document.removeEventListener('keydown', onKey);
      }
    };
  }

  async function signOut() {
    await fetch('/api/auth/logout', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: '{}'
    });
    window.location.assign('/login');
  }

  // Move focus to the page title after a client-side navigation (keyboard and screen reader users).
  afterNavigate(({ type }) => {
    if (type !== 'enter') document.getElementById('page-title')?.focus({ preventScroll: true });
  });
</script>

<AppShell {nav} activeId={activeNav(page.url.pathname)}>
  {#snippet brand()}
    <a href="/" class="demo-brand"><img src={logo} alt="" width="30" height="26" />Microcks</a>
  {/snippet}
  {#snippet topbarCenter()}
    <button type="button" class="demo-search" aria-haspopup="dialog" onclick={() => (paletteOpen = true)}>
      <Icon name="search" size={16} />
      <span class="demo-search-text">Search an API, an operation, a command…</span>
      <kbd aria-hidden="true">{shortcut}</kbd>
    </button>
  {/snippet}
  {#snippet topbarActions()}
    <Can action="contract.import">
      <Button variant="primary" icon="download" href="/sources/new">Import a contract</Button>
    </Can>
    <ThemeToggle />
    {#if data.session?.user}
      <div class="demo-user" use:dismissOnOutside>
        <button
          type="button"
          class="demo-avatar"
          aria-haspopup="menu"
          aria-expanded={userMenuOpen}
          aria-label="Account menu for {data.session.user.name}"
          onclick={() => (userMenuOpen = !userMenuOpen)}
        >
          {#if avatarUrl && !avatarFailed}
            <img src={avatarUrl} alt="" width="36" height="36" onerror={() => (avatarFailed = true)} />
          {:else}
            {initials}
          {/if}
        </button>
        {#if userMenuOpen}
          <div class="demo-user-menu" role="menu">
            <div class="demo-user-menu-id">
              <strong>{data.session.user.name}</strong>
              {#if data.session.user.email}<span>{data.session.user.email}</span>{/if}
              {#if data.session.user.roles.length}<span>{data.session.user.roles.join(', ')}</span>{/if}
            </div>
            {#if data.session.enabled}
              <button type="button" role="menuitem" class="demo-user-menu-item" onclick={signOut}>Sign out</button>
            {/if}
          </div>
        {/if}
      </div>
    {/if}
  {/snippet}
  {#snippet footer()}
    <footer class="app-footer">
      <span>Microcks v2 preview · Apache License 2.0</span>
      <ul aria-label="Project links">
        <li><a href="https://microcks.io" rel="noreferrer">microcks.io</a></li>
        <li><a href="https://microcks.io/documentation/" rel="noreferrer">Documentation</a></li>
        <li><a href="https://github.com/microcks/microcks" rel="noreferrer">GitHub</a></li>
        <li><a href="https://microcks.io/discord-invite" rel="noreferrer">Community</a></li>
      </ul>
    </footer>
  {/snippet}
  {@render children()}
</AppShell>

<CommandBar
  items={commands.items}
  bind:open={paletteOpen}
  label="Search and commands"
  placeholder="Search APIs, workflows and pages"
  onClose={() => (paletteOpen = false)}
  onCommand={(item) => {
    const href = commands.byId.get(item.id);
    if (href) void goto(href);
  }}
/>
