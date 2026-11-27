<script lang="ts">
  import { Icon } from '@microcks/deep-ink';
  import type { FeatureFlag } from '#lib/admin.js';

  let { flags }: { flags: FeatureFlag[] } = $props();
</script>

<div class="ad-tab">
  <div role="note" class="ad-lock-note">
    <Icon name="lock" size={14} />
    <span>
      Read-only. These values are reported by the Microcks backend. They come from <span class="ad-mono">application.properties</span> and <span class="ad-mono">features.properties</span>, or from your Helm values or Operator resource. Change them there and restart Microcks.
    </span>
  </div>
  <ul class="ad-card ad-flags" aria-label="Features and their configuration">
    {#each flags as f (f.id)}
      <li class="ad-flag">
        <span class="ad-flag-text">
          <span class="ad-flag-label">{f.label}</span>
          <span class="ad-flag-desc">{f.description}</span>
        </span>
        <span class="ad-flag-state" data-on={f.enabled ? '' : undefined}>
          <Icon name={f.enabled ? 'check' : 'circle'} size={14} />
          {f.enabled ? 'Enabled' : 'Disabled'}
        </span>
        <span class="ad-flag-value">{f.value ?? '—'}</span>
        <span class="ad-flag-key">{f.key}</span>
      </li>
    {/each}
  </ul>
</div>
