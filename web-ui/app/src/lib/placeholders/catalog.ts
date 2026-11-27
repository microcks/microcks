// PLACEHOLDER: demo data for the catalog pages. No LLM, test, metric or trace backend exists yet.
// Real equivalents in Microcks: GET /api/tests (test runs), GET /api/metrics/conformance/*,
// GET /api/metrics/invocations/* (traffic), and the Microcks AI Copilot (/api/copilot/samples).
import type { CatalogExample, CatalogOperation, Proposal } from '#lib/components/catalog/types.js';

export const PLACEHOLDER_NOTE = 'Demo data: test runs, conformance and traffic are not recorded by this preview yet.';

/** Seven daily call counts for the sparkline, derived from the operation name so they stay stable. */
export function demoCalls(seed: string): number[] {
  let h = 7;
  for (const c of seed) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return Array.from({ length: 7 }, (_, i) => 20 + ((h >>> (i * 3)) % 60));
}

export const DEMO_BARS = Array.from({ length: 24 }, (_, i) => 18 + ((i * 37) % 70));

export const DEMO_TESTS_7D = { passed: 12, failed: 3 };
export const DEMO_AVG_CONFORMANCE = 78;
export const DEMO_HOURS = [5, 1, 2, 1, 1, 6, 15, 40, 92, 121, 165, 125];
/** Calls "today" for one service, stable for a given id. */
export const demoCallsToday = (id: string) => demoCalls(id).reduce((a, b) => a + b, 0) * 3;

export const DEMO_RECENT_TESTS = [
  { id: 'demo-1', label: 'Conformance · REST mock', result: 'passed', when: '12 min ago' },
  { id: 'demo-2', label: 'Non-regression · gRPC', result: 'failed', when: '2 h ago' },
  { id: 'demo-3', label: 'Conformance · AsyncAPI', result: 'passed', when: 'yesterday' }
];

export const DEMO_TRACE_STATUS = [200, 200, 201, 404, 200];

const clone = <T>(v: T): T => JSON.parse(JSON.stringify(v));

function vary(body: string, i: number): string {
  try {
    const doc = clone(JSON.parse(body));
    const edit = (o: any): boolean => {
      for (const k of Object.keys(o ?? {})) {
        if (typeof o[k] === 'string') {
          o[k] = `${o[k]} ${i + 1}`;
          return true;
        }
        if (typeof o[k] === 'number') {
          o[k] = Math.round((o[k] + i + 1) * 10) / 10;
          return true;
        }
        if (o[k] && typeof o[k] === 'object' && edit(o[k])) return true;
      }
      return false;
    };
    edit(doc);
    return JSON.stringify(doc, null, 2);
  } catch {
    return body;
  }
}

/** PLACEHOLDER generator: derives plausible variants of an existing example. */
export function generateProposals(op: CatalogOperation, count: number, hint: string): Proposal[] {
  const seed: CatalogExample = op.examples[0] ?? {
    id: 'seed',
    name: 'example',
    request: { params: {} },
    response: { status: 200, contentType: 'application/json', body: '{\n  "message": "ok"\n}' }
  };
  const taken = new Set(op.examples.map((e) => e.name));
  const out: Proposal[] = [];
  for (let i = 0; out.length < count; i++) {
    const params = Object.fromEntries(Object.entries(seed.request.params).map(([k, v]) => [k, `${v} ${i + 2}`]));
    const name = hint.trim() && i === 0 ? hint.trim().slice(0, 40) : `${seed.name} ${i + 2}`;
    if (taken.has(name)) continue;
    taken.add(name);
    out.push({
      id: `p-${Date.now().toString(36)}-${i}`,
      name,
      request: { params, ...(seed.request.body ? { body: vary(seed.request.body, i) } : {}) },
      response: { ...seed.response, body: vary(seed.response.body, i) }
    });
  }
  return out;
}
