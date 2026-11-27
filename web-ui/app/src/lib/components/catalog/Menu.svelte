<script lang="ts">
  import { Button, type IconName } from '@microcks/deep-ink';

  export interface MenuItem {
    id: string;
    label: string;
    onselect?: () => void;
    href?: string;
    disabled?: boolean;
    description?: string;
  }

  interface Props {
    label: string;
    icon?: IconName;
    items: MenuItem[];
    align?: 'start' | 'end';
  }

  let { label, icon, items, align = 'start' }: Props = $props();
  const uid = $props.id();
  const menuId = `${uid}-menu`;
  let menu: HTMLDivElement;
  let trigger = $state<HTMLElement | null>(null);
  let expanded = $state(false);

  const itemEls = () => [...(menu?.querySelectorAll<HTMLElement>('.menu-item:not(:disabled):not([aria-disabled="true"])') ?? [])];

  function place() {
    if (!menu || !trigger) return;
    const r = trigger.getBoundingClientRect();
    const mh = menu.offsetHeight;
    const mw = menu.offsetWidth;
    const below = r.bottom + 4;
    const top = below + mh > window.innerHeight && r.top - 4 - mh > 0 ? r.top - 4 - mh : below;
    const wanted = align === 'end' ? r.right - mw : r.left;
    menu.style.top = `${Math.max(8, top)}px`;
    menu.style.left = `${Math.max(8, Math.min(wanted, window.innerWidth - mw - 8))}px`;
  }

  function ontoggle(e: Event) {
    expanded = (e as ToggleEvent).newState === 'open';
    if (expanded) {
      place();
      itemEls()[0]?.focus();
    }
  }

  function onkeydown(e: KeyboardEvent) {
    const els = itemEls();
    const at = els.indexOf(document.activeElement as HTMLElement);
    const next =
      e.key === 'ArrowDown' ? els[(at + 1) % els.length]
      : e.key === 'ArrowUp' ? els[(at - 1 + els.length) % els.length]
      : e.key === 'Home' ? els[0]
      : e.key === 'End' ? els[els.length - 1]
      : undefined;
    if (!next) return;
    e.preventDefault();
    next.focus();
  }

  function onfocusout(e: FocusEvent) {
    const to = e.relatedTarget as Node | null;
    if (to && !menu.contains(to) && !trigger?.contains(to)) menu.hidePopover?.();
  }

  const select = (item: MenuItem) => {
    item.onselect?.();
    menu.hidePopover?.();
  };
</script>

<Button bind:ref={trigger} variant="secondary" {icon} aria-expanded={expanded} aria-controls={menuId} popovertarget={menuId}>{label}</Button>
<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
<div bind:this={menu} id={menuId} class="menu" popover="auto" role="group" aria-label={label} {ontoggle} {onkeydown} {onfocusout}>
  {#each items as item (item.id)}
    {@const descId = item.description ? `${menuId}-${item.id}-desc` : undefined}
    {#if item.href !== undefined}
      <a class="menu-item" href={item.href} aria-disabled={item.disabled ? true : undefined} aria-describedby={descId} onclick={() => select(item)}>{item.label}</a>
    {:else}
      <button type="button" class="menu-item" disabled={item.disabled} aria-describedby={descId} onclick={() => select(item)}>{item.label}</button>
    {/if}
    {#if item.description}<span class="menu-item-description" id={descId}>{item.description}</span>{/if}
  {/each}
</div>
