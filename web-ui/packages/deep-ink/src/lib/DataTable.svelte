<script lang="ts" module>
  export interface SortState {
    key: string;
    direction: 'ascending' | 'descending';
  }

  export interface DataTableColumn<T> {
    key: string;
    header: string;
    headerHidden?: boolean;
    align?: 'start' | 'end';
    mono?: boolean;
    nowrap?: boolean;
    rowHeader?: boolean;
    width?: string;
    sortable?: boolean;
    /** Name of the cell snippet to render (defaults to `String(row[key])`). */
    cell?: (row: T) => unknown;
  }

  export function sortRows<T>(
    rows: readonly T[],
    sort: SortState | undefined,
    valueOf?: (row: T, key: string) => unknown
  ): T[] {
    if (!sort) return [...rows];
    const get = valueOf ?? ((row: T, key: string) => (row as Record<string, unknown>)[key]);
    const factor = sort.direction === 'ascending' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const x = get(a, sort.key);
      const y = get(b, sort.key);
      if (typeof x === 'number' && typeof y === 'number') return (x - y) * factor;
      return String(x ?? '').localeCompare(String(y ?? ''), undefined, { numeric: true }) * factor;
    });
  }
</script>

<script lang="ts" generics="T">
  import type { Snippet } from 'svelte';
  import Icon from './Icon.svelte';

  interface Props {
    caption: string;
    captionHidden?: boolean;
    columns: DataTableColumn<T>[];
    rows: T[];
    rowKey: (row: T) => string;
    rowHref?: (row: T) => string;
    selectedKey?: string;
    sort?: SortState;
    onSortChange?: (sort: SortState) => void;
    emptyText?: string;
    density?: 'comfortable' | 'compact';
    headerCase?: 'upper' | 'sentence';
    rowTone?: (row: T) => 'failed' | 'danger' | 'idle' | undefined;
    scroll?: boolean;
    layout?: 'auto' | 'fixed';
    onRowSelect?: (row: T) => void;
    class?: string;
    /** Renders the cell of `column` for `row`. Falls back to the raw value. */
    cell?: Snippet<[T, DataTableColumn<T>]>;
  }

  let {
    caption,
    captionHidden,
    columns,
    rows,
    rowKey,
    rowHref,
    selectedKey,
    sort,
    onSortChange,
    emptyText = 'Nothing here yet.',
    density = 'comfortable',
    headerCase = 'upper',
    rowTone,
    scroll = true,
    layout = 'auto',
    onRowSelect,
    class: className,
    cell
  }: Props = $props();

  const captionId = $props.id();
  const sortedColumn = $derived(sort ? columns.find((c) => c.key === sort.key) : undefined);

  function nextSort(key: string): SortState {
    return { key, direction: sort?.key === key && sort.direction === 'ascending' ? 'descending' : 'ascending' };
  }
  function raw(row: T, key: string): string {
    const v = (row as Record<string, unknown>)[key];
    return v === undefined || v === null ? '' : String(v);
  }
</script>

{#snippet content(row: T, c: DataTableColumn<T>)}
  {#if cell}{@render cell(row, c)}{:else}{raw(row, c.key)}{/if}
{/snippet}

<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
<!-- The scroll region is focusable so keyboard users can scroll wide tables. -->
<div
  class={['table-wrap', className]}
  role={scroll ? 'region' : undefined}
  aria-labelledby={scroll ? captionId : undefined}
  tabindex={scroll ? 0 : undefined}
  data-scroll={scroll ? undefined : 'false'}
>
  <table
    class="table"
    data-density={density}
    data-header={headerCase === 'sentence' ? 'sentence' : undefined}
    data-layout={layout === 'fixed' ? 'fixed' : undefined}
  >
    <caption id={captionId} class={captionHidden ? 'sr-only' : undefined}>{caption}</caption>
    <thead>
      <tr>
        {#each columns as c (c.key)}
          {@const active = sort?.key === c.key ? sort.direction : undefined}
          <th
            scope="col"
            data-align={c.align === 'end' ? 'end' : undefined}
            aria-sort={c.sortable ? active : undefined}
            style={c.width ? `width:${c.width}` : undefined}
          >
            {#if c.sortable}
              <button type="button" class="table-sort" onclick={() => onSortChange?.(nextSort(c.key))}>
                {c.header}
                <Icon name={active === 'descending' ? 'arrow-down' : active === 'ascending' ? 'arrow-up' : 'dash'} size={12} />
              </button>
            {:else if c.headerHidden}
              <span class="sr-only">{c.header}</span>
            {:else}
              {c.header}
            {/if}
          </th>
        {/each}
      </tr>
    </thead>
    <tbody>
      {#if rows.length === 0}
        <tr><td class="table-empty" colspan={Math.max(columns.length, 1)}>{emptyText}</td></tr>
      {:else}
        {#each rows as row (rowKey(row))}
          {@const key = rowKey(row)}
          {@const selected = selectedKey !== undefined && key === selectedKey}
          {@const href = rowHref?.(row)}
          <tr data-selected={selected ? '' : undefined} data-tone={rowTone?.(row)}>
            {#each columns as c, i (c.key)}
              {#snippet inner()}
                {#if i === 0 && href !== undefined}
                  <a class="table-rowlink" {href} aria-current={selected ? 'true' : undefined}>{@render content(row, c)}</a>
                {:else if c.rowHeader && onRowSelect}
                  <button type="button" class="table-pick" aria-pressed={selected} onclick={() => onRowSelect(row)}
                    >{@render content(row, c)}</button
                  >
                {:else if i === 0 && selected}
                  <span class="sr-only">Selected: </span>{@render content(row, c)}
                {:else}
                  {@render content(row, c)}
                {/if}
              {/snippet}
              {#if c.rowHeader}
                <th
                  scope="row"
                  data-align={c.align === 'end' ? 'end' : undefined}
                  data-mono={c.mono ? '' : undefined}
                  data-nowrap={c.nowrap ? '' : undefined}>{@render inner()}</th
                >
              {:else}
                <td
                  data-align={c.align === 'end' ? 'end' : undefined}
                  data-mono={c.mono ? '' : undefined}
                  data-nowrap={c.nowrap ? '' : undefined}>{@render inner()}</td
                >
              {/if}
            {/each}
          </tr>
        {/each}
      {/if}
    </tbody>
  </table>
  <span role="status" aria-live="polite" class="sr-only"
    >{sort && sortedColumn ? `Sorted by ${sortedColumn.header}, ${sort.direction}` : ''}</span
  >
</div>
