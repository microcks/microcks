<script lang="ts">
  import PageHeader from '#lib/components/PageHeader.svelte';
  import { WAVES } from '#lib/components/observe/screen-map.js';

  let { data } = $props();
</script>

<div data-main-pad="roomy">
  <PageHeader title="From contract to proof" />
  <div class="stack">
    <p class="map-lead">
      {data.total} screens across {data.phases.length} steps, delivered in {WAVES.length} waves by priority:
      {WAVES.map((name, i) => `W${i + 1} ${name}`).join(', ')}. Only screens present in this build are listed.
    </p>
    {#each data.phases as phase (phase.step)}
      <section aria-labelledby="phase-{phase.step}">
        <h2 id="phase-{phase.step}" class="map-step">Step {phase.step} · {phase.name}</h2>
        <p class="map-verb">{phase.verb}</p>
        <ul class="map-grid">
          {#each phase.screens as screen (screen.n)}
            <li class="panel map-screen">
              <h3>
                <span>{screen.n}</span> ·
                {#if screen.href}<a href={screen.href}>{screen.title}</a>{:else}{screen.title}{/if}
              </h3>
              <p><code>{screen.route}</code> · Wave {screen.wave} ({WAVES[screen.wave - 1]})</p>
              <p>{screen.what}</p>
              <p>{screen.wow}</p>
            </li>
          {/each}
        </ul>
      </section>
    {/each}
    <p class="map-verb">Cross-cutting: navigation shell, ⌘K command bar, drop a contract anywhere, light and dark theme.</p>
  </div>
</div>

<style>
  .map-lead, .map-verb { margin: 0; color: var(--text-secondary); font-size: 13px; line-height: 1.5; }
  .map-step { margin: 8px 0 2px; font-size: 16px; font-weight: 600; }
  .map-grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(min(100%, 260px), 1fr)); margin: 10px 0 0; padding: 0; list-style: none; }
  .map-screen { display: flex; flex-direction: column; gap: 6px; padding: 14px 16px; }
  .map-screen h3 { margin: 0; font-size: 15px; font-weight: 600; }
  .map-screen p { margin: 0; font-size: 13px; line-height: 1.5; color: var(--text-secondary); }
  .map-screen code { font: 400 12px/1.4 var(--font-mono); color: var(--text); }
</style>
