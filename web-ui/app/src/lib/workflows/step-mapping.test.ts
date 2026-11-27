import assert from 'node:assert/strict';
import { test } from 'node:test';
import { dependsOnOf, messagingOf, runnabilityOf } from './step-mapping.ts';

test('a workflow whose reasons name async steps cannot run yet, and says why in the words of Microcks', () => {
  const r = runnabilityOf({ runnable: false, reasons: ['async steps cannot be run yet: requestShipping, confirmOrder'] });
  assert.deepEqual(r, { runnable: false, reason: 'asyncapi-steps', message: 'Async steps cannot be run yet: requestShipping, confirmOrder' });
});

test('async steps win over a channel the contract does not define when both reasons are given', () => {
  const r = runnabilityOf({ runnable: false, reasons: ['Channel orderShipped not found in Order events 1.0.0', 'Async steps cannot be run yet: shipped'] });
  assert.equal(r.runnable === false && r.reason, 'asyncapi-steps');
  assert.equal(r.runnable === false && r.message, 'Channel orderShipped not found in Order events 1.0.0. Async steps cannot be run yet: shipped');
});

test('any other reason is a source that is not linked, and a runnable workflow has none', () => {
  assert.deepEqual(runnabilityOf({ runnable: false, reasons: ['source petstore is not linked'] }), {
    runnable: false,
    reason: 'source-not-linked',
    message: 'Source petstore is not linked'
  });
  assert.deepEqual(runnabilityOf({ runnable: true, reasons: [] }), { runnable: true });
  assert.deepEqual(runnabilityOf(undefined), { runnable: true });
});

test('a step that exchanges a message tells its action, channel, correlationId and timeout; an HTTP step tells nothing', () => {
  assert.deepEqual(messagingOf({ action: 'receive', correlationId: '$steps.a.outputs.id', timeout: 6000 }, 'orders/confirmed'), {
    action: 'receive',
    channel: 'orders/confirmed',
    correlationId: '$steps.a.outputs.id',
    timeoutMs: 6000
  });
  assert.deepEqual(messagingOf({ action: 'send' }, 'shipping/requests'), { action: 'send', channel: 'shipping/requests' });
  assert.equal(messagingOf(null, undefined), undefined);
});

test('steps are sequential when none of them declares dependsOn', () => {
  const steps = [
    { stepId: 'a', dependsOn: [], prerequisites: [] },
    { stepId: 'b', dependsOn: [], prerequisites: ['a'] },
    { stepId: 'c', dependsOn: [], prerequisites: ['a', 'b'] }
  ];
  assert.deepEqual(dependsOnOf(steps), [undefined, undefined, undefined]);
});

test('when a step declares dependsOn, each step depends on its closest prerequisites, so a join keeps its branches', () => {
  const steps = [
    { stepId: 'menu', dependsOn: [], prerequisites: [] },
    { stepId: 'x', dependsOn: ['menu'], prerequisites: ['menu'] },
    { stepId: 'y', dependsOn: ['menu'], prerequisites: ['menu'] },
    { stepId: 'join', dependsOn: [], prerequisites: ['menu', 'x', 'y'] }
  ];
  assert.deepEqual(dependsOnOf(steps), [[], ['menu'], ['menu'], ['x', 'y']]);
});
