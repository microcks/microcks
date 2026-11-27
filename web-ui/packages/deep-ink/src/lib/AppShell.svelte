<script lang="ts" module>
  import type { IconName } from './icon-paths.js';

  export interface ShellNavItem {
    id: string;
    label: string;
    href: string;
    icon?: IconName;
    badge?: string;
  }
</script>

<script lang="ts">
  import type { Snippet } from 'svelte';
  import Button from './Button.svelte';
  import Icon from './Icon.svelte';

  interface Props {
    brand: Snippet;
    nav: ShellNavItem[];
    activeId: string;
    topbarCenter?: Snippet;
    topbarActions?: Snippet;
    banner?: Snippet;
    footer?: Snippet;
    skipLinkLabel?: string;
    navLabel?: string;
    children: Snippet;
  }

  let {
    brand,
    nav,
    activeId,
    topbarCenter,
    topbarActions,
    banner,
    footer,
    skipLinkLabel = 'Skip to content',
    navLabel = 'Main',
    children
  }: Props = $props();

  const navId = $props.id();
  let open = $state(false);
  let menuEl = $state<HTMLButtonElement>();
  let mainEl = $state<HTMLElement>();

  function onKeydown(e: KeyboardEvent) {
    if (open && e.key === 'Escape') {
      open = false;
      menuEl?.focus();
    }
  }

  $effect(() => {
    const mq = window.matchMedia('(max-width: 767px)');
    const reset = () => {
      if (!mq.matches) open = false;
    };
    mq.addEventListener('change', reset);
    return () => mq.removeEventListener('change', reset);
  });
</script>

<svelte:document onkeydown={onKeydown} />

<div class="shell">
  <a
    class="skip-link"
    href="#main"
    onclick={(e) => {
      e.preventDefault();
      mainEl?.focus();
    }}>{skipLinkLabel}</a
  >
  <header class="topbar">
    <Button
      bind:ref={menuEl}
      variant="ghost"
      class="shell-menu"
      aria-expanded={open}
      aria-controls={navId}
      onclick={() => (open = !open)}
    >
      <Icon name={open ? 'close' : 'menu'} size={18} />
      Menu
    </Button>
    <div class="topbar-brand">{@render brand()}</div>
    {#if topbarCenter}<div class="topbar-center">{@render topbarCenter()}</div>{/if}
    {#if topbarActions}<div class="topbar-actions">{@render topbarActions()}</div>{/if}
  </header>
  {#if banner}<div class="shell-banner">{@render banner()}</div>{/if}
  <nav class="sidenav" id={navId} aria-label={navLabel} data-open={open ? 'true' : 'false'}>
    <ul>
      {#each nav as item (item.id)}
        <li>
          <a
            class="sidenav-link"
            href={item.href}
            aria-current={item.id === activeId ? 'page' : undefined}
            onclick={() => (open = false)}
          >
            {#if item.icon}<Icon name={item.icon} size={18} />{/if}
            <span>{item.label}</span>
            {#if item.badge}<span class="sidenav-badge">{item.badge}</span>{/if}
          </a>
        </li>
      {/each}
    </ul>
  </nav>
  <main id="main" tabindex="-1" class="main" bind:this={mainEl}>
    {@render children()}
    {#if footer}{@render footer()}{/if}
  </main>
</div>
