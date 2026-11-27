/**
 * PLACEHOLDER: Arazzo workflows, documents, recorded examples, runs and verification sessions.
 *
 * Microcks has no Arazzo support yet (the artifact parser does not recognise Arazzo documents, and there is
 * no /api/workflows or /api/workflow-runs endpoint). Everything below is demo data ported from the Deep Ink
 * prototype and is flagged "demo data" in the UI. To be replaced by Microcks APIs: Arazzo document import
 * (as artifacts), workflow listing, workflow runs (start / list / get / watch), run sessions and the
 * ArazzoSources overlay (examples).
 *
 * Source links (`link.serviceId`) are LOGICAL keys of the prototype (`pastry2`, `pastry1`, `orders`), resolved
 * against the real store services by `#lib/server/workflows.js`.
 *
 * Owner: `workflows` domain. The `workflow-run` pages read this file; additions only.
 */
import { baggage } from '#lib/workflows/baggage.js';
import type {
  Action,
  ArazzoDocument,
  Criterion,
  MockCoherenceIssue,
  RunOptions,
  RunOrigin,
  RunTarget,
  Session,
  SessionCall,
  Step,
  StepParameter,
  StepResult,
  Workflow,
  WorkflowExample,
  WorkflowRun
} from '#lib/workflows/types.js';

export const MOCK_BASE = 'http://microcks.local:8080/rest/API+Pastry/2.0';
export const STAGING_BASE = 'https://pastry.staging.acme.io/v2';
export const MICROCKS_BASE = 'http://microcks.local:8080';
export const SESSION_ID = 's-7f3a2c91';
export const SECRET_STAGING = 'pastry-staging-oauth';
export const TEST_RUN_IDS = { failed: '119', passed: '118', failedBefore: '117' } as const;

/** Workflow run ids by run number. Run #14 is the hero of the demo. */
export const RUN_IDS = {
  14: '6729a1d27e4c0b5f93a8c214',
  13: '67299b3e1f0a4c7d82e5b106',
  12: '67290c5a9d3e41b7a6f8e215',
  11: '6728f8d47b2c4e09a1d3c524',
  15: '6729a3f08b1d6e2c45f7a930',
  16: '6729a4b15c9e7d3a28b0f6e1',
  checkMenu3: '6728e1a03c9b4f5d87e2a633'
} as const;

const simple = (condition: string): Criterion => ({ type: 'simple', condition });
const OK = [simple('$statusCode == 200')];

interface StepInit {
  stepId: string;
  sourceName: string;
  operationId: string;
  method: string;
  path: string;
  description: string;
  parameters?: StepParameter[];
  requestBody?: string;
  successCriteria?: Criterion[];
  onSuccess?: Action[];
  onFailure?: Action[];
  outputs?: { name: string; expression: string }[];
  dependsOn?: string[];
}

const step = (init: StepInit): Step => ({
  parameters: [],
  successCriteria: OK,
  onSuccess: [],
  onFailure: [],
  outputs: [],
  ...init
});

const nameParam: StepParameter = { name: 'name', in: 'path', value: '$steps.listPastries.outputs.firstName' };

export const RESTOCK_STEPS: Step[] = [
  step({
    stepId: 'listPastries', sourceName: 'apiPastry', operationId: 'GetPastries', method: 'GET', path: '/pastries',
    description: 'List the pastries of the requested size and keep the first one.',
    parameters: [{ name: 'size', in: 'query', value: '$inputs.size' }],
    outputs: [{ name: 'firstName', expression: '$response.body#/0/name' }]
  }),
  step({
    stepId: 'getPastry', sourceName: 'apiPastry', operationId: 'GetPastryByName', method: 'GET', path: '/pastries/{name}',
    description: 'Read the first pastry to get its current price and status.',
    parameters: [nameParam],
    onSuccess: [{ type: 'end', name: 'alreadyAvailable', criteria: [simple("$response.body#/status == 'available'")] }],
    outputs: [
      { name: 'oldPrice', expression: '$response.body#/price' },
      { name: 'status', expression: '$response.body#/status' }
    ]
  }),
  step({
    stepId: 'patchPastry', sourceName: 'apiPastry', operationId: 'PatchPastry', method: 'PATCH', path: '/pastries/{name}',
    description: 'Make the pastry available again at the new price.',
    parameters: [nameParam],
    requestBody: '{"price": "{$inputs.newPrice}", "status": "available"}',
    onFailure: [{ type: 'retry', name: 'retryPatch', retryAfter: 1, retryLimit: 2 }],
    outputs: [{ name: 'newPrice', expression: '$response.body#/price' }]
  }),
  step({
    stepId: 'verifyPastry', sourceName: 'apiPastry', operationId: 'GetPastryByName', method: 'GET', path: '/pastries/{name}',
    description: 'Read the pastry again and check it is now available.',
    parameters: [nameParam],
    successCriteria: [
      simple('$statusCode == 200'),
      simple("$response.body#/status == 'available'"),
      { type: 'regex', context: '$response.body#/status', condition: '^available$' }
    ]
  })
];

