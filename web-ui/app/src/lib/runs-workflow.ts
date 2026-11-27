import type { ArazzoDocument, Workflow, WorkflowExample, WorkflowInput, WorkflowRun, StepResult, SourceProtocol } from '#lib/workflows/types.js';
import { DEFAULT_RUN_TIMEOUT_MS } from '#lib/workflows/types.js';
import { stoppedStep } from '#lib/workflows/describe.js';

/*
 * Pure, client-safe model of the workflow run pages (setup form and run view), ported from the Deep Ink demo
 * (`domain/arazzo/run-request`, `progress`, `application/arazzo/run-scenarios`). Execution is a simulation done by the
 * BFF (see lib/server/runs-sim.ts): Microcks has no Arazzo engine yet.
 */

export const EXAMPLE_BASE_URL = 'https://pastry.staging.acme.io/v2';

export type RunTargetKind = 'mocks' | 'implementations' | 'mixed';
export type Where = 'mock' | 'implementation';

export interface RunRequestInput {
  name: string;
  type: WorkflowInput['type'];
  value: string;
}

export interface RunRequestForm {
  workflowId: string;
  notRunnableMessage?: string;
  target: RunTargetKind;
  sources: string[];
  baseUrls: Record<string, string>;
  perSource?: Record<string, Where>;
  inputs: RunRequestInput[];
  timeoutMs: string;
}

export interface FieldError {
  /** `workflowId`, `baseUrl.<source>`, `input.<name>` or `timeoutMs`. */
  field: string;
  message: string;
}

const NUMBER = /^-?\d+(\.\d+)?$/;
const INTEGER = /^-?\d+$/;
const ABSOLUTE_URL = /^https?:\/\/[^\s/]+\S*$/;

/** Validates the run form; errors come in field order. `[]` means valid. */
export function validateRunRequest(form: RunRequestForm): FieldError[] {
  const errors: FieldError[] = [];
  if (form.notRunnableMessage) errors.push({ field: 'workflowId', message: form.notRunnableMessage });

  const needsUrl = (source: string) => form.target === 'implementations' || (form.target === 'mixed' && form.perSource?.[source] === 'implementation');
  for (const source of form.sources) {
    if (!needsUrl(source)) continue;
    const url = (form.baseUrls[source] ?? '').trim();
    if (!url) errors.push({ field: `baseUrl.${source}`, message: `Enter the base URL of the ${source} implementation.` });
    else if (!ABSOLUTE_URL.test(url)) errors.push({ field: `baseUrl.${source}`, message: `Enter an absolute http(s) URL, such as ${EXAMPLE_BASE_URL}.` });
  }

  for (const input of form.inputs) {
    const text = input.value.trim();
    if (text === '') continue;
    if (input.type === 'number' && !NUMBER.test(text)) {
      errors.push({
        field: `input.${input.name}`,
        message: text.includes(',') ? `Use a dot as decimal separator (3.9): ${input.name} is a JSON number.` : `${input.name} must be a number.`
      });
    } else if (input.type === 'integer' && !INTEGER.test(text)) {
      errors.push({ field: `input.${input.name}`, message: `${input.name} must be a whole number.` });
    }
  }

  const timeout = form.timeoutMs.trim();
  if (!(/^\d+$/.test(timeout) && parseInt(timeout, 10) >= 1)) {
    errors.push({ field: 'timeoutMs', message: 'Enter a whole number of milliseconds, 1 or more.' });
  }
  return errors;
}

export function timeoutOrDefault(text: string): number {
  const n = parseInt(text.trim(), 10);
  return Number.isFinite(n) && n >= 1 ? n : DEFAULT_RUN_TIMEOUT_MS;
}

/** Typed inputs of a run from the raw text of the form; blank fields are left out. */
export function typedInputs(inputs: readonly WorkflowInput[], raw: Record<string, string>): Record<string, unknown> {
  return Object.fromEntries(
    inputs.flatMap((i): [string, unknown][] => {
      const text = (raw[i.name] ?? '').trim();
      if (text === '') return [];
      if (i.type === 'number' || i.type === 'integer') return [[i.name, Number(text)]];
      if (i.type === 'boolean') return [[i.name, text === 'true']];
      return [[i.name, text]];
    })
  );
}

