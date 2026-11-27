<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    label: Snippet;
    description?: Snippet;
    kind?: 'checkbox' | 'radio';
    variant?: 'plain' | 'card' | 'compact';
    checked?: boolean;
    group?: string;
    value?: string;
    name?: string;
    disabled?: boolean;
    onchange?: (e: Event) => void;
    class?: string;
  }

  let {
    label,
    description,
    kind = 'checkbox',
    variant = 'plain',
    checked = $bindable(false),
    group = $bindable(),
    value,
    name,
    disabled,
    onchange,
    class: className
  }: Props = $props();

  const uid = $props.id();
</script>

<label class="choice" data-variant={variant}>
  {#if kind === 'radio'}
    <input
      type="radio"
      {name}
      {value}
      {disabled}
      bind:group
      aria-labelledby="{uid}-label"
      aria-describedby={description ? `${uid}-desc` : undefined}
      class={['radio', className]}
      {onchange}
    />
  {:else}
    <input
      type="checkbox"
      {name}
      {disabled}
      bind:checked
      aria-labelledby="{uid}-label"
      aria-describedby={description ? `${uid}-desc` : undefined}
      class={['checkbox', className]}
      {onchange}
    />
  {/if}
  <span>
    <span class="choice-label" id="{uid}-label">{@render label()}</span>
    {#if description}<span class="choice-description" id="{uid}-desc">{@render description()}</span>{/if}
  </span>
</label>
