<script lang="ts" module>
  export type RunnerLevel = 'INFO' | 'SEND' | 'PASS' | 'FAIL' | 'WARN' | 'WAIT';
  export interface RunnerLine {
    time: string;
    level: RunnerLevel;
    text: string;
    emphasis?: boolean;
    error?: boolean;
  }
</script>

<script lang="ts">
  import type { Snippet } from 'svelte';

  interface Props {
    title?: string;
    status?: Snippet;
    lines: RunnerLine[];
    class?: string;
  }

  let { title = 'Runner log', status, lines, class: className }: Props = $props();
  const id = $props.id();
</script>

<section aria-labelledby={id} class={['runner-log', className]}>
  <div class="runner-log-head">
    <h2 class="runner-log-title" {id}>{title}</h2>
    {#if status}<span class="runner-log-status">{@render status()}</span>{/if}
  </div>
  <!-- The log scrolls, so it must be keyboard reachable. -->
  <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
  <div class="runner-log-body" role="log" aria-labelledby={id} tabindex="0">
    <ol class="runner-log-lines">
      {#each lines as l, i (i)}
        <li class="runner-log-line" data-level={l.level} data-emphasis={l.emphasis ? '' : undefined} data-error={l.error ? '' : undefined}>
          <span class="runner-log-time">{l.time}</span>
          <span class="runner-log-level">{l.level}</span>
          <span class="runner-log-text">{l.text}</span>
        </li>
      {/each}
    </ol>
  </div>
</section>
