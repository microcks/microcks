<script lang="ts">
  import type { Snippet } from 'svelte';
  import Icon from './Icon.svelte';

  export interface FieldControl {
    id: string;
    'aria-describedby'?: string;
    'aria-invalid'?: true;
    required?: boolean;
  }

  interface Props {
    label: string | Snippet;
    help?: string;
    error?: string;
    required?: boolean;
    class?: string;
    children: Snippet<[FieldControl]>;
  }

  let { label, help, error, required, class: className, children }: Props = $props();

  const uid = $props.id();
  const id = $derived(`${uid}-control`);
  const helpId = $derived(help ? `${uid}-help` : undefined);
  const errorId = $derived(error ? `${uid}-error` : undefined);
  const control = $derived<FieldControl>({
    id,
    'aria-describedby': [helpId, errorId].filter(Boolean).join(' ') || undefined,
    'aria-invalid': error ? true : undefined,
    required: required || undefined
  });
</script>

<div class={['field', className]} data-invalid={error ? '' : undefined}>
  <label class="label" for={id}>{#if typeof label === 'string'}{label}{:else}{@render label()}{/if}{#if required}<span class="required" aria-hidden="true"> *</span>{/if}</label>
  {@render children(control)}
  {#if help}<p class="help" id={helpId}>{help}</p>{/if}
  {#if error}
    <p class="error" id={errorId}>
      <Icon name="cross" size={13} />
      <span><span class="sr-only">Error: </span>{error}</span>
    </p>
  {/if}
</div>