const list = (id: string, size: string) =>
  step({
    stepId: id, sourceName: 'apiPastry', operationId: 'GetPastries', method: 'GET', path: '/pastries',
    description: `List the ${size} pastries.`,
    parameters: [{ name: 'size', in: 'query', value: size }],
    outputs: [{ name: 'count', expression: '$response.body#/length' }]
  });

export const CHECK_MENU_STEPS: Step[] = [
  list('listSmall', 'S'),
  list('listMedium', 'M'),
  list('listLarge', 'L'),
  step({
    stepId: 'compareSizes', sourceName: 'apiPastry', operationId: 'GetPastries', method: 'GET', path: '/pastries',
    description: 'Compare the three sizes once they are all listed.',
    dependsOn: ['listSmall', 'listMedium', 'listLarge']
  })
];

const mockUrl = (doc: 'pastry' | 'beer', workflowId: string) =>
  doc === 'pastry'
    ? `${MICROCKS_BASE}/arazzo/Pastry%20workflows/1.2.0/${workflowId}`
    : `${MICROCKS_BASE}/arazzo/Beer%20checkout/0.3.0/${workflowId}`;

const RUNNABLE = { runnable: true } as const;

export const WORKFLOWS: Workflow[] = [
  {
    workflowId: 'restock-pastry', documentId: 'pastry', summary: 'Restock a pastry',
    description:
      'Lists the pastries of a size and reads the first one. When it is already available the workflow ends there; otherwise it is made available again at a new price, then read again to verify.',
    inputs: [{ name: 'size', type: 'string', example: 'M' }, { name: 'newPrice', type: 'number', example: '3.9' }],
    steps: RESTOCK_STEPS,
    outputs: [
      { name: 'pastryName', expression: '$steps.listPastries.outputs.firstName' },
      { name: 'oldPrice', expression: '$steps.getPastry.outputs.oldPrice' },
      { name: 'newPrice', expression: '$steps.patchPastry.outputs.newPrice' }
    ],
    mockUrl: mockUrl('pastry', 'restock-pastry'), runnability: RUNNABLE
  },
  {
    workflowId: 'order-pastry', documentId: 'pastry', summary: 'Order a pastry',
    inputs: [{ name: 'name', type: 'string' }, { name: 'quantity', type: 'integer' }],
    steps: [
      step({ stepId: 'getPastry', sourceName: 'apiPastry', operationId: 'GetPastryByName', method: 'GET', path: '/pastries/{name}',
        description: 'Read the pastry to order.', parameters: [{ name: 'name', in: 'path', value: '$inputs.name' }] }),
      step({ stepId: 'publishOrder', sourceName: 'pastryOrders', operationId: 'publishOrder', method: 'PUBLISH', path: 'pastry-orders',
        description: 'Publish the order on the orders channel.' }),
      step({ stepId: 'awaitValidation', sourceName: 'pastryOrders', operationId: 'awaitValidation', method: 'SUBSCRIBE', path: 'pastry-orders-validated',
        description: 'Wait for the order to be validated.' })
    ],
    outputs: [],
    mockUrl: mockUrl('pastry', 'order-pastry'),
    runnability: {
      runnable: false, reason: 'asyncapi-steps',
      message: 'Workflow order-pastry cannot run yet: its steps publishOrder and awaitValidation exchange messages on an AsyncAPI source'
    }
  },
  {
    workflowId: 'compare-prices', documentId: 'pastry', summary: 'Compare v1 / v2 prices',
    inputs: [{ name: 'name', type: 'string' }],
    steps: [
      step({ stepId: 'getV1', sourceName: 'apiPastryV1', operationId: 'GetPastryByName', method: 'GET', path: '/pastries/{name}',
        description: 'Read the pastry in API Pastry 1.0.', parameters: [{ name: 'name', in: 'path', value: '$inputs.name' }] }),
      step({ stepId: 'getV2', sourceName: 'apiPastry', operationId: 'GetPastryByName', method: 'GET', path: '/pastries/{name}',
        description: 'Read the pastry in API Pastry 2.0.', parameters: [{ name: 'name', in: 'path', value: '$inputs.name' }] }),
      step({ stepId: 'checkV2Listing', sourceName: 'apiPastry', operationId: 'GetPastries', method: 'GET', path: '/pastries',
        description: 'Check the pastry is listed in version 2.0.' })
    ],
    outputs: [], mockUrl: mockUrl('pastry', 'compare-prices'), runnability: RUNNABLE
  },
  {
    workflowId: 'check-menu', documentId: 'pastry', summary: 'Check the menu sizes',
    inputs: [], steps: CHECK_MENU_STEPS, outputs: [], mockUrl: mockUrl('pastry', 'check-menu'), runnability: RUNNABLE
  },
  {
    workflowId: 'checkout-beer', documentId: 'beer', summary: 'Check out a beer',
    inputs: [{ name: 'beerName', type: 'string' }, { name: 'quantity', type: 'integer' }],
    steps: [
      step({ stepId: 'findBeer', sourceName: 'beerCatalog', operationId: 'FindBeer', method: 'GET', path: '/beers', description: 'Find the beer.' }),
      step({ stepId: 'getBeer', sourceName: 'beerCatalog', operationId: 'GetBeer', method: 'GET', path: '/beers/{name}', description: 'Read the beer.' }),
      step({ stepId: 'reserveBeer', sourceName: 'beerCatalog', operationId: 'ReserveBeer', method: 'POST', path: '/beers/{name}/reserve', description: 'Reserve the beer.' })
    ],
    outputs: [], mockUrl: mockUrl('beer', 'checkout-beer'),
    runnability: { runnable: false, reason: 'source-not-linked', message: 'Workflow checkout-beer cannot run until beerCatalog is linked' }
  }
];

