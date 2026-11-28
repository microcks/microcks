import assert from 'node:assert/strict';
import { test } from 'node:test';
import { operationHref } from './operation-link.ts';

const document = {
  sources: [
    { name: 'pastryApi', link: { status: 'linked' as const, serviceId: 'svc1' } },
    { name: 'orderApi', link: { status: 'not-linked' as const, expectedFile: 'order' } }
  ]
};

test('an operation opens on the page of the API its source is linked to, on that operation', () => {
  assert.equal(operationHref(document, { sourceName: 'pastryApi', method: 'GET', path: '/pastry/{name}' }), '/services/svc1?op=get-pastry-name');
});

test('a step on a source that is not linked, or no document, falls back to the list of APIs', () => {
  assert.equal(operationHref(document, { sourceName: 'orderApi', method: 'POST', path: '/orders' }), '/services');
  assert.equal(operationHref(undefined, { sourceName: 'pastryApi', method: 'GET', path: '/x' }), '/services');
});
