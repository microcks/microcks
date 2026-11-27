import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isAvailable, isDemo, isFilterAvailable, PORTED, unavailableReason, type ArazzoFeature } from './availability.ts';

const REAL = '6b0a060c8823ea1867fd73da';
const FEATURES = Object.keys(PORTED) as ArazzoFeature[];

test('a placeholder document keeps every feature, as in the mockup', () => {
  for (const feature of FEATURES) {
    assert.equal(isAvailable(feature, 'pastry'), true);
    assert.equal(unavailableReason(feature, { id: 'beer' }), undefined);
  }
});

test('demo data is told from the flag first, then from the id', () => {
  assert.equal(isDemo({ id: REAL, demo: true }), true);
  assert.equal(isDemo({ id: 'pastry', demo: false }), false);
  assert.equal(isDemo(REAL), false);
  assert.equal(isDemo(undefined), true);
  assert.equal(isDemo(true), true);
});

test('a feature that is not ported is unavailable on a real document, with a reason', () => {
  for (const feature of FEATURES.filter((f) => !PORTED[f])) {
    assert.equal(isAvailable(feature, REAL), false);
    assert.match(unavailableReason(feature, { id: REAL }) ?? '', /^Not available yet/);
  }
});

test('AI generation, document edit, Mock URL and sessions are not available yet on real documents', () => {
  for (const feature of ['ai-copilot', 'document-edit', 'mock-url', 'sessions'] as const) assert.equal(isAvailable(feature, REAL), PORTED[feature]);
});

test('the Mock URL and Session filters are off on a real document, the others stay on', () => {
  assert.equal(isFilterAvailable('mock-url', REAL), false);
  assert.equal(isFilterAvailable('session', REAL), false);
  for (const filter of ['any', 'all', 'mocks', 'implementations']) assert.equal(isFilterAvailable(filter, REAL), true);
  assert.equal(isFilterAvailable('mock-url', 'pastry'), true);
});