export const DOCUMENTS: ArazzoDocument[] = [
  {
    id: 'pastry', apiId: '6728f1c2a4e1b93d5c0e7a14', file: 'pastry-workflows.arazzo.yaml', arazzoVersion: '1.1.0', version: '1.2.0',
    overlay: 'pastry-workflows.arazzo-sources.yaml',
    title: 'Pastry workflows',
    origin: 'Synced 12 min ago from GitHub',
    sources: [
      { name: 'apiPastry', type: 'openapi', url: './apipastry-2.0-openapi.yaml', link: { status: 'linked', serviceId: 'pastry2', protocol: 'REST', operationsFound: '3 of 3 found' } },
      { name: 'apiPastryV1', type: 'openapi', url: './apipastry-1.0-openapi.yaml', link: { status: 'linked', serviceId: 'pastry1', protocol: 'REST', operationsFound: '1 of 1 found' } },
      { name: 'pastryOrders', type: 'asyncapi', url: './pastry-orders-0.1.0-asyncapi.yaml', link: { status: 'linked', serviceId: 'orders', protocol: 'EVENT', operationsFound: '2 of 2 found' } }
    ],
    workflowIds: ['restock-pastry', 'order-pastry', 'compare-prices', 'check-menu'],
    importIssues: []
  },
  {
    id: 'beer', apiId: '6728f1c2a4e1b93d5c0e7a15', file: 'beer-checkout.arazzo.yaml', arazzoVersion: '1.0.1', version: '0.3.0',
    title: 'Beer checkout',
    origin: 'Uploaded from a file 6 d ago by MA',
    sources: [
      { name: 'beerCatalog', type: 'openapi', url: './beer-catalog-1.0-openapi.yaml', link: { status: 'not-linked', expectedFile: 'beer-catalog-1.0-openapi.yaml' } }
    ],
    workflowIds: ['checkout-beer'],
    importIssues: []
  }
];

/* ---------------------------------------------------------------------------------------------- runs */

const SCHEMA_MSG =
  '/price does not match the Pastry schema: instance type (string) does not match any allowed primitive type (allowed: ["integer","number"])';

const held = (condition: string, evaluation: string, kind: 'simple' | 'regex' = 'simple') => ({ condition, kind, evaluation, held: true });
const status200 = held('$statusCode == 200', '$statusCode = 200');
const attempt = (n: number, ms: number, when: string, httpStatus = 200) => ({ n, httpStatus, ms, when });
const NO_SCHEMA = { state: 'off' as const, ref: '', message: 'Off for this run: the step is judged on its success criteria only.' };

function passedStep(runId: string, workflowId: string, s: Step, base: string, ms: number, outputs: Record<string, unknown> = {}, validate = false): StepResult {
  return {
    stepId: s.stepId, status: 'passed', attempts: [attempt(1, ms, 'first call')],
    request: { method: s.method, url: `${base}${s.path}`, params: Object.fromEntries(s.parameters.map((p) => [p.name, p.value])) },
    criteria: s.successCriteria.map((c) => held(c.condition, `${c.type === 'regex' ? c.context : c.condition.split(' ')[0]} = 200`, c.type)),
    schema: validate ? { state: 'pass', ref: `${s.operationId} · 200 · application/json`, message: 'The response conforms to its schema.' } : NO_SCHEMA,
    outputs,
    flow: { kind: 'next', title: 'Next step', text: 'The step passed, so the run continues with the next step.' },
    baggage: baggage(runId, workflowId, s.stepId, 1)
  };
}

