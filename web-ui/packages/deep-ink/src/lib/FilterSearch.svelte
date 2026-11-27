<script lang="ts">
  import Icon from './Icon.svelte';

  interface Props {
    label: string;
    placeholder?: string;
    value?: string;
    size?: 'md' | 'lg';
    class?: string;
    autocomplete?: 'off' | 'on';
  }

  let { label, placeholder, value = $bindable(''), size = 'md', class: className, autocomplete = 'off' }: Props = $props();
</script>

<label class={['filter-bar-search', className]} data-size={size}>
  <Icon name="search" size={16} />
  <span class="sr-only">{label}</span>
  <input type="search" placeholder={placeholder ?? label} {autocomplete} bind:value />
</label>
