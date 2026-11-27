<script lang="ts">
  import type { HTMLAttributes } from 'svelte/elements';
  import Icon from './Icon.svelte';
  import type { IconName } from './icon-paths.js';
  import { PROTOCOL_ATTR, type Protocol, type ProofStatus } from './types.js';

  interface ProofValue {
    value?: string;
    status?: ProofStatus;
    icon?: IconName;
    iconLabel?: string;
  }

  interface Props extends HTMLAttributes<HTMLSpanElement> {
    protocol?: Protocol;
    arazzo?: boolean;
    name?: string;
    version?: string;
    label?: string;
    value?: string;
    status?: ProofStatus;
    icon?: IconName;
    iconLabel?: string;
    values?: ProofValue[];
    versionTone?: 'muted' | 'text';
    size?: 'sm' | 'compact' | 'md' | 'card' | 'lg' | 'xl';
  }

  let {
    protocol = 'REST',
    arazzo,
    name,
    version,
    label,
    value,
    status = 'neutral',
    icon,
    iconLabel,
    values,
    versionTone,
    size = 'md',
    class: className,
    ...rest
  }: Props = $props();

  const STATUS_WORD = { success: 'success', danger: 'error', warning: 'warning' } as const;
  const filled = (s?: ProofStatus) => (s === 'success' || s === 'danger' || s === 'warning' ? s : undefined);

  const isProof = $derived(!arazzo && label !== undefined);
  const type = $derived(arazzo ? 'ARAZZO' : isProof ? label : protocol);
  const dataProtocol = $derived(arazzo ? 'arazzo' : isProof ? undefined : PROTOCOL_ATTR[protocol]);
  const main = $derived(isProof ? value : name);
  const proofFill = $derived(isProof && status === 'protocol' ? PROTOCOL_ATTR[protocol] : undefined);
</script>

{#snippet proof(v: ProofValue)}
  {#if v.icon}<Icon name={v.icon} size={12} />{/if}{v.value ?? ''}{#if v.iconLabel}<span class="sr-only"
      >{v.value ? ` ${v.iconLabel}` : v.iconLabel}</span
    >{/if}{#if filled(v.status)}{' '}<span class="sr-only">({STATUS_WORD[filled(v.status)!]})</span>{/if}
{/snippet}

<span
  {...rest}
  class={['api-badge', className]}
  data-protocol={dataProtocol}
  data-status={isProof ? status : undefined}
  data-size={size}
  data-fill={proofFill}
  data-version={versionTone}
>
  <span class="api-badge-type">{type}</span>{' '}
  {#if isProof && (main || icon)}
    <span class="api-badge-name" data-icon-only={icon && !main ? '' : undefined}
      >{@render proof({ value: main, status, icon, iconLabel })}</span
    >
  {:else if main}
    <span class="api-badge-name">{main}</span>
  {/if}{' '}
  {#if !isProof && version}<span class="api-badge-version">{version}</span>{/if}
  {#if isProof && values}
    {#each values as v, i (i)}
      {' '}<span class="api-badge-value" data-status={v.status ?? 'neutral'} data-icon-only={v.icon && !v.value ? '' : undefined}
        >{@render proof(v)}</span
      >
    {/each}
  {/if}
</span>
