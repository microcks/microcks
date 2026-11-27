<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    label: Snippet;
    description?: Snippet;
    checked: boolean;
    onchange?: (checked: boolean) => void;
    size?: 'md' | 'lg';
    class?: string;
  }

  let { label, description, checked = $bindable(), onchange, size = 'md', class: className }: Props = $props();
  const uid = $props.id();
</script>

<label class="choice">
  <input
    type="checkbox"
    role="switch"
    aria-labelledby="{uid}-label"
    aria-describedby={description ? `${uid}-desc` : undefined}
    class={['switch', className]}
    data-size={size === 'lg' ? 'lg' : undefined}
    bind:checked
    onchange={(e) => onchange?.(e.currentTarget.checked)}
  />
  <span>
    <span class="choice-label" id="{uid}-label">{@render label()}</span>
    {#if description}<span class="choice-description" id="{uid}-desc">{@render description()}</span>{/if}
  </span>
</label>