function notRun(runId: string, workflowId: string, s: Step): StepResult {
  return {
    stepId: s.stepId, status: 'not-run', attempts: [], criteria: [], schema: NO_SCHEMA, outputs: {},
    flow: { kind: 'next', title: '', text: '' }, baggage: baggage(runId, workflowId, s.stepId, 1)
  };
}

const [LIST, GET, PATCH, VERIFY] = RESTOCK_STEPS as [Step, Step, Step, Step];
const MOCK_OPTS = (example: string, inputs: Record<string, unknown>): RunOptions => ({ example, inputs, timeoutMs: 10_000, validateSchemas: false });

function restockRun(init: {
  number: number; id: string; startedAt: string; origin: RunOrigin; target: RunTarget; options: RunOptions;
  status: 'SUCCESS' | 'FAILURE'; steps: StepResult[]; outputs?: Record<string, unknown>;
  coherence?: MockCoherenceIssue[]; relatedTestRunId?: string;
}): WorkflowRun {
  return { documentId: 'pastry', workflowId: 'restock-pastry', outputs: {}, coherence: [], ...init };
}

interface PastryBody { name: string; description: string; size: 'S' | 'M' | 'L'; price: number | string; status: string }
const PASTRY: Record<string, PastryBody> = {
  'Tarte Tatin': { name: 'Tarte Tatin', description: 'Caramelized upside-down apple tart', size: 'S', price: 3.2, status: 'unknown' },
  'Eclair Cafe': { name: 'Eclair Cafe', description: 'Delicious Coffee Eclair, not caloric at all', size: 'M', price: 2.4, status: 'unknown' },
  'Eclair Chocolat': { name: 'Eclair Chocolat', description: 'Delicious Chocolate Eclair, not caloric at all', size: 'M', price: 2.4, status: 'unknown' },
  Millefeuille: { name: 'Millefeuille', description: 'Delicious Millefeuille, not caloric at all', size: 'L', price: 4.4, status: 'available' }
};
const BY_SIZE: Record<'S' | 'M' | 'L', string[]> = { S: ['Tarte Tatin'], M: ['Eclair Cafe', 'Eclair Chocolat'], L: ['Millefeuille'] };

const J = (value: unknown) => JSON.stringify(value);
const pastry = (name: string) => PASTRY[name] as PastryBody;

function objectLines(o: PastryBody, badKey?: keyof PastryBody, note?: string): { text: string; bad?: boolean; note?: string }[] {
  const keys = Object.keys(o) as (keyof PastryBody)[];
  return [
    { text: '{' },
    ...keys.map((k, i) => {
      const text = `  ${J(k)}: ${J(o[k])}${i < keys.length - 1 ? ',' : ''}`;
      return k === badKey ? { text, bad: true, note: note ?? 'schema mismatch' } : { text };
    }),
    { text: '}' }
  ];
}

function listStep(runId: string, base: string, size: 'S' | 'M' | 'L', ms: number, validate: boolean): StepResult {
  const names = BY_SIZE[size];
  const one = (o: PastryBody) => `{ ${(Object.keys(o) as (keyof PastryBody)[]).map((k) => `${J(k)}: ${J(o[k])}`).join(', ')} }`;
  return {
    stepId: 'listPastries', status: 'passed', attempts: [attempt(1, ms, 'first call')],
    request: { method: 'GET', url: `${base}/pastries?size=${size}`, params: { size } },
    response: {
      meta: `200 OK · application/json · ${names.length} ${names.length > 1 ? 'items' : 'item'}`,
      lines: [{ text: '[' }, ...names.map((n, i) => ({ text: `  ${one(pastry(n))}${i < names.length - 1 ? ',' : ''}` })), { text: ']' }]
    },
    criteria: [status200],
    schema: validate ? { state: 'pass', ref: 'GetPastries · 200 · application/json', message: 'The response conforms to its schema: an array of Pastry.' } : NO_SCHEMA,
    outputs: { firstName: names[0] },
    flow: { kind: 'next', title: 'Next: getPastry', text: 'No onSuccess action is declared: the run continues with the next step.' },
    baggage: baggage(runId, 'restock-pastry', 'listPastries', 1)
  };
}

