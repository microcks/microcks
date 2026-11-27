<script lang="ts" module>
  export type CheckResult = 'pass' | 'fail' | 'warn' | 'running' | 'queued';
  export interface MatrixColumn {
    id: string;
    label: string;
    sub?: string;
  }
  export interface MatrixCell {
    columnId?: string;
    result: CheckResult;
    label?: string;
    duration?: string;
  }
  export interface MatrixRow {
    id: string;
    label: string;
    sub?: string;
    mono?: boolean;
    cells: MatrixCell[];
  }
  export interface MatrixGroup {
    id: string;
    label: string;
    sub?: string;
    rows: MatrixRow[];
  }
  export interface MatrixFooterCell {
    text: string;
    tone?: 'success' | 'danger';
  }
  export const cellKey = (rowId: string, columnId?: string) => `${rowId}|${columnId ?? '*'}`;
</script>

<script lang="ts">
  import { Icon, type IconName } from '@microcks/deep-ink';

  interface Props {
    caption: string;
    columns: MatrixColumn[];
    groups: MatrixGroup[];
    footer?: { label: string; cells: MatrixFooterCell[] };
    selectedKey?: string;
    onselect?: (key: string | undefined) => void;
  }

  let { caption, columns, groups, footer, selectedKey, onselect }: Props = $props();

  const RESULT: Record<CheckResult, { word: string; icon: IconName }> = {
    pass: { word: 'Passed', icon: 'check' },
    fail: { word: 'Failed', icon: 'cross' },
    warn: { word: 'Warning', icon: 'warning' },
    running: { word: 'Running', icon: 'running' },
    queued: { word: 'Queued', icon: 'circle' }
  };
  const span = $derived(columns.length);
</script>

<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
<div class="matrix-wrap" role="region" aria-label={caption} tabindex="0">
  <table class="matrix" style="--matrix-columns: {span}">
    <caption class="sr-only">{caption}</caption>
    <thead>
      <tr>
        <th scope="col">Check</th>
        {#each columns as c (c.id)}
          <th scope="col"><span>{c.label}</span>{#if c.sub}<span class="matrix-sub">{c.sub}</span>{/if}</th>
        {/each}
      </tr>
    </thead>
    {#each groups as g (g.id)}
      <tbody>
        <tr class="matrix-group">
          <th scope="rowgroup" colspan={span + 1}>{g.label}{#if g.sub}<span class="matrix-sub"> · {g.sub}</span>{/if}</th>
        </tr>
        {#each g.rows as r (r.id)}
          <tr>
            <th scope="row" data-mono={r.mono ? '' : undefined}>
              <span>{r.label}</span>{#if r.sub}<span class="matrix-sub">{r.sub}</span>{/if}
            </th>
            {#each r.cells as cell (cellKey(r.id, cell.columnId))}
              {@const key = cellKey(r.id, cell.columnId)}
              {@const col = columns.find((c) => c.id === cell.columnId)}
              {@const finished = cell.result === 'pass' || cell.result === 'fail' || cell.result === 'warn'}
              {@const look = RESULT[cell.result]}
              {@const selected = selectedKey === key}
              <td colspan={cell.columnId ? 1 : span} data-result={cell.result}>
                {#if finished && onselect}
                  <button type="button" class="matrix-cell" aria-pressed={selected} onclick={() => onselect(selected ? undefined : key)}>
                    <span class="sr-only">{col ? `${col.label} × ` : ''}{r.label}:</span>
                    <Icon name={look.icon} size={14} />
                    <span>{cell.label ?? look.word}</span>
                    {#if cell.duration}<span class="matrix-ms">{cell.duration}</span>{/if}
                  </button>
                {:else}
                  <span class="matrix-cell">
                    <Icon name={look.icon} size={14} />
                    <span>{cell.label ?? look.word}</span>
                    {#if finished && cell.duration}<span class="matrix-ms">{cell.duration}</span>{/if}
                  </span>
                {/if}
              </td>
            {/each}
          </tr>
        {/each}
      </tbody>
    {/each}
    {#if footer}
      <tfoot>
        <tr>
          <th scope="row">{footer.label}</th>
          {#each footer.cells as c, i (i)}<td data-tone={c.tone}>{c.text}</td>{/each}
        </tr>
      </tfoot>
    {/if}
  </table>
</div>
