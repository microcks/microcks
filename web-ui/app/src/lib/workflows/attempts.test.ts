import assert from 'node:assert/strict';
import { test } from 'node:test';
import { attemptsOf, groupByStep, toActions } from './attempts.ts';

const entry = (stepId: string, status: 'PASSED' | 'FAILED', statusCode: number | null, elapsedTime: number) => ({ stepId, status, statusCode, elapsedTime });

test('the retries of a step are entries of the run with the same step id, in order', () => {
  const grouped = groupByStep([entry('a', 'PASSED', 200, 5), entry('b', 'FAILED', 503, 7), entry('b', 'FAILED', 503, 9), entry('b', 'PASSED', 200, 4)]);
  assert.deepEqual([...grouped.keys()], ['a', 'b']);
  assert.equal(grouped.get('b')!.length, 3);
});

test('each attempt has its number, status and duration, and waits the retryAfter of its action before it', () => {
  const attempts = attemptsOf([entry('b', 'FAILED', 503, 7), entry('b', 'PASSED', 200, 4)], 'T0', 1.5);
  assert.deepEqual(attempts, [
    { n: 1, httpStatus: 503, ms: 7, when: 'T0' },
    { n: 2, httpStatus: 200, ms: 4, when: 'T0', waitMs: 1500 }
  ]);
});

test('an attempt without a response has HTTP status 0 and no wait when the action waits nothing', () => {
  assert.deepEqual(attemptsOf([entry('b', 'FAILED', null, 3), entry('b', 'FAILED', null, 2)], 'T0', 0)[1], { n: 2, httpStatus: 0, ms: 2, when: 'T0' });
});

test('only retry and end actions are shown, goto is not part of what Microcks runs', () => {
  assert.deepEqual(
    toActions([
      { name: 'again', type: 'retry', retryAfter: 2, retryLimit: 3, criteria: [{ condition: '$statusCode == 503' }] },
      { name: 'stop', type: 'end' },
      { name: 'jump', type: 'goto' }
    ]),
    [
      { type: 'retry', name: 'again', retryAfter: 2, retryLimit: 3, criteria: [{ type: 'simple', condition: '$statusCode == 503' }] },
      { type: 'end', name: 'stop' }
    ]
  );
  assert.deepEqual(toActions(undefined), []);
  assert.deepEqual(toActions([{ name: 'r', type: 'retry' }]), [{ type: 'retry', name: 'r', retryAfter: 0, retryLimit: 1 }]);
});