function getStep(runId: string, base: string, name: string, ms: number, validate: boolean): StepResult {
  const p = pastry(name);
  const ends = p.status === 'available';
  return {
    stepId: 'getPastry', status: 'passed', attempts: [attempt(1, ms, 'first call')],
    request: { method: 'GET', url: `${base}/pastries/${encodeURIComponent(name)}`, params: { name } },
    response: { meta: '200 OK · application/json', lines: objectLines(p) },
    criteria: [status200],
    schema: validate ? { state: 'pass', ref: 'GetPastryByName · 200 · application/json · Pastry', message: 'The response conforms to the Pastry schema.' } : NO_SCHEMA,
    outputs: { oldPrice: p.price, status: p.status },
    flow: ends
      ? { kind: 'end', title: 'onSuccess end applies: the run ends here', text: `Its criterion $response.body#/status == 'available' holds (${J(p.status)}): nothing to restock. The run ends with SUCCESS; patchPastry and verifyPastry are not run.` }
      : { kind: 'next', title: 'onSuccess end does not apply: next is patchPastry', text: `Its criterion $response.body#/status == 'available' does not hold (${J(p.status)}), so the run continues with the next step.` },
    baggage: baggage(runId, 'restock-pastry', 'getPastry', 1)
  };
}

function run14(): WorkflowRun {
  const id = RUN_IDS[14];
  const base = STAGING_BASE;
  const patch: StepResult = {
    stepId: 'patchPastry', status: 'failed',
    attempts: [
      attempt(1, 66, 'first call'),
      { ...attempt(2, 58, 'after 1 s'), waitMs: 1000 },
      { ...attempt(3, 61, 'after 1 s'), waitMs: 1000 }
    ],
    request: { method: 'PATCH', url: `${base}/pastries/Eclair%20Cafe`, params: { name: 'Eclair Cafe' }, body: '{"price": 3.9, "status": "available"}' },
    response: {
      meta: '200 OK · application/json',
      lines: objectLines({ ...pastry('Eclair Cafe'), price: '3.9', status: 'available' }, 'price', 'schema: number')
    },
    criteria: [status200],
    schema: { state: 'fail', ref: 'PatchPastry · 200 · application/json · Pastry', message: SCHEMA_MSG },
    outputs: {},
    flow: {
      kind: 'stop', title: 'Failed after 3 attempts: the run ends here',
      text: 'Each attempt failed on schema validation although its success criteria held, so onFailure retry applied twice (retryAfter 1 s, retryLimit 2). After attempt 3 the retry limit is reached: the run ends with FAILURE and verifyPastry is not run.'
    },
    baggage: baggage(id, 'restock-pastry', 'patchPastry', 3)
  };
  return restockRun({
    number: 14, id, startedAt: '2026-11-03T09:10:04Z', origin: { kind: 'ui' }, target: { kind: 'implementations', baseUrls: { apiPastry: base } },
    options: { example: 'restock-eclair', inputs: { size: 'M', newPrice: 3.9 }, secret: SECRET_STAGING, timeoutMs: 10_000, validateSchemas: true },
    status: 'FAILURE', relatedTestRunId: TEST_RUN_IDS.failed,
    steps: [listStep(id, base, 'M', 41, true), getStep(id, base, 'Eclair Cafe', 29, true), patch, notRun(id, 'restock-pastry', VERIFY)]
  });
}

function sessionRun13(): WorkflowRun {
  const id = RUN_IDS[13];
  const patch: StepResult = {
    ...passedStep(id, 'restock-pastry', PATCH, MOCK_BASE, 0, {}),
    status: 'failed',
    attempts: [attempt(1, 0, 'call #4')],
    flow: { kind: 'stop', title: 'Data not taken from an earlier step', text: 'patchPastry called with name=Millefeuille, expected $steps.listPastries.outputs.firstName = Eclair Cafe' }
  };
  return restockRun({
    number: 13, id, startedAt: '2026-11-03T07:08:04Z', origin: { kind: 'session', sessionId: SESSION_ID, app: 'checkout-web' },
    target: { kind: 'mocks' }, options: { inputs: { size: 'M', newPrice: 3.9 }, timeoutMs: 10_000, validateSchemas: false },
    status: 'FAILURE',
    steps: [
      { ...passedStep(id, 'restock-pastry', LIST, MOCK_BASE, 0, { firstName: 'Eclair Cafe' }), attempts: [attempt(1, 0, 'call #1')] },
      { ...passedStep(id, 'restock-pastry', GET, MOCK_BASE, 0, { oldPrice: 2.4, status: 'unknown' }), attempts: [attempt(1, 0, 'call #2')] },
      patch,
      { ...passedStep(id, 'restock-pastry', VERIFY, MOCK_BASE, 0), attempts: [attempt(1, 0, 'call #5')] }
    ]
  });
}

