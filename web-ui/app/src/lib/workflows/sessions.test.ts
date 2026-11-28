import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  compensationStepsOf,
  failureTextOf,
  contractFailureOf,
  dataFailureOf,
  failureChoicesOf,
  restMockPath,
  sessionCallsOf,
  sessionCurl,
  verificationProblemsOf
} from './sessions.ts';

const calls = [
  { index: 0, apiName: 'Order API', apiVersion: '1.0.0', method: 'POST', path: '/orders', stepId: 'createOrder' },
  { index: 1, apiName: 'Payment API', apiVersion: '1.0.0', method: 'POST', path: '/payments', stepId: 'chargePayment' },
  { index: 2, apiName: 'Order API', apiVersion: '1.0.0', method: 'GET', path: '/orders/o-42', stepId: null, failure: 'unexpected call `getOrder` after `chargePayment`' }
];
const steps = [
  { stepId: 'createOrder', status: 'PASSED', statusCode: 201 },
  { stepId: 'chargePayment', status: 'FAILED', statusCode: 402, message: '`chargePayment` body `/orderId` should come from `$steps.createOrder.outputs.id` (o-42), got o-43' },
  { stepId: 'cancelOrder', status: 'FAILED', message: 'step `cancelOrder` missing' }
];

test('a call keeps its place, the step it matched and the status the mock answered, a call matching no step is unexpected', () => {
  const [first, second, third] = sessionCallsOf(calls, steps);
  assert.deepEqual([first!.n, first!.stepId, first!.httpStatus, first!.method, first!.path], [1, 'createOrder', 201, 'POST', '/orders']);
  assert.deepEqual([second!.n, second!.stepId, second!.httpStatus], [2, 'chargePayment', 402]);
  assert.deepEqual([third!.n, third!.stepId, third!.httpStatus], [3, 'unexpected', 0]);
});

test('a call no step matched has nothing to judge against a contract or data passing', () => {
  const [, , third] = sessionCallsOf(calls, steps);
  assert.deepEqual([third!.contract.state, third!.dataPassing.state, third!.failure], ['off', 'off', 'unexpected call `getOrder` after `chargePayment`']);
});

test('a call whose step took a value that does not come from the workflow fails its data passing, not its contract', () => {
  const [, second] = sessionCallsOf(calls, steps);
  assert.equal(second!.dataPassing.state, 'fail');
  assert.equal(second!.contract.state, 'pass');
  assert.match(second!.dataPassing.text, /should come from/);
});

test('a request that does not conform to the contract of its API fails its contract', () => {
  const message = '`createOrder` request does not conform to Order API 1.0.0: /customerId is required';
  const [call] = sessionCallsOf([calls[0]!], [{ stepId: 'createOrder', status: 'FAILED', statusCode: 201, message }]);
  assert.equal(contractFailureOf(message), '`createOrder` request does not conform to Order API 1.0.0: /customerId is required');
  assert.equal(dataFailureOf(message), undefined);
  assert.equal(call!.contract.state, 'fail');
  assert.equal(call!.dataPassing.state, 'pass');
});

test('the problems of a verification name the failed steps and the unexpected calls, in the order of the workflow', () => {
  assert.deepEqual(verificationProblemsOf(steps, sessionCallsOf(calls, steps), undefined), [
    { lead: 'Step 2, chargePayment:', text: 'data not taken from an earlier step: chargePayment called with orderId=o-43, expected $steps.createOrder.outputs.id = o-42' },
    { lead: 'Step 3, cancelOrder:', text: 'no call received for this step' },
    { lead: 'Call #3, GET /orders/o-42:', text: 'unexpected call `getOrder` after `chargePayment`' }
  ]);
});

test('the message of a session that dropped calls is the first problem', () => {
  const [first] = verificationProblemsOf([], [], 'session reached 1002 calls, 2 calls were not recorded');
  assert.deepEqual(first, { lead: 'Session:', text: 'session reached 1002 calls, 2 calls were not recorded' });
});

test('the steps after an injected failure are the compensation', () => {
  assert.deepEqual(compensationStepsOf(['createOrder', 'chargePayment', 'releaseStock', 'cancelOrder'], 'chargePayment'), ['releaseStock', 'cancelOrder']);
  assert.deepEqual(compensationStepsOf(['a', 'b'], undefined), []);
  assert.deepEqual(compensationStepsOf(['a', 'b'], 'zzz'), []);
});

test('the mocks of a REST API are served under its name with + for spaces, its version and the path of the operation', () => {
  assert.equal(restMockPath('Order API', '1.0.0', '/orders/{orderId}'), '/rest/Order+API/1.0.0/orders/{orderId}');
  assert.equal(restMockPath('Pastry & Co', '2.0', '/p'), '/rest/Pastry+%26+Co/2.0/p');
});

test('the curl to a mock carries the session id in the x-microcks-session header', () => {
  assert.equal(
    sessionCurl('http://localhost:8080', 'POST', '/rest/Order+API/1.0.0/orders', 'abc123'),
    "curl -X POST 'http://localhost:8080/rest/Order+API/1.0.0/orders' \\\n  -H 'x-microcks-session: abc123'"
  );
});

test('the examples a step can fail with are those of its operation, the refusals first', () => {
  const exchanges = [
    { name: 'o-42', response: { status: '201' } },
    { name: 'card refused', response: { status: '402' } },
    { name: 'no status' }
  ];
  assert.deepEqual(failureChoicesOf(exchanges), [
    { value: 'card refused', code: 402 },
    { value: 'o-42', code: 201 },
    { value: 'no status', code: 200 }
  ]);
});

test('a failed step is told as the mockup does: data not taken from an earlier step, broken contract, no call received', () => {
  assert.equal(
    failureTextOf('patchPastry', '`patchPastry` path `name` should come from `$steps.listPastries.outputs.firstName` (Eclair Cafe), got Millefeuille'),
    'data not taken from an earlier step: patchPastry called with name=Millefeuille, expected $steps.listPastries.outputs.firstName = Eclair Cafe'
  );
  assert.equal(
    failureTextOf('createOrder', '`createOrder` request does not conform to Order API 1.0.0: /customerId is required'),
    'breaks the contract: request does not conform to Order API 1.0.0: /customerId is required'
  );
  assert.equal(failureTextOf('cancelOrder', 'step `cancelOrder` missing'), 'no call received for this step');
  assert.equal(failureTextOf('x', '`x` expected before `y`'), '`x` expected before `y`');
});
