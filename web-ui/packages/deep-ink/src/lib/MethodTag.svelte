<script lang="ts">
  import { PROTOCOL_ATTR, type Protocol } from './types.js';

  interface Props {
    method: string;
    protocol?: Protocol;
    minWidth?: number;
    size?: 'md' | 'sm' | 'xs';
    class?: string;
  }

  let { method, protocol = 'REST', minWidth, size = 'md', class: className }: Props = $props();
</script>

<span
  class={['method', className]}
  data-method={method}
  data-protocol={PROTOCOL_ATTR[protocol]}
  data-size={size === 'md' ? undefined : size}
  style={minWidth ? `--method-min-width:${minWidth}px` : undefined}>{method}</span
>
