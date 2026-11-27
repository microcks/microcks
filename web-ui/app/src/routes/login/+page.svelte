<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { Button } from '@microcks/deep-ink';
  import logo from '@microcks/deep-ink/assets/microcks-logo.svg';

  let state = $state<'loading' | 'ready' | 'unavailable'>('loading');

  onMount(async () => {
    try {
      const session = await (await fetch('/api/auth/session')).json();
      if (!session.enabled || session.authenticated) {
        await goto('/');
      } else {
        state = session.oidcEnabled ? 'ready' : 'unavailable';
      }
    } catch {
      state = 'unavailable';
    }
  });
</script>

<svelte:head><title>Sign in · Microcks</title></svelte:head>

<main class="login">
  <img src={logo} alt="" width="40" height="35" />
  <h1 id="page-title">Sign in to Microcks</h1>
  {#if state === 'ready'}
    <p>Your organisation's identity provider authenticates you.</p>
    <!-- Full navigation: the backend redirects to the identity provider. -->
    <Button variant="primary" href="/api/auth/login" data-sveltekit-reload>Sign in with your identity provider</Button>
  {:else if state === 'unavailable'}
    <p role="alert">Authentication is enabled but no identity provider is configured for this instance.</p>
  {:else}
    <p>Checking your session…</p>
  {/if}
</main>

<style>
  .login {
    display: grid;
    gap: 12px;
    justify-items: center;
    align-content: center;
    min-height: 100dvh;
    padding: 20px;
    text-align: center;
  }
  h1 {
    font-size: 24px;
    font-weight: 400;
    margin: 0;
  }
  p {
    margin: 0;
    max-width: 40ch;
  }
</style>
