import assert from 'node:assert/strict';
import { test } from 'node:test';
import { initialLinks, sourceVerdict, workflowVerdict, type ImportAnalysis } from './import-analysis.ts';

const apis = [
  { serviceId: 's-customer', name: 'Customer API', version: '1.0.0' },
  { serviceId: 's-order', name: 'Order API', version: '1.0.0' }
];
const analysis: ImportAnalysis = {
  arazzoVersion: '1.0.1',
  title: 'Order fulfilment',
  version: '1.0.0',
  workflowIds: ['searchThenRead', 'placeOrder'],
  sources: [
    {
      sourceName: 'customerApi',
      declaredUrl: './customer-api.openapi.yaml',
      match: { status: 'MATCHED', contractTitle: 'Customer API', contractVersion: '1.0.0' },
      link: { sourceName: 'customerApi', apiName: 'Customer API', apiVersion: '1.0.0', linked: true },
      operationCount: 2,
      foundOperationCount: 2,
      unresolvedStepCount: 0
    },
    {
      sourceName: 'orderApi',
      declaredUrl: './order-api.openapi.yaml',
      match: { status: 'UNREADABLE' },
      link: { sourceName: 'orderApi', linked: false },
      operationCount: 1,
      foundOperationCount: 0,
      unresolvedStepCount: 1
    }
  ],
  checks: [
    { workflowId: 'searchThenRead', runnable: true, apiNames: ['Customer API'], unlinkedSources: [], reasons: [] },
    { workflowId: 'placeOrder', runnable: false, apiNames: [], unlinkedSources: ['orderApi'], reasons: [] }
  ],
  apis
};

test('the links the backend proposes start as the choices, with the id of the API they name', () => {
  assert.deepEqual(initialLinks(analysis), { customerApi: apis[0], orderApi: null });
});

test('a source says whether its contract was found and how many of the called operations the API has', () => {
  assert.deepEqual(sourceVerdict(analysis.sources[0]!), { tone: 'success', status: 'Linked', detail: '2 of 2 operations found' });
  assert.deepEqual(sourceVerdict(analysis.sources[1]!), {
    tone: 'warning',
    status: 'Not linked',
    detail: 'The contract at ./order-api.openapi.yaml could not be read: pick the API to link'
  });
});

test('a source whose contract matches no API of Microcks tells to import it first', () => {
  const noApi = { ...analysis.sources[1]!, match: { status: 'NO_API' as const, contractTitle: 'Order API', contractVersion: '2.0.0' } };
  assert.equal(sourceVerdict(noApi).detail, 'No API named Order API 2.0.0 in Microcks: import it first, or pick the API to link');
});

test('a workflow waits for the sources it calls, or runs', () => {
  assert.deepEqual(workflowVerdict(analysis.checks[0]!), { runnable: true, note: 'Ready to run' });
  assert.deepEqual(workflowVerdict(analysis.checks[1]!), { runnable: false, note: 'Needs orderApi linked' });
  const missing = { workflowId: 'w', runnable: false, apiNames: ['Order API'], unlinkedSources: [], reasons: ['Operation CreateOrder not found in Order API 1.0.0'] };
  assert.equal(workflowVerdict(missing).note, 'Operation CreateOrder not found in Order API 1.0.0');
});
