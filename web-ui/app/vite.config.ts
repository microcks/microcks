import adapter from '@sveltejs/adapter-node';
import { sveltekit } from '@sveltejs/kit/vite';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [tailwindcss(), sveltekit({ adapter: adapter() })],
  // The design-system package is consumed from source: let Vite compile its Svelte files.
  ssr: { noExternal: ['@microcks/deep-ink'] },
  server: { fs: { allow: ['..'] } }
});
