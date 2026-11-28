import { createResponder, baseUrlFor } from '#lib/placeholders/runs-world.js';
import { RUN_IDS, SECRET_STAGING, TEST_RUN_IDS, scenarioRun } from '#lib/placeholders/workflows.js';
import {
  defaultsFor,
  isRunScenario,
  timeoutOrDefault,
  typedInputs,
  validateRunRequest,
  type FieldError,
  type LinkedService,
  type RunSetupPayload,
  type StartRunInput,
  type WorkflowRunPayload
} from '#lib/runs-workflow.js';
import type { RunOptions, RunTarget, Workflow, WorkflowRun } from '#lib/workflows/types.js';
import { cachedSecrets } from './admin';
import { simulateRun } from './runs-sim';
import { getService } from './store';
import { addRun, getDetail, getDocument, getRun, getWorkflowDetail, isPlaceholderDocument, listAll, listWorkflowsPayload, nextRunNumber } from './workflows';
import { getRealRunPayload, startRealRun } from './runs-arazzo';

/*
 * Workflow runs BFF. PLACEHOLDER: Microcks has no Arazzo engine or API, so a run is simulated by ./runs-sim against the
 * demo world of lib/placeholders/runs-world.ts and kept in the workflows store. Real counterparts (proposed, not in
 * Microcks): POST /api/workflows/documents/{id}/workflows/{wf}/runs and GET /api/workflows/runs/{id}.
 */

export async function getRunSetup(docId: string, workflowId: string, scenario: string | null, token?: string): Promise<RunSetupPayload | undefined> {
  const real = !isPlaceholderDocument(docId);
  const detail = real ? await getWorkflowDetail(docId, workflowId, token) : getDetail(docId, workflowId);
  if (!detail) return undefined;
  const { workflow, document, examples } = detail;
  const services: Record<string, LinkedService> = {};
  for (const src of document.sources) {
    if (src.link.status !== 'linked') continue;
    const s = getService(src.link.serviceId);
    if (s) services[src.name] = { id: s.id, name: s.name, version: s.version, protocol: s.protocol as LinkedService['protocol'] };
  }
  const secrets = cachedSecrets().map((s) => ({ id: s.id, name: s.name }));
  // The restock-pastry scenario selects this secret by name; the demo secret is offered even when the store has none.
  if (!real && !secrets.some((s) => s.name === SECRET_STAGING)) secrets.push({ id: 'demo-secret', name: SECRET_STAGING });
  const all = real ? await listWorkflowsPayload(token) : listAll();
  return {
    document,
    workflow,
    workflows: all.workflows,
    documents: all.documents,
    examples,
    secrets,
    defaults: defaultsFor(workflow, isRunScenario(scenario) ? scenario : undefined, real),
    services,
    demo: !real
  };
}

/** The seeded run a request replays, so the links of the story (run #14 ↔ test run #119) stay stable. */
function canonicalRunId(workflowId: string, target: RunTarget, options: RunOptions): string | undefined {
  if (workflowId === 'check-menu' && target.kind === 'mocks') return RUN_IDS.checkMenu3;
  if (workflowId !== 'restock-pastry') return undefined;
  if (target.kind === 'implementations' && options.example === 'restock-eclair' && options.validateSchemas && options.inputs['newPrice'] === 3.9) return RUN_IDS[14];
  if (target.kind === 'mocks' && options.example === 'already-available') return RUN_IDS[15];
  if (target.kind === 'mocks' && !options.example && options.inputs['size'] === 'S') return RUN_IDS[16];
  return undefined;
}

const newRunId = () => Array.from({ length: 24 }, () => Math.floor(Math.random() * 16).toString(16)).join('');

export type StartResult = { runId: string } | { status: 404 | 422; error: string; errors?: FieldError[] };

export async function startWorkflowRun(input: StartRunInput, token?: string): Promise<StartResult> {
  if (!isPlaceholderDocument(input.documentId)) {
    const real = await getWorkflowDetail(input.documentId, input.workflowId, token);
    if (!real) return { status: 404, error: `Workflow "${input.workflowId}" does not exist in document "${input.documentId}".` };
    return startRealRun(input, real.workflow, token);
  }
  const detail = getDetail(input.documentId, input.workflowId);
  if (!detail) return { status: 404, error: `Workflow "${input.workflowId}" does not exist in document "${input.documentId}".` };
  const { workflow } = detail;
  const sources = [...new Set(workflow.steps.map((s) => s.sourceName))];
  const errors = validateRunRequest({
    workflowId: workflow.workflowId,
    notRunnableMessage: workflow.runnability.runnable ? undefined : workflow.runnability.message,
    target: input.target,
    sources,
    baseUrls: input.baseUrls ?? {},
    perSource: input.perSource,
    inputs: workflow.inputs.map((i) => ({ name: i.name, type: i.type, value: String(input.inputs?.[i.name] ?? '') })),
    timeoutMs: String(input.timeoutMs ?? '')
  });
  if (errors.length > 0) return { status: 422, error: errors[0]!.message, errors };

  const target: RunTarget =
    input.target === 'mocks'
      ? { kind: 'mocks' }
      : input.target === 'implementations'
        ? { kind: 'implementations', baseUrls: input.baseUrls }
        : { kind: 'mixed', perSource: input.perSource ?? {}, proposed: true };
  const options: RunOptions = {
    example: input.example || undefined,
    inputs: typedInputs(workflow.inputs, input.inputs ?? {}),
    secret: input.target !== 'mocks' && input.secret ? input.secret : undefined,
    timeoutMs: timeoutOrDefault(String(input.timeoutMs)),
    validateSchemas: Boolean(input.validateSchemas)
  };

  const known = canonicalRunId(workflow.workflowId, target, options);
  if (known && !getRun(known)) {
    const scenario = scenarioRun(known, new Date().toISOString());
    if (scenario) addRun(scenario);
  }
  if (known && getRun(known)) return { runId: known };

  const runId = newRunId();
  const run = simulateRun({
    runId,
    number: nextRunNumber(workflow.documentId, workflow.workflowId),
    workflow,
    origin: { kind: 'ui' },
    target,
    options,
    startedAt: new Date().toISOString(),
    respond: createResponder(target, options),
    baseUrl: (source) => baseUrlFor(target, source)
  });
  const failedOnSchema = run.steps.some((s) => s.schema.state === 'fail');
  // Same bug as test run #119: the staging API breaks its schema on patchPastry.
  addRun(failedOnSchema && workflow.workflowId === 'restock-pastry' ? { ...run, relatedTestRunId: TEST_RUN_IDS.failed } : run);
  return { runId };
}

export async function getWorkflowRunPayload(runId: string, token?: string): Promise<WorkflowRunPayload | undefined> {
  const run: WorkflowRun | undefined = getRun(runId) ?? scenarioRun(runId);
  if (!run) return getRealRunPayload(runId, (docId, workflowId) => getWorkflowDetail(docId, workflowId, token), token);
  const detail = getDetail(run.documentId, run.workflowId);
  return {
    run,
    document: detail?.document ?? getDocument(run.documentId),
    workflow: detail?.workflow as Workflow | undefined,
    examples: detail?.examples ?? []
  };
}
