<script lang="ts">
  import { onMount } from 'svelte';
  import Button from './Button.svelte';
  import Icon from './Icon.svelte';
  import { THEME_EVENT, getTheme, setTheme, type Theme } from './theme.js';

  let theme = $state<Theme>('dark');

  onMount(() => {
    theme = getTheme();
    const sync = () => (theme = getTheme());
    document.addEventListener(THEME_EVENT, sync);
    return () => document.removeEventListener(THEME_EVENT, sync);
  });
</script>

<Button
  variant="secondary"
  iconOnly
  class="theme-toggle"
  aria-label="Light theme"
  aria-pressed={theme === 'light'}
  onclick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
>
  <Icon name="moon" size={18} />
</Button>
