/*
 * PLACEHOLDER: the simulated world the workflow runner calls (Microcks mocks of API Pastry and its staging
 * implementation). Microcks has no Arazzo engine, so a run is played by lib/server/runs-sim.ts against this
 * deterministic data, copied from the Deep Ink prototype. To replace with: the Arazzo execution API of Microcks
 * (proposed `POST /api/workflows/documents/{id}/workflows/{wf}/runs`, `GET /api/workflows/runs/{id}`), which would call the
 * real mocks (/rest/API+Pastry/2.0/…) or the implementation base URLs.
 *
 * Mocks answer from recorded examples only. The implementation knows more pastries, but its PATCH has the bug of the
 * story: `price` comes back as the string "3.9" while the success criteria hold.
 */
import { resolveExpression, type EvaluationContext } from '#lib/workflows/expression.js';
import type { RunOptions, RunTarget, Step } from '#lib/workflows/types.js';
import type { SimulatedResponder, SimulatedResponse } from '#lib/server/runs-sim.js';
import { MICROCKS_BASE, MOCK_BASE, STAGING_BASE } from './workflows.js';

interface Pastry {
  name: string;
  description: string;
  size: 'S' | 'M' | 'L';
  price: number;
  status: string;
}

const PASTRIES: Pastry[] = [
  { name: 'Tarte Tatin', description: 'Caramelised apple tart, upside down', size: 'S', price: 3.2, status: 'unknown' },
  { name: 'Tartelette Framboise', description: 'Raspberry tartlet', size: 'S', price: 2.9, status: 'unknown' },
  { name: 'Eclair Cafe', description: 'Delicious Coffee Eclair, not caloric at all', size: 'M', price: 2.4, status: 'unknown' },
  { name: 'Eclair Chocolat', description: 'Delicious Chocolate Eclair', size: 'M', price: 2.5, status: 'available' },
  { name: 'Millefeuille', description: 'Layers of puff pastry and cream', size: 'L', price: 4.4, status: 'available' }
];

/** GetPastryByName examples recorded in API Pastry 2.0 (the mocks answer these names only). */
const MOCK_EXAMPLE_NAMES = ['Millefeuille', 'Eclair Cafe', 'Eclair Chocolat', 'Tartelette Framboise'];

const SCHEMA_REF = 'PatchPastry · 200 · application/json · Pastry';
const SCHEMA_MESSAGE =
  '/price does not match the Pastry schema: instance type (string) does not match any allowed primitive type (allowed: ["integer","number"])';

const PATCH_MS = [66, 58, 61];

type Where = 'mock' | 'implementation';

function whereOf(target: RunTarget, sourceName: string): Where {
  if (target.kind === 'mocks') return 'mock';
  if (target.kind === 'implementations') return 'implementation';
  return target.perSource[sourceName] ?? 'mock';
}

/** Base URL a source is called on, for this run. */
export function baseUrlFor(target: RunTarget, sourceName: string): string {
  if (whereOf(target, sourceName) === 'implementation') {
    const given = target.kind === 'implementations' ? target.baseUrls[sourceName] : undefined;
    return (given && given.trim()) || STAGING_BASE;
  }
  if (sourceName === 'apiPastryV1') return MOCK_BASE.replace('/2.0', '/1.0');
  if (sourceName === 'apiPastry') return MOCK_BASE;
  return MICROCKS_BASE;
}

function param(step: Step, name: string, ctx: EvaluationContext): string | undefined {
  const p = step.parameters.find((x) => x.name === name);
  if (!p) return undefined;
  const value = resolveExpression(p.value, ctx);
  return value === undefined ? undefined : String(value);
}

const asBody = (p: Pastry, overrides: Partial<Record<keyof Pastry, unknown>> = {}) => ({
  name: p.name,
  description: p.description,
  size: p.size,
  price: p.price,
  status: p.status,
  ...overrides
});

export function createResponder(target: RunTarget, options: Pick<RunOptions, 'inputs'>): SimulatedResponder {
  return (step, attempt, ctx): SimulatedResponse => {
    const impl = whereOf(target, step.sourceName) === 'implementation';
    const route = `${step.method} ${step.path}`;
    const schema = impl ? ('pass' as const) : undefined;

    if (route === 'GET /pastries') {
      const size = param(step, 'size', ctx);
      const list = PASTRIES.filter((p) => p.size === size);
      if (list.length === 0) return { statusCode: 400, ms: impl ? 38 : 4, body: { reason: `size ${size ?? ''} is not one of S, M, L` }, schema };
      return { statusCode: 200, ms: impl ? 41 : 6, body: list.map((p) => asBody(p)), schema };
    }

    if (route === 'GET /pastries/{name}') {
      const name = param(step, 'name', ctx);
      const pastry = PASTRIES.find((p) => p.name === name);
      const known = impl ? pastry !== undefined : pastry !== undefined && MOCK_EXAMPLE_NAMES.includes(pastry.name);
      if (!pastry || !known) {
        return impl
          ? { statusCode: 404, ms: 31, body: { reason: `No pastry named ${name ?? ''}` }, schema }
          : { statusCode: 400, ms: 4, missingExample: { description: `an example of ${step.operationId} for name=${name ?? ''}`, hint: name ?? '' } };
      }
      const patched = ctx.steps?.['patchPastry'];
      const body = patched ? asBody(pastry, { status: 'available', price: patched.outputs['newPrice'] ?? pastry.price }) : asBody(pastry);
      return { statusCode: 200, ms: impl ? 29 : 5, body, schema };
    }

    if (route === 'PATCH /pastries/{name}') {
      const name = param(step, 'name', ctx);
      const pastry = PASTRIES.find((p) => p.name === name);
      if (!pastry) return { statusCode: impl ? 404 : 400, ms: 4, body: { reason: `No pastry named ${name ?? ''}` }, schema };
      const newPrice = options.inputs['newPrice'];
      const price = typeof newPrice === 'number' ? newPrice : pastry.price;
      if (impl) {
        return {
          statusCode: 200,
          ms: PATCH_MS[attempt - 1] ?? 60,
          body: asBody(pastry, { price: String(price), status: 'available' }),
          schema: 'fail',
          badKey: 'price',
          badNote: 'schema: number',
          schemaRef: SCHEMA_REF,
          schemaMessage: SCHEMA_MESSAGE
        };
      }
      return { statusCode: 200, ms: 7, body: asBody(pastry, { price, status: 'available' }) };
    }

    return { statusCode: 200, ms: impl ? 40 : 6, body: {}, schema };
  };
}
