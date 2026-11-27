<script lang="ts">
  import type { Snippet } from 'svelte';
  import { Icon } from '@microcks/deep-ink';

  export type RunSegment = 'done' | 'failed' | 'warn' | 'current' | 'queued';
  export interface RunCount {
    kind: 'pass' | 'fail' | 'warn' | 'pending';
    text: string;
  }

  interface Props {
    label?: string;
    busy?: boolean;
    done: number;
    total: number;
    caption: string;
    nowLabel?: string;
    now?: string;
    segments: RunSegment[];
    counts?: RunCount[];
    action?: Snippet;
    note?: string;
    class?: string;
  }

  let { label = 'Progress', busy = false, done, total, caption, nowLabel, now, segments, counts, action, note, class: className }: Props = $props();

  const COUNT_ICON = { pass: 'check', fail: 'cross', warn: 'warning', pending: 'circle' } as const;
</script>

<section aria-label={label} class={['run-progress', className]}>
  <div class="run-progress-top">
    <p class="run-progress-count">
      <span class="run-progress-done">{done}<span class="run-progress-total">/{total}</span></span>
      <span class="run-progress-caption">{caption}</span>
    </p>
    {#if now !== undefined}
      <div class="run-progress-now" aria-live="polite">
        {#if nowLabel}<span class="run-progress-now-label">{nowLabel}</span>{/if}
        <span class="run-progress-now-value">{now}</span>
      </div>
    {/if}
  </div>
  <div class="run-progress-bar" role="img" aria-label="{done} of {total} {caption}" aria-busy={busy || undefined}>
    {#each segments as s, i (i)}<span data-seg={s}></span>{/each}
  </div>
  {#if note}<p class="run-progress-note">{note}</p>{/if}
  {#if counts?.length || action}
    <div class="run-progress-foot">
      {#if counts?.length}
        <ul class="run-progress-counts">
          {#each counts as c (c.kind + c.text)}
            <li data-kind={c.kind}><Icon name={COUNT_ICON[c.kind]} size={13} />{c.text}</li>
          {/each}
        </ul>
      {/if}
      {#if action}<span class="run-progress-action">{@render action()}</span>{/if}
    </div>
  {/if}
</section>
