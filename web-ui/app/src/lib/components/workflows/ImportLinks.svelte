<script lang="ts">
  import { Chip, Icon, Select } from '@microcks/deep-ink';
  import { sourceVerdict, workflowVerdict, type ApiRef, type ImportAnalysis, type LinkChoices } from '#lib/workflows/import-analysis.js';

  interface Props {
    analysis: ImportAnalysis;
    choices: LinkChoices;
    /** Number of steps of each workflow, when the screen knows it. */
    stepCounts?: Record<string, string>;
    busy?: boolean;
    onchoose: (sourceName: string, api: ApiRef | null) => void;
  }

  let { analysis, choices, stepCounts = {}, busy = false, onchoose }: Props = $props();

  const key = (a: ApiRef) => `${a.name}\u0000${a.version}`;
  const linkedCount = $derived(analysis.sources.filter((s) => s.link.linked).length);

  function choose(sourceName: string, value: string) {
    onchoose(sourceName, analysis.apis.find((a) => key(a) === value) ?? null);
  }
</script>

<div class="step-section">
  <h3>{analysis.workflowIds.length} {analysis.workflowIds.length === 1 ? 'workflow' : 'workflows'} discovered</h3>
  <ul class="boxed-list" aria-busy={busy}>
    {#each analysis.checks as c (c.workflowId)}
      {@const v = workflowVerdict(c)}
      <li>
        <span class="boxed-list-main">
          <span class="boxed-list-title">{c.workflowId}</span>
          <span class="boxed-list-sub">{v.runnable ? `calls ${c.apiNames.join(', ')}` : v.note}</span>
        </span>
        <Chip variant="outline" tone={v.runnable ? 'success' : 'warning'} class="import-wf-chip">
          {#if !v.runnable}<Icon name="warning" size={12} />{/if}{v.runnable ? 'Ready to run' : 'Cannot run yet'}
        </Chip>
        {#if stepCounts[c.workflowId]}<span class="boxed-list-end">{stepCounts[c.workflowId]}</span>{/if}
      </li>
    {/each}
  </ul>
</div>

<div class="step-section">
  <h3>Link source descriptions to Microcks APIs · {linkedCount} of {analysis.sources.length} linked</h3>
  <p>Each step calls an operation of a source description. Link each one to an API already imported in Microcks: its operations are checked now, and its mocks become a run target.</p>
  <div class="sources-table">
    <table aria-busy={busy}>
      <caption class="sr-only">Source descriptions</caption>
      <thead>
        <tr>
          <th scope="col">Source description</th>
          <th scope="col">Declared URL</th>
          <th scope="col">Microcks API</th>
          <th scope="col">Step operations</th>
        </tr>
      </thead>
      <tbody>
        {#each analysis.sources as s (s.sourceName)}
          {@const v = sourceVerdict(s)}
          {@const chosen = choices[s.sourceName]}
          <tr>
            <td><code>{s.sourceName}</code></td>
            <td class="mono-url">{s.declaredUrl ?? ''}</td>
            <td>
              <Select aria-label="Microcks API of {s.sourceName}" size="sm" value={chosen ? key(chosen) : ''} disabled={busy} onchange={(e) => choose(s.sourceName, e.currentTarget.value)}>
                <option value="">Not linked</option>
                {#each analysis.apis as a (key(a))}<option value={key(a)}>{a.name} {a.version}</option>{/each}
              </Select>
              {#if s.match?.status === 'MATCHED' && chosen && s.match.contractTitle === chosen.name && s.match.contractVersion === chosen.version}
                <span class="mono-sm">MATCHED</span>
              {/if}
            </td>
            <td>
              <span class="ops-found" data-tone={v.tone}>
                <Icon name={v.tone === 'success' ? 'check-circle' : 'warning'} size={14} class="ops-found-icon" />{v.detail}
              </span>
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
</div>