function mocksSuccess(number: number, id: string, startedAt: string, origin: RunOrigin, example: string, ms: readonly [number, number, number, number]): WorkflowRun {
  return restockRun({
    number, id, startedAt, origin, target: { kind: 'mocks' }, options: MOCK_OPTS(example, { size: 'M', newPrice: 3.9 }), status: 'SUCCESS',
    steps: [
      passedStep(id, 'restock-pastry', LIST, MOCK_BASE, ms[0], { firstName: 'Eclair Cafe' }),
      passedStep(id, 'restock-pastry', GET, MOCK_BASE, ms[1], { oldPrice: 2.4, status: 'unknown' }),
      passedStep(id, 'restock-pastry', PATCH, MOCK_BASE, ms[2], { newPrice: 3.9 }),
      passedStep(id, 'restock-pastry', VERIFY, MOCK_BASE, ms[3])
    ],
    outputs: { pastryName: 'Eclair Cafe', oldPrice: 2.4, newPrice: 3.9 }
  });
}

/** Run #15, scenario "Mocks · ended early": Millefeuille is already available, the onSuccess end stops the run. */
function run15(startedAt = '2026-11-03T09:14:37Z'): WorkflowRun {
  const id = RUN_IDS[15];
  return restockRun({
    number: 15, id, startedAt, origin: { kind: 'ui' }, target: { kind: 'mocks' },
    options: MOCK_OPTS('already-available', { size: 'L' }), status: 'SUCCESS',
    steps: [listStep(id, MOCK_BASE, 'L', 6, false), getStep(id, MOCK_BASE, 'Millefeuille', 5, false), notRun(id, 'restock-pastry', PATCH), notRun(id, 'restock-pastry', VERIFY)],
    outputs: { pastryName: 'Millefeuille', oldPrice: 4.4 }
  });
}

/** Run #16, scenario "Mocks · missing example": the mocks of GetPastryByName have no example for Tarte Tatin. */
function run16(startedAt = '2026-11-03T09:16:52Z'): WorkflowRun {
  const id = RUN_IDS[16];
  const get: StepResult = {
    stepId: 'getPastry', status: 'failed', attempts: [attempt(1, 4, 'first call', 400)],
    request: { method: 'GET', url: `${MOCK_BASE}/pastries/Tarte%20Tatin`, params: { name: 'Tarte Tatin' } },
    response: { meta: '400 Bad Request', lines: [] },
    criteria: [{ condition: '$statusCode == 200', kind: 'simple', evaluation: '$statusCode = 400', held: false }],
    schema: NO_SCHEMA, outputs: {},
    flow: { kind: 'stop', title: 'No onFailure action: the run ends here', text: 'getPastry declares no onFailure action, so the run ends with FAILURE. patchPastry and verifyPastry are not run.' },
    baggage: baggage(id, 'restock-pastry', 'getPastry', 1)
  };
  return restockRun({
    number: 16, id, startedAt, origin: { kind: 'ui' }, target: { kind: 'mocks' },
    options: { inputs: { size: 'S', newPrice: 4.2 }, timeoutMs: 10_000, validateSchemas: false }, status: 'FAILURE',
    steps: [listStep(id, MOCK_BASE, 'S', 6, false), get, notRun(id, 'restock-pastry', PATCH), notRun(id, 'restock-pastry', VERIFY)],
    coherence: [{
      stepId: 'getPastry', operationId: 'GetPastryByName',
      missing: 'an example of GetPastryByName for name=Tarte Tatin',
      copilotHint: 'Tarte Tatin'
    }]
  });
}

function checkMenuRun(): WorkflowRun {
  const id = RUN_IDS.checkMenu3;
  return {
    id, number: 3, documentId: 'pastry', workflowId: 'check-menu', origin: { kind: 'ui' }, target: { kind: 'mocks' },
    options: { inputs: {}, timeoutMs: 10_000, validateSchemas: false }, status: 'SUCCESS', startedAt: '2026-10-30T08:00:04Z',
    steps: CHECK_MENU_STEPS.map((s) => passedStep(id, 'check-menu', s, MOCK_BASE, 6)), outputs: {}, coherence: []
  };
}

/** The runs that exist when the demo opens: #14 (latest), #13, #12, #11 of restock-pastry and #3 of check-menu. */
export function seedRuns(): WorkflowRun[] {
  return [
    run14(),
    sessionRun13(),
    mocksSuccess(12, RUN_IDS[12], '2026-11-02T16:42:04Z', { kind: 'ui' }, 'restock-eclair', [41, 38, 52, 36]),
    mocksSuccess(11, RUN_IDS[11], '2026-11-01T10:15:04Z', { kind: 'mock-url' }, 'restock-eclair', [44, 35, 49, 33]),
    checkMenuRun()
  ];
}

/** Runs #15 and #16 only exist once their scenario is launched; their ids stay stable for the links of the story. */
export function scenarioRun(id: string, startedAt?: string): WorkflowRun | undefined {
  if (id === RUN_IDS[15]) return run15(startedAt);
  if (id === RUN_IDS[16]) return run16(startedAt);
  return undefined;
}

