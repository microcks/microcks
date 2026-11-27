import type { MockCoherenceIssue, StepResult, StepStatus } from '#lib/workflows/types.js';

/*
 * Pure mappings of what the backend says about response schema validation (S9) and missing mock examples (S10) to the
 * run model of the Deep Ink mockup. The texts are the ones of the simulator (lib/server/runs-sim.ts).
 */

/** A missing mock example, as the backend reports it on a run (only for a failed step of a run on the mocks). */
export interface BackendMissingExample {
  stepIndex?: number;
  stepId: string;
  serviceId?: string | null;
  operationId: string;
  operationName?: string | null;
  parameters?: { name: string; in?: string; value: unknown; expression?: string | null }[] | null;
}

const SCHEMA_VIOLATION = /does not match the .* schema|is not described in the OpenAPI contract/;
const NO_CONTRACT = /^No OpenAPI contract of source/;

/** The "Schema validation" state of a step, from the run option, the step outcome and the reason the backend gave. */
export function schemaOf(
  validate: boolean,
  status: StepStatus,
  message: string | null | undefined,
  operationId: string,
  statusCode: number | null | undefined
): StepResult['schema'] {
  if (!validate) return { state: 'off', ref: '', message: 'Off for this run: the step is judged on its success criteria only.' };
  if (status === 'not-run') return { state: 'off', ref: '', message: 'Not run.' };
  const ref = `${operationId} · ${statusCode ?? '?'} · application/json`;
  if (message && NO_CONTRACT.test(message)) return { state: 'na', ref: '', message };
  if (message && SCHEMA_VIOLATION.test(message)) return { state: 'fail', ref, message };
  if (status === 'passed') return { state: 'pass', ref, message: 'The response conforms to its schema.' };
  return { state: 'na', ref: '', message: 'The step failed before its response was judged against the schema.' };
}

/** The mock example a failed step needed and did not find ("an example of GetPastryByName for name=Tarte Tatin"). */
export function coherenceOf(missing: BackendMissingExample | null | undefined): MockCoherenceIssue[] {
  if (!missing) return [];
  const values = (missing.parameters ?? []).map((p) => `${p.name}=${typeof p.value === 'string' ? p.value : JSON.stringify(p.value)}`);
  return [
    {
      stepId: missing.stepId,
      operationId: missing.operationId,
      missing: `an example of ${missing.operationId}${values.length ? ` for ${values.join(', ')}` : ''}`,
      copilotHint: String(missing.parameters?.[0]?.value ?? '')
    }
  ];
}
