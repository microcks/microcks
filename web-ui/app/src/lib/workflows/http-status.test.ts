import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isHttpOk, statusLabel } from './http-status.ts';

test('a step that got no response says so instead of showing the status 0', () => {
  assert.equal(statusLabel(200), '200');
  assert.equal(statusLabel(0), 'no response');
  assert.equal(statusLabel(undefined), 'no response');
});

test('only a status under 300 is a success: no response is not', () => {
  assert.equal(isHttpOk(204), true);
  assert.equal(isHttpOk(404), false);
  assert.equal(isHttpOk(0), false);
});