export const scenarioRuns = (): WorkflowRun[] => [run15(), run16()];

/* ---------------------------------------------------------------------------------------- sessions */

const call = (n: number, method: string, path: string, httpStatus: number, stepId: string, dataText: string, extra: Partial<SessionCall> = {}): SessionCall => ({
  n, method, path, httpStatus, stepId,
  contract: { state: 'pass', text: 'conforms' },
  dataPassing: { state: 'pass', text: dataText },
  ...extra
});

export function seedSessions(): Session[] {
  return [
    {
      id: SESSION_ID, app: 'checkout-web · saga tests', documentId: 'pastry', workflowId: 'restock-pastry', state: 'closed',
      openedAt: '2026-11-03T07:05:04Z', closedAt: '2026-11-03T07:05:45Z', inputs: { size: 'M', newPrice: 3.9 },
      calls: [
        call(1, 'GET', '/pastries?size=M', 200, 'listPastries', 'size from the inputs'),
        call(2, 'GET', '/pastries/Eclair%20Cafe', 200, 'getPastry', 'name from listPastries'),
        call(3, 'GET', '/pastries?size=XL', 400, 'unexpected', '—', {
          contract: { state: 'fail', text: 'size XL is not one of S, M, L' }, dataPassing: { state: 'off', text: '—' }
        }),
        call(4, 'PATCH', '/pastries/Millefeuille', 200, 'patchPastry', 'name=Millefeuille, expected Eclair Cafe', {
          dataPassing: { state: 'fail', text: 'name=Millefeuille, expected Eclair Cafe' }
        }),
        call(5, 'GET', '/pastries/Eclair%20Cafe', 200, 'verifyPastry', 'name from listPastries')
      ],
      verification: {
        result: 'FAILURE', runNumber: 13,
        problems: [
          { lead: 'Step 3, patchPastry:', text: 'data not taken from an earlier step: patchPastry called with name=Millefeuille, expected $steps.listPastries.outputs.firstName = Eclair Cafe' },
          { lead: 'Call #3, GET /pastries?size=XL:', text: 'unexpected call `GetPastries` after `getPastry`' }
        ]
      }
    },
    {
      id: 's-2b91e04d', app: 'checkout-web · compensation test', documentId: 'pastry', workflowId: 'restock-pastry', state: 'open',
      openedAt: '2026-11-03T09:04:04Z', inputs: { size: 'M', newPrice: 3.9 },
      failureInjection: { stepId: 'patchPastry', example: 'Eclair Cafe · out of stock' },
      calls: [
        call(1, 'GET', '/pastries?size=M', 200, 'listPastries', 'size from the inputs'),
        call(2, 'GET', '/pastries/Eclair%20Cafe', 200, 'getPastry', 'name from listPastries')
      ]
    },
    {
      id: 's-c4d07a18', app: 'checkout-web · saga tests', documentId: 'pastry', workflowId: 'restock-pastry', state: 'closed',
      openedAt: '2026-11-02T11:20:04Z', closedAt: '2026-11-02T11:20:42Z', inputs: { size: 'M', newPrice: 3.9 },
      calls: [
        call(1, 'GET', '/pastries?size=M', 200, 'listPastries', 'size from the inputs'),
        call(2, 'GET', '/pastries/Eclair%20Cafe', 200, 'getPastry', 'name from listPastries'),
        call(3, 'PATCH', '/pastries/Eclair%20Cafe', 200, 'patchPastry', 'name from listPastries'),
        call(4, 'GET', '/pastries/Eclair%20Cafe', 200, 'verifyPastry', 'name from listPastries')
      ],
      verification: { result: 'SUCCESS', runNumber: 10, problems: [] }
    }
  ];
}

export const EXAMPLES: WorkflowExample[] = [
  {
    id: 'restock-eclair', workflowId: 'restock-pastry',
    description: 'Eclair Cafe is not available: the workflow restocks it at 3.9.',
    inputs: { size: 'M', newPrice: 3.9 },
    steps: [
      { stepId: 'listPastries', status: 200, outputs: { firstName: 'Eclair Cafe' } },
      { stepId: 'getPastry', status: 200 },
      { stepId: 'patchPastry', status: 200 },
      { stepId: 'verifyPastry', status: 200 }
    ],
    outputs: { newPrice: 3.9 }
  },
  {
    id: 'already-available', workflowId: 'restock-pastry',
    description: 'Millefeuille is already available: the end action of getPastry stops the run, with success.',
    inputs: { size: 'L' },
    steps: [
      { stepId: 'listPastries', status: 200, outputs: { firstName: 'Millefeuille' } },
      { stepId: 'getPastry', status: 200, outputs: { status: 'available' } }
    ],
    stopsAt: 'getPastry',
    outputs: {},
    lastJudged: { runNumber: 9, at: '2026-10-29T08:40:04Z', matched: true }
  }
];

