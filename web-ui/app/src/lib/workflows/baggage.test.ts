import assert from 'node:assert/strict';
import { test } from 'node:test';
import { callsMocks, traceAttributes } from './baggage.ts';

test('the mocks tag the spans of a call with the run, workflow, step and attempt of its baggage', () => {
  assert.deepEqual(traceAttributes('r1', 'wf', 'getPastry', 2), [
    { name: 'arazzo.run.id', value: 'r1' },
    { name: 'arazzo.workflow.id', value: 'wf' },
    { name: 'arazzo.step.id', value: 'getPastry' },
    { name: 'arazzo.attempt', value: '2' }
  ]);
});

test('only the calls to mocks carry the baggage: every call of a mocks run, the mock sources of a mixed run, none of an implementations run', () => {
  assert.equal(callsMocks({ kind: 'mocks' }, 'a'), true);
  assert.equal(callsMocks({ kind: 'implementations', baseUrls: {} }, 'a'), false);
  const mixed = { kind: 'mixed' as const, perSource: { a: 'mock' as const, b: 'implementation' as const } };
  assert.equal(callsMocks(mixed, 'a'), true);
  assert.equal(callsMocks(mixed, 'b'), false);
});
