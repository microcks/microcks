<script lang="ts">
  import { ApiBadge, Button, Icon } from '@microcks/deep-ink';
  import type { AgentCheck, AgentCheckDetail, DetailTone } from '#lib/runs.js';
  import Coded from './Coded.svelte';
  import ResultBox from './ResultBox.svelte';
  import SectionLabel from './SectionLabel.svelte';
  import TaskLanes from './TaskLanes.svelte';

  interface Props {
    detail: AgentCheckDetail;
    check: AgentCheck;
    cardRow: boolean;
    onclose: () => void;
  }

  let { detail, check, cardRow, onclose }: Props = $props();

  const RESULT_WORD = { pass: 'passed', fail: 'failed', warn: 'absent' } as const;
  const TONE_WORD: Record<DetailTone, string> = { ok: 'ok', bad: 'problem', warn: 'warning', muted: '', plain: '' };</script>

<section class="cell-detail" data-result={check.result} aria-label="Detail of {detail.title}">
  <div class="cell-detail-head">
    <div class="cell-detail-title">
      <h3>{detail.title}</h3>
      <span class="cell-detail-sub">{detail.sub}</span>
    </div>
    <div class="cell-detail-actions">
      <ApiBadge
        label={cardRow ? 'agent card' : 'conversation'}
        value={RESULT_WORD[check.result]}
        status={check.result === 'pass' ? 'success' : check.result === 'fail' ? 'danger' : 'warning'}
      />
      <Button size="sm" iconOnly icon="close" aria-label="Close cell detail" class="cell-detail-close" onclick={onclose} />
    </div>
  </div>

  {#if detail.kind === 'summary'}
    <dl class="cell-facts">
      {#each detail.rows as r (r.label)}
        <div>
          <dt>{r.label}</dt>
          <dd data-tone={r.tone}>{r.value}{#if TONE_WORD[r.tone]}<span class="sr-only"> ({TONE_WORD[r.tone]})</span>{/if}</dd>
        </div>
      {/each}
    </dl>
    {#if detail.note}<p class="cell-detail-note">{detail.note}</p>{/if}
  {:else if detail.kind === 'lanes'}
    <div class="detail-block">
      <SectionLabel as="h4" lineHeight="normal">Task lifecycle · expected versus actual</SectionLabel>
      <div class="lifecycle-box">
        <TaskLanes lanes={detail.lanes} />
        {#if detail.note}<p class="lifecycle-note"><Icon name="arrow-up" size={13} />{detail.note}</p>{/if}
      </div>
    </div>
    {#if detail.verdict}
      {@render verdict(detail.verdict)}
    {/if}
    <div class="detail-block">
      <SectionLabel as="h4" lineHeight="normal">Raw HTTP exchange · {detail.exchanges.length} calls</SectionLabel>
      {#each detail.exchanges as x, i (i)}
        <div class="exchange" data-highlight={x.highlight ? '' : undefined}>
          <div class="exchange-head">
            <span class="exchange-call">{x.head}</span>
            <span class="exchange-status" data-tone={x.statusTone}>{x.status}</span>
          </div>
          <div class="exchange-cols">
            {#each [{ label: 'Request', lines: x.request }, { label: 'Response', lines: x.response }] as side (side.label)}
              <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
              <div class="exchange-side" tabindex="0" role="group" aria-label="{side.label} of {x.head}">
                <div class="exchange-side-inner">
                  <span class="exchange-label">{side.label}</span>
                  {#each side.lines as l, j (j)}
                    <div class="exchange-line" data-kind={l.kind}>
                      <span class="exchange-mark" aria-hidden="true">{l.kind === 'ok' ? '✓' : l.kind === 'bad' ? '✕' : ''}</span>
                      <span>{l.text}</span>
                      {#if l.kind === 'ok' || l.kind === 'bad'}<span class="sr-only"> ({l.kind === 'ok' ? 'expected' : 'wrong'})</span>{/if}
                    </div>
                  {/each}
                </div>
              </div>
            {/each}
          </div>
        </div>
      {/each}
    </div>
  {:else}
    <div class="detail-block">
      <SectionLabel as="h4" lineHeight="normal">Received stream · a2a.v1.A2AService/SendStreamingMessage</SectionLabel>
      <ol class="stream-events">
        {#each detail.events as e, i (i)}
          <li
            data-expected={e.expected ? '' : undefined}
            data-bad={e.mark === 'x' ? '' : undefined}
            data-kind={e.kind === 'request' ? 'request' : e.kind === 'stream closed' ? 'closed' : undefined}
          >
            <span class="stream-at">{e.at}</span>
            <span class="stream-mark" aria-hidden="true">{e.mark === 'x' ? '✕' : e.mark === '+' ? '+' : ''}</span>
            {#if e.mark}<span class="sr-only">{e.mark === 'x' ? 'Wrong: ' : 'Missing: '}</span>{/if}
            <span class="stream-kind">{e.kind}</span>
            <span class="stream-detail">{e.detail}</span>
            <span class="stream-flags">
              {#each e.flags as f (f.text)}<span class="stream-flag" data-tone={f.tone}>{f.text}</span>{/each}
            </span>
          </li>
        {/each}
      </ol>
    </div>
    {#if detail.verdict}
      {@render verdict(detail.verdict)}
    {/if}
  {/if}
</section>

{#snippet verdict(v: { title: string; body: string })}
  <ResultBox tone="danger" icon="cross" bordered={false} surface="raised" live="off">
    {#snippet title()}<Coded text={v.title} />{/snippet}
    <Coded text={v.body} />
  </ResultBox>
{/snippet}