/* ---------------------------------------------------------------------------------------- sources */

export const BEER_ARAZZO_YAML = [
  'arazzo: 1.0.1',
  'info:',
  '  title: Beer checkout',
  '  version: 0.3.0',
  'sourceDescriptions:',
  '  - name: beerCatalog',
  '    url: ./beer-catalog-1.0-openapi.yaml',
  '    type: openapi',
  'workflows:',
  '  - workflowId: checkout-beer',
  '    # findBeer, getBeer, reserveBeer'
].join('\n');

export const PASTRY_ARAZZO_YAML = `arazzo: 1.1.0
info:
  title: Pastry workflows
  version: 1.2.0
sourceDescriptions:
  - name: apiPastry
    url: ./apipastry-2.0-openapi.yaml
    type: openapi
  - name: apiPastryV1
    url: ./apipastry-1.0-openapi.yaml
    type: openapi
  - name: pastryOrders
    url: ./pastry-orders-0.1.0-asyncapi.yaml
    type: asyncapi
workflows:
  - workflowId: restock-pastry
    summary: Restock a pastry
    description: >-
      Lists the pastries of a size and reads the first one. When it is
      already available the workflow ends; otherwise it is made available
      again at a new price, then read again to verify.
    inputs:
      type: object
      properties:
        size:
          type: string
          enum: [S, M, L]
          default: M
        newPrice:
          type: number
          default: 3.9
    steps:
      - stepId: listPastries
        description: List the pastries of the requested size.
        operationId: $sourceDescriptions.apiPastry.GetPastries
        parameters:
          - name: size
            in: query
            value: $inputs.size
        successCriteria:
          - condition: $statusCode == 200
        outputs:
          firstName: $response.body#/0/name
      - stepId: getPastry
        description: Read the first pastry to get its current price and status.
        operationId: $sourceDescriptions.apiPastry.GetPastryByName
        parameters:
          - name: name
            in: path
            value: $steps.listPastries.outputs.firstName
        successCriteria:
          - condition: $statusCode == 200
        onSuccess:
          - name: alreadyAvailable
            type: end
            criteria:
              - condition: $response.body#/status == 'available'
        outputs:
          oldPrice: $response.body#/price
          status: $response.body#/status
      - stepId: patchPastry
        description: Make the pastry available again at the new price.
        operationId: $sourceDescriptions.apiPastry.PatchPastry
        parameters:
          - name: name
            in: path
            value: $steps.listPastries.outputs.firstName
        requestBody:
          contentType: application/json
          payload:
            price: "{$inputs.newPrice}"
            status: available
          replacements:
            - target: /price
              value: $inputs.newPrice
        successCriteria:
          - condition: $statusCode == 200
        onFailure:
          - name: retryPatch
            type: retry
            retryAfter: 1
            retryLimit: 2
        outputs:
          newPrice: $response.body#/price
      - stepId: verifyPastry
        description: Read the pastry again and check it is now available.
        operationId: $sourceDescriptions.apiPastry.GetPastryByName
        parameters:
          - name: name
            in: path
            value: $steps.listPastries.outputs.firstName
        successCriteria:
          - condition: $statusCode == 200
          - condition: $response.body#/status == 'available'
          - context: $response.body#/status
            condition: ^available$
            type: regex
    outputs:
      pastryName: $steps.listPastries.outputs.firstName
      oldPrice: $steps.getPastry.outputs.oldPrice
      newPrice: $steps.patchPastry.outputs.newPrice
  # order-pastry, compare-prices, check-menu: 3 more workflows (lines 103-222)
`;

export const PASTRY_OVERLAY_YAML = `apiVersion: mocks.microcks.io/v1alpha1
kind: ArazzoSources
metadata:
  name: Pastry workflows
  version: 1.2.0
sources:
  apiPastry:
    name: API Pastry
    version: "2.0"
  apiPastryV1:
    name: API Pastry
    version: "1.0"
  pastryOrders:
    name: Pastry orders API
    version: 0.1.0
examples:
  restock-pastry:
    restock-eclair:
      inputs:
        size: M
        newPrice: 3.9
      steps:
        listPastries:
          status: 200
          outputs:
            firstName: Eclair Cafe
        getPastry:
          status: 200
        patchPastry:
          status: 200
        verifyPastry:
          status: 200
      outputs:
        newPrice: 3.9
    already-available:
      inputs:
        size: L
      steps:
        listPastries:
          status: 200
          outputs:
            firstName: Millefeuille
        getPastry:
          status: 200
          outputs:
            status: available
      stopsAt: getPastry
`;
