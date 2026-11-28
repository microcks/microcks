import type { SessionCall } from './types.js';

/*
 * Verification sessions of imported documents (stories S15, S16, S17): what Microcks records for a session and says in
 * its verification (`POST /api/arazzo/sessions/{id}/verification`), mapped to the rows of the Sessions tab and the run page.
 */

/** A call of the `session` of a verification result, in its order of receipt. */
export interface BackendSessionCall {
  index: number;
  apiName: string;
  apiVersion: string;
  method: string;
  path: string;
  /** The step the call was matched to; absent when it matches none. */
  stepId?: string | null;
  /** Why the call fails the verification (a call no remaining step expects). */
  failure?: string | null;
}

/** The part of a step of the verification this module reads. */
export interface BackendSessionStep {
  stepId: string;
  status: string;
  statusCode?: number | null;
  message?: string | null;
}

/** What a step of the verification says about the values its call carried: "... should come from `$steps...` ...". */
export const dataFailureOf = (message: string | null | undefined): string | undefined =>
  message?.split('; ').find((m) => m.includes(' should come from ')) ?? undefined;

/** What a step of the verification says about the contract of its API: "... request does not conform to ...". */
export const contractFailureOf = (message: string | null | undefined): string | undefined =>
  message?.split('; ').find((m) => m.includes(' does not conform to ')) ?? undefined;

const DATA = /`[^`]+` (?:path|query|header|body) `\/?([^`]+)` should come from `([^`]+)` \((.*)\), got (.*)$/;

/** A failure of a step of the verification, as the Sessions tab and the run page tell it. */
export function failureTextOf(stepId: string, message: string): string {
  if (message === `step \`${stepId}\` missing`) return 'no call received for this step';
  const data = DATA.exec(dataFailureOf(message) ?? '');
  if (data) return `data not taken from an earlier step: ${stepId} called with ${data[1]}=${data[4]}, expected ${data[2]} = ${data[3]}`;
  const contract = contractFailureOf(message);
  if (contract) return `breaks the contract: ${contract.replace(/^`[^`]+` /, '')}`;
  return message;
}

/** The calls of a session as the table shows them: their step (`unexpected` for none), the status the mock answered. */
export function sessionCallsOf(calls: readonly BackendSessionCall[], steps: readonly BackendSessionStep[]): SessionCall[] {
  return [...calls]
    .sort((a, b) => a.index - b.index)
    .map((c) => {
      const step = c.stepId ? steps.find((s) => s.stepId === c.stepId) : undefined;
      const base = { n: c.index + 1, method: c.method, path: c.path, httpStatus: step?.statusCode ?? 0 };
      if (!step) return { ...base, stepId: 'unexpected', ...(c.failure ? { failure: c.failure } : {}), contract: { state: 'off' as const, text: '—' }, dataPassing: { state: 'off' as const, text: '—' } };
      const contract = contractFailureOf(step.message);
      const data = dataFailureOf(step.message);
      return {
        ...base,
        stepId: step.stepId,
        contract: contract ? { state: 'fail' as const, text: contract } : { state: 'pass' as const, text: 'conforms' },
        dataPassing: data ? { state: 'fail' as const, text: data } : { state: 'pass' as const, text: 'as the workflow says' }
      };
    });
}

/** Why a verification failed, one line per failed step then per unexpected call, as the Sessions tab lists them. */
export function verificationProblemsOf(
  steps: readonly BackendSessionStep[],
  calls: readonly SessionCall[],
  message: string | null | undefined
): { lead: string; text: string }[] {
  const problems: { lead: string; text: string }[] = [];
  if (message) problems.push({ lead: 'Session:', text: message });
  steps.forEach((s, i) => {
    if (s.status === 'FAILED') problems.push({ lead: `Step ${i + 1}, ${s.stepId}:`, text: s.message ? failureTextOf(s.stepId, s.message) : 'failed' });
  });
  for (const c of calls) {
    const text = c.failure ?? (c.stepId === 'unexpected' ? 'unexpected call' : undefined);
    if (text) problems.push({ lead: `Call #${c.n}, ${c.method} ${c.path}:`, text });
  }
  return problems;
}

/** The steps that follow the step set to fail: the compensation the application is expected to run. */
export function compensationStepsOf(stepIds: readonly string[], failedStepId: string | undefined): string[] {
  const at = failedStepId === undefined ? -1 : stepIds.indexOf(failedStepId);
  return at < 0 ? [] : stepIds.slice(at + 1);
}

/** The path of the mocks of a REST API: its name (with `+` for spaces), its version, then the path of the operation. */
export const restMockPath = (apiName: string, apiVersion: string, path: string) =>
  `/rest/${encodeURIComponent(apiName).replace(/%20/g, '+')}/${encodeURIComponent(apiVersion)}${path}`;

/** A curl to a mock, naming the session as an application does with the `x-microcks-session` header. */
export const sessionCurl = (base: string, method: string, mockPath: string, sessionId: string) =>
  `curl -X ${method} '${base}${mockPath}' \\\n  -H 'x-microcks-session: ${sessionId}'`;

/** An exchange of the examples of an operation, as the Microcks API gives it. */
export interface BackendExampleExchange {
  name?: string;
  response?: { status?: string | null };
}

/** The examples a step can be made to fail with: the examples of its operation, refusals (4xx, 5xx) first. */
export function failureChoicesOf(exchanges: readonly BackendExampleExchange[]): { value: string; code: number }[] {
  const choices = exchanges
    .filter((e): e is BackendExampleExchange & { name: string } => typeof e.name === 'string' && e.name !== '')
    .map((e) => ({ value: e.name, code: Number(e.response?.status) || 200 }));
  return [...choices.filter((c) => c.code >= 400), ...choices.filter((c) => c.code < 400)];
}
