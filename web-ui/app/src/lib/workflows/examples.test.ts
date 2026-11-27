import assert from 'node:assert/strict';
import { test } from 'node:test';
import { exampleCheckOf, judgedAs, toWorkflowExample } from './examples.ts';

test('a recorded example of the backend becomes an example of the screens, named by its name', () => {
  const example = toWorkflowExample({
    workflowId: 'listThenReadFirst',
    name: 'unknown-first-pastry',
    inputs: {},
    outputs: {},
    steps: [
      { stepId: 'list', status: 200, outputs: { first: 'Baba Rhum' } },
      { stepId: 'readFirst', status: 404, outputs: {} }
    ],
    stopsAt: 'readFirst'
  });
  assert.equal(example.id, 'unknown-first-pastry');
  assert.equal(example.stopsAt, 'readFirst');
  assert.deepEqual(example.steps[0], { stepId: 'list', status: 200, outputs: { first: 'Baba Rhum' } });
});

test('a recorded example with no stop and no outputs says it runs to its end', () => {
  const example = toWorkflowExample({ workflowId: 'w', name: 'happy', inputs: { size: 'S' }, outputs: { price: 3.2 }, steps: [] });
  assert.equal(example.stopsAt, undefined);
  assert.deepEqual(example.inputs, { size: 'S' });
  assert.deepEqual(example.outputs, { price: 3.2 });
});

test('the judgement of a run is the backend verdict, with the expectations it did not meet', () => {
  assert.deepEqual(exampleCheckOf({ name: 'a', verdict: 'DOES_NOT_MATCH', unmetExpectations: ['Step readFirst: expecting 200 but got 404'] }), {
    matches: false,
    unmet: ['Step readFirst: expecting 200 but got 404']
  });
  assert.deepEqual(exampleCheckOf({ name: 'a', verdict: 'MATCHES' }), { matches: true, unmet: [] });
  assert.equal(exampleCheckOf({ name: 'a' }), undefined, 'a run still in progress has no verdict yet');
  assert.equal(exampleCheckOf(undefined), undefined);
});

test('a run that fails as its example expects still matches it', () => {
  assert.equal(judgedAs({ status: 'FAILURE', exampleCheck: { matches: true, unmet: [] } }), true);
  assert.equal(judgedAs({ status: 'SUCCESS', exampleCheck: { matches: false, unmet: ['x'] } }), false);
  assert.equal(judgedAs({ status: 'SUCCESS' }), true, 'without a backend verdict (demo runs) the run outcome decides');
  assert.equal(judgedAs({ status: 'FAILURE' }), false);
});
