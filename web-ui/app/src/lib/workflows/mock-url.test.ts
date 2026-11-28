import assert from 'node:assert/strict';
import { test } from 'node:test';
import { mockUrlOf, originOf, sampleInputs } from './mock-url.ts';

test('the Mock URL writes the spaces of the title as + like the Mock URLs of APIs', () => {
  assert.equal(mockUrlOf('http://localhost:8080', 'Pastry tasting', '1.0.0', 'tasteThenReprice'), 'http://localhost:8080/workflow/Pastry+tasting/1.0.0/tasteThenReprice');
});

test('the Mock URL encodes the other characters of the title and the version', () => {
  assert.equal(mockUrlOf('', 'Orders & returns', '1.0 beta', 'wf'), '/workflow/Orders+%26+returns/1.0%20beta/wf');
});

test('a run recorded with the origin MOCK_URL was called through the Mock URL, any other run through the UI or API', () => {
  assert.deepEqual(originOf('MOCK_URL'), { kind: 'mock-url' });
  assert.deepEqual(originOf(undefined), { kind: 'api' });
  assert.deepEqual(originOf(null), { kind: 'api' });
});

test('a run recorded with the origin SESSION verifies a session, whose id the run keeps', () => {
  assert.deepEqual(originOf('SESSION', 's1'), { kind: 'session', sessionId: 's1', app: 'the application' });
  assert.deepEqual(originOf('SESSION'), { kind: 'session', sessionId: '', app: 'the application' });
});

test('the sample body of the Mock URL curl keeps the example of an input and gives the others a value of their type', () => {
  const inputs = [
    { name: 'customer', type: 'string' as const, example: 'laurent' },
    { name: 'newPrice', type: 'number' as const },
    { name: 'count', type: 'integer' as const, example: '3' },
    { name: 'gift', type: 'boolean' as const },
    { name: 'note', type: 'string' as const }
  ];
  assert.deepEqual(sampleInputs(inputs), { customer: 'laurent', newPrice: 0, count: 3, gift: false, note: '' });
});