/** Body of POST /api/runs/workflow-runs (the BFF of the form). */
export interface StartRunInput {
  documentId: string;
  workflowId: string;
  target: RunTargetKind;
  baseUrls: Record<string, string>;
  perSource?: Record<string, Where>;
  secret?: string;
  example?: string;
  inputs: Record<string, string>;
  validateSchemas: boolean;
  timeoutMs: number;
}

/* ------------------------------------------------------------------------------------ scenarios */

export type RunScenario = 'impl14' | 'mocksEnd' | 'mocksMissing';

export interface RunFormDefaults {
  target: RunTargetKind;
  baseUrls: Record<string, string>;
  perSource: Record<string, Where>;
  secret: string;
  example: string;
  detached: boolean;
  inputs: Record<string, string>;
  validateSchemas: boolean;
  timeoutMs: string;
}

export const SCENARIO_RUN_IDS = {
  impl14: '6729a1d27e4c0b5f93a8c214',
  mocksEnd: '6729a3f08b1d6e2c45f7a930',
  mocksMissing: '6729a4b15c9e7d3a28b0f6e1'
} as const;

const SECRET_STAGING = 'pastry-staging-oauth';

/** The three prototype scenarios of restock-pastry, selected with `?scenario=` and linked by the run page. */
export const RUN_SCENARIOS: Record<RunScenario, { label: string; runNumber: number; runId: string; defaults: RunFormDefaults }> = {
  impl14: {
    label: 'Implementations #14',
    runNumber: 14,
    runId: SCENARIO_RUN_IDS.impl14,
    defaults: {
      target: 'implementations',
      baseUrls: { apiPastry: EXAMPLE_BASE_URL },
      perSource: { apiPastry: 'implementation' },
      secret: SECRET_STAGING,
      example: 'restock-eclair',
      detached: false,
      inputs: { size: 'M', newPrice: '3.9' },
      validateSchemas: true,
      timeoutMs: String(DEFAULT_RUN_TIMEOUT_MS)
    }
  },
  mocksEnd: {
    label: 'Mocks · ended early',
    runNumber: 15,
    runId: SCENARIO_RUN_IDS.mocksEnd,
    defaults: {
      target: 'mocks',
      baseUrls: { apiPastry: EXAMPLE_BASE_URL },
      perSource: { apiPastry: 'mock' },
      secret: '',
      example: 'already-available',
      detached: false,
      inputs: { size: 'L', newPrice: '' },
      validateSchemas: false,
      timeoutMs: String(DEFAULT_RUN_TIMEOUT_MS)
    }
  },
  mocksMissing: {
    label: 'Mocks · missing example',
    runNumber: 16,
    runId: SCENARIO_RUN_IDS.mocksMissing,
    defaults: {
      target: 'mocks',
      baseUrls: { apiPastry: EXAMPLE_BASE_URL },
      perSource: { apiPastry: 'mock' },
      secret: '',
      example: '',
      detached: true,
      inputs: { size: 'S', newPrice: '4.2' },
      validateSchemas: false,
      timeoutMs: String(DEFAULT_RUN_TIMEOUT_MS)
    }
  }
};

export const isRunScenario = (v: string | null | undefined): v is RunScenario => v === 'impl14' || v === 'mocksEnd' || v === 'mocksMissing';

export const scenarioOf = (runId: string): RunScenario | undefined => (Object.keys(RUN_SCENARIOS) as RunScenario[]).find((k) => RUN_SCENARIOS[k].runId === runId);

const SAMPLE_INPUTS: Record<string, string> = { name: 'Eclair Cafe', quantity: '2', beerName: 'Pilsner' };

const baseUrlOf = (source: string) => (source === 'apiPastry' ? EXAMPLE_BASE_URL : source === 'apiPastryV1' ? EXAMPLE_BASE_URL.replace(/\/v2$/, '/v1') : '');

