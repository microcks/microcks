<script lang="ts" module>
  export interface TaskLaneNode {
    label: string;
    task?: string;
    at?: string;
    tone?: 'ok' | 'bad';
    absent?: boolean;
  }
  export interface TaskLane {
    label: string;
    sub?: string;
    tone?: 'ok' | 'bad';
    nodes: TaskLaneNode[];
  }
</script>

<script lang="ts">
  import { Icon } from '@microcks/deep-ink';

  let { lanes }: { lanes: TaskLane[] } = $props();
</script>

<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
<div role="region" aria-label="Task lanes" tabindex="0" class="task-lanes">
  {#each lanes as lane (lane.label)}
    <div class="task-lane" data-tone={lane.tone}>
      <p class="task-lane-title">
        <strong>{lane.label}</strong>
        {#if lane.sub}<span>{lane.sub}</span>{/if}
      </p>
      <ol aria-label={lane.sub ? `${lane.label}: ${lane.sub}` : lane.label}>
        {#each lane.nodes as node, i (i)}
          <li class="task-lane-node" data-tone={node.tone} data-absent={node.absent ? '' : undefined}>
            {#if node.absent}
              <span aria-hidden="true">—</span>
            {:else}
              <span class="task-lane-label">
                {#if node.tone === 'bad'}<Icon name="cross" size={12} />{:else if node.tone === 'ok'}<Icon name="check" size={12} />{/if}
                {node.label}
                {#if node.tone}<span class="sr-only"> ({node.tone === 'bad' ? 'illegal' : 'legal'})</span>{/if}
              </span>
              {#if node.task}<span class="task-lane-task">{node.task}</span>{/if}
              {#if node.at}<span class="task-lane-at">{node.at}</span>{/if}
            {/if}
          </li>
        {/each}
      </ol>
    </div>
  {/each}
</div>
