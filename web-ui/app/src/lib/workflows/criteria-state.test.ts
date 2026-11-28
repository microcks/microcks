import assert from 'node:assert/strict';
import { test } from 'node:test';
import { criteriaState } from './criteria-state.ts';

const held = { condition: '$statusCode == 200', kind: 'simple', evaluation: '$statusCode = 200', held: true } as const;

test('a step that was not run has no criteria state', () => {
  assert.equal(criteriaState({ status: 'not-run', criteria: [] }), 'not-run');
});

test('a failed step whose criteria could not be evaluated says so instead of claiming they held', () => {
  assert.equal(criteriaState({ status: 'failed', criteria: [] }), 'not-evaluated');
});

test('evaluated criteria are held only when every one held', () => {
  assert.equal(criteriaState({ status: 'passed', criteria: [held] }), 'held');
  assert.equal(criteriaState({ status: 'failed', criteria: [held, { ...held, held: false }] }), 'failed');
});