/** Initial values of the form: restock-pastry follows its scenario; any other workflow starts on the mocks. */
export function defaultsFor(workflow: Pick<Workflow, 'workflowId' | 'steps' | 'inputs'>, scenario?: RunScenario): RunFormDefaults {
  if (workflow.workflowId === 'restock-pastry') return RUN_SCENARIOS[scenario ?? 'impl14'].defaults;
  const sources = [...new Set(workflow.steps.map((s) => s.sourceName))];
  return {
    target: 'mocks',
    baseUrls: Object.fromEntries(sources.map((s) => [s, baseUrlOf(s)])),
    perSource: Object.fromEntries(sources.map((s) => [s, 'mock' as const])),
    secret: '',
    example: '',
    detached: true,
    inputs: Object.fromEntries(workflow.inputs.map((i) => [i.name, i.example ?? SAMPLE_INPUTS[i.name] ?? ''])),
    validateSchemas: true,
    timeoutMs: String(DEFAULT_RUN_TIMEOUT_MS)
  };
}

/* -------------------------------------------------------------------------------------- payloads */

export interface LinkedService {
  id: string;
  name: string;
  version: string;
  protocol: SourceProtocol;
}

/** GET /api/runs/workflow-setup/:docId/:workflowId */
export interface RunSetupPayload {
  document: ArazzoDocument;
  workflow: Workflow;
  workflows: Workflow[];
  examples: WorkflowExample[];
  secrets: { id: string; name: string }[];
  defaults: RunFormDefaults;
  /** Real services of the store, by source description name. */
  services: Record<string, LinkedService>;
  /** False for a workflow of an imported document (Microcks API); placeholder data otherwise. */
  demo?: boolean;
}

/** GET /api/runs/workflow-runs/:id */
export interface WorkflowRunPayload {
  /** False for a run executed by Microcks (Arazzo minion); simulated run of placeholder data otherwise. */
  demo?: boolean;
  run: WorkflowRun;
  document?: ArazzoDocument;
  workflow?: Workflow;
  examples: WorkflowExample[];
}

/* ---------------------------------------------------------------------------------------- frames */

const blank = (step: StepResult): StepResult => ({
  ...step,
  status: 'not-run',
  attempts: [],
  request: undefined,
  response: undefined,
  criteria: [],
  outputs: {},
  schema: { ...step.schema, state: 'off' },
  flow: { kind: 'next', title: '', text: '' }
});

/**
 * The successive states of a run while it executes, derived from its final result: one frame per attempt, the last
 * frame being the final run. Steps that never ran are not part of the sequence.
 */
export function runFrames(run: WorkflowRun): WorkflowRun[] {
  const executed = run.steps.filter((s) => s.status !== 'not-run');
  if (executed.length === 0) return [run];
  const frames: WorkflowRun[] = [];
  const settled = new Map<string, StepResult>();

  const frame = (current: WorkflowRun['current'], partial?: StepResult): WorkflowRun => ({
    ...run,
    status: 'RUNNING',
    outputs: {},
    current,
    steps: run.steps.map((s) => settled.get(s.stepId) ?? (partial && partial.stepId === s.stepId ? partial : blank(s)))
  });

  frames.push(frame({ stepId: executed[0]!.stepId, attempt: 1 }));
  executed.forEach((step, index) => {
    for (let k = 1; k < step.attempts.length; k++) {
      frames.push(frame({ stepId: step.stepId, attempt: k + 1 }, { ...blank(step), attempts: step.attempts.slice(0, k) }));
    }
    settled.set(step.stepId, step);
    const next = executed[index + 1];
    if (next) frames.push(frame({ stepId: next.stepId, attempt: 1 }));
  });
  frames.push(run);
  return frames;
}

/* ------------------------------------------------------------------------------------ view helpers */

export type Phase = 'done' | 'running' | 'waiting';

export function phaseOf(run: WorkflowRun, index: number): Phase {
  if (run.status !== 'RUNNING') return 'done';
  const currentIndex = run.steps.findIndex((s) => s.stepId === run.current?.stepId);
  if (currentIndex < 0) return run.steps[index]?.status === 'not-run' ? 'waiting' : 'done';
  return index < currentIndex ? 'done' : index === currentIndex ? 'running' : 'waiting';
}

export const implementationsText = (baseUrls: Record<string, string>): string =>
  Object.entries(baseUrls)
    .map(([source, url]) => `${source} → ${url}`)
    .join(' · ');

