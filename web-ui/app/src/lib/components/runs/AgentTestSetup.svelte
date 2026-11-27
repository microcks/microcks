<script lang="ts">
  import { Button, Choice, Field, Input, Select } from '@microcks/deep-ink';
  import { setupSummary, validateSetup, type AgentTestDefaults, type AgentTestSetup, type TransportId } from '#lib/runs.js';

  interface Props {
    defaults: AgentTestDefaults;
    initial: AgentTestSetup;
    busy?: boolean;
    onrun: (setup: AgentTestSetup) => void;
  }

  let { defaults, initial, busy = false, onrun }: Props = $props();

  // The form starts from the setup it is given and keeps its own copy.
  /* svelte-ignore state_referenced_locally */
  let setup = $state<AgentTestSetup>({ ...initial, transports: [...initial.transports], conversations: [...initial.conversations] });
  /* svelte-ignore state_referenced_locally */
  let timeout = $state(String(initial.timeoutMs));

  const merged = $derived<AgentTestSetup>({ ...setup, timeoutMs: Number.parseInt(timeout, 10) });
  const problem = $derived(validateSetup(merged));
  const urlOk = $derived(/^https?:\/\/\S+$/.test(setup.cardUrl.trim()));
  const timeoutOk = $derived(timeout.trim() !== '' && Number.isFinite(merged.timeoutMs) && merged.timeoutMs > 0);
  const secret = $derived(defaults.secrets.find((s) => s.name === setup.secret));

  function toggle<T extends string>(list: T[], id: T): T[] {
    return list.includes(id) ? list.filter((x) => x !== id) : [...list, id];
  }

  function submit(e: SubmitEvent) {
    e.preventDefault();
    if (!problem) onrun(merged);
  }

  const MODES = [
    { value: 'contains' as const, label: 'Contains', example: '"Millefeuille" in the answer' },
    { value: 'regex' as const, label: 'Regex', example: '/mille-?feuille/i' }
  ];
</script>

<section aria-labelledby="setup-title" class="setup-card">
  <div class="setup-card-head">
    <h2 id="setup-title">Test a real agent against its card</h2>
    <p>
      Microcks reads the Agent Card, checks it, then replays each conversation of the <span class="mono">APIExamples</span> artifact on every selected
      transport. The transport result and the task state are judged separately: a <span class="mono">200 OK</span> carrying a
      <span class="mono">failed</span> task is still a failure.
    </p>
  </div>
  <form class="setup-grid" onsubmit={submit} novalidate>
    <div class="setup-col" data-col="main">
      <Field
        label="Agent Card URL"
        error={urlOk ? undefined : 'Enter an http(s) URL, usually ending in /.well-known/agent-card.json.'}
        help={urlOk ? 'Microcks reads the card, then calls each interface listed in supportedInterfaces.' : undefined}
        class="setup-url"
      >
        {#snippet children(c)}
          <Input {...c} type="url" spellcheck={false} autocomplete="off" mono bind:value={setup.cardUrl} />
        {/snippet}
      </Field>

      <fieldset class="setup-fieldset">
        <legend>Transports to test <span class="setup-legend-hint">· read from <span class="mono">supportedInterfaces</span></span></legend>
        <div class="transport-choices">
          {#each defaults.transports as t (t.id)}
            <Choice
              variant="card"
              class="transport-choice-input"
              checked={setup.transports.includes(t.id)}
              onchange={() => (setup.transports = toggle<TransportId>(setup.transports, t.id))}
            >
              {#snippet label()}
                <span class="transport-choice-name">{t.label}{#if t.preferred}<span class="preferred-chip">preferred</span>{/if}</span>
              {/snippet}
              {#snippet description()}
                <span>{t.sub}</span><span class="transport-choice-url">{t.url}</span>
              {/snippet}
            </Choice>
          {/each}
        </div>
      </fieldset>

      <fieldset class="setup-fieldset">
        <legend>
          Conversations to replay
          <span class="setup-legend-hint">
            · {setup.conversations.length} of {defaults.conversations.length} selected · from <span class="mono">pastry-concierge-1.0-examples.yaml</span>
          </span>
        </legend>
        <div class="conversation-list">
          {#each defaults.conversations as c (c.id)}
            <label class="conversation-option">
              <input
                type="checkbox"
                class="checkbox"
                checked={setup.conversations.includes(c.id)}
                onchange={() => (setup.conversations = toggle(setup.conversations, c.id))}
              />
              <span class="conversation-option-id">{c.id}</span>
              <span class="conversation-option-skill"><span class="muted">skill</span><span class="mono" data-none={c.skillId === '(none)' ? '' : undefined}>{c.skillId}</span></span>
              <span class="conversation-option-turns">{c.turns}</span>
              <span class="conversation-option-expect"><span class="muted">expects</span><span class="mono">{c.expect}</span></span>
            </label>
          {/each}
        </div>
      </fieldset>
    </div>

    <div class="setup-col" data-col="side">
      <Field label="Secret" help={secret?.help} class={secret?.matches ? 'setup-secret' : 'setup-secret setup-secret-warn'}>
        {#snippet children(c)}
          <Select {...c} bind:value={setup.secret}>
            {#each defaults.secrets as s (s.name)}<option value={s.name}>{s.name || 'No secret'}</option>{/each}
          </Select>
        {/snippet}
      </Field>
      <Field
        error={timeoutOk ? undefined : 'Enter a number of milliseconds greater than 0.'}
        help={timeoutOk ? 'Per agent message, not per conversation. LLM agents can take several seconds to answer.' : undefined}
        class="setup-timeout"
      >
        {#snippet label()}Timeout per message <span class="muted setup-unit">(ms)</span>{/snippet}
        {#snippet children(c)}
          <Input {...c} mono inputmode="numeric" bind:value={timeout} />
        {/snippet}
      </Field>
      <fieldset class="setup-compare">
        <legend>Compare agent text loosely</legend>
        <p>
          LLM agents rephrase their answers from one call to the next, so Microcks never checks agent text for strict equality. Choose how the expected text
          of each conversation is matched.
        </p>
        <div class="setup-modes">
          {#each MODES as m (m.value)}
            <Choice kind="radio" variant="card" name="text-mode" value={m.value} bind:group={setup.textMode}>
              {#snippet label()}{m.label}{/snippet}
              {#snippet description()}<span class="mono">{m.example}</span>{/snippet}
            </Choice>
          {/each}
        </div>
        <Choice variant="card" bind:checked={setup.dataSchema}>
          {#snippet label()}Validate <span class="mono">data</span> parts with JSON Schema{/snippet}
          {#snippet description()}Structured answers such as <span class="mono">{'{name, price, reason}'}</span> are checked for shape, not for exact values.{/snippet}
        </Choice>
      </fieldset>
      <div class="setup-run">
        <Button type="submit" variant="primary" icon="play" disabled={Boolean(problem) || busy} aria-describedby="setup-run-help">Run test</Button>
        <span id="setup-run-help" class="setup-run-help" data-problem={problem ? '' : undefined}>{problem ?? setupSummary(merged)}</span>
      </div>
    </div>
  </form>
</section>