export interface RunApiBody {
  inputs: Record<string, unknown>;
  timeout: number;
  implementations?: Record<string, string>;
  /** Mixed run: where each source goes; `implementations` then holds the sources sent to their implementation. */
  target?: { kind: 'mixed'; perSource: Record<string, 'mock' | 'implementation'> };
  secretName?: string;
  validateSchema: boolean;
  example?: string;
}

export function apiBodyOf(run: WorkflowRun): RunApiBody {
  return {
    inputs: run.options.inputs,
    timeout: run.options.timeoutMs,
    ...(run.target.kind === 'implementations' ? { implementations: run.target.baseUrls } : {}),
    ...(run.target.kind === 'mixed'
      ? { implementations: run.target.baseUrls ?? {}, target: { kind: 'mixed' as const, perSource: run.target.perSource } }
      : {}),
    ...(run.target.kind !== 'mocks' && run.options.secret ? { secretName: run.options.secret } : {}),
    validateSchema: run.options.validateSchemas,
    ...(run.options.example ? { example: run.options.example } : {})
  };
}

export function launchedBy(run: WorkflowRun): string {
  if (run.origin.kind === 'session') return run.origin.app;
  if (run.origin.kind === 'api') return 'API';
  if (run.origin.kind === 'mock-url') return 'Mock URL';
  return 'MA';
}

export function targetLine(run: WorkflowRun): string {
  if (run.target.kind === 'mocks') return 'Microcks mocks';
  if (run.target.kind === 'implementations') return `Implementations · ${implementationsText(run.target.baseUrls)}`;
  if (run.target.proposed) return 'Mixed · proposed, beyond POC';
  const where = mixedText(run.target.perSource, run.target.baseUrls ?? {});
  return where ? `Mixed · ${where}` : 'Mixed';
}

/** Where each source of a mixed run goes: `customerApi → https://…, orderApi → its mocks`. */
export function mixedText(perSource: Record<string, 'mock' | 'implementation'>, baseUrls: Record<string, string>): string {
  return Object.entries(perSource)
    .map(([name, where]) => `${name} → ${where === 'implementation' ? (baseUrls[name] ?? 'its implementation') : 'its mocks'}`)
    .join(', ');
}

export function originText(run: WorkflowRun): string {
  if (run.origin.kind === 'session') return `Session ${run.origin.sessionId}`;
  if (run.origin.kind === 'ui') return 'UI · Run workflow';
  if (run.origin.kind === 'api') return 'API';
  return 'Mock URL';
}

/** "apiPastry · /rest/API+Pastry/2.0": the mock endpoint of each source a mocks run calls. */
export function mockSources(run: WorkflowRun): string {
  return [...new Set(run.steps.flatMap((s) => (s.request ? [s.request.url] : [])))]
    .map((u) => u.match(/^https?:\/\/[^/]+(\/rest\/[^/]+\/[^/]+)/)?.[1])
    .filter((x, i, all): x is string => !!x && all.indexOf(x) === i)
    .map((p) => `apiPastry · ${p}`)
    .join(' · ');
}

/** JSON-ish value as the prototype prints it. */
export const J = (v: unknown): string => (v === undefined ? '—' : JSON.stringify(v));

/** "patchPastry failed after 3 attempts and ended the run". */
export function stopText(run: WorkflowRun): string {
  const stopped = stoppedStep(run);
  if (!stopped) return 'the run ended';
  if (stopped.status === 'passed') return `onSuccess end of ${stopped.stepId} ended the run`;
  const n = stopped.attempts.length;
  return `${stopped.stepId} failed${n > 1 ? ` after ${n} attempts` : ''} and ended the run`;
}

/** The field a bad response line breaks, in words: `"price": "3.9",` → "/price is a string". */
export function badFieldText(step: StepResult): string {
  const bad = step.response?.lines.find((l) => l.bad);
  const m = bad?.text.match(/"([^"]+)":\s*(.+?),?$/);
  if (!m) return 'see the response';
  const value = m[2] ?? '';
  const kind = value.startsWith('"') ? 'a string' : value === 'null' ? 'null' : value.startsWith('{') ? 'an object' : value.startsWith('[') ? 'an array' : 'a number';
  return `/${m[1]} is ${kind}`;
}
