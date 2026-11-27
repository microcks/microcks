import assert from 'node:assert/strict';
import { test } from 'node:test';
import { runsUrl } from './api-snippet.ts';

const doc = { apiId: '6b0a0911f72a61cade13e193', title: 'Order scenarios', version: '1.0.0' };

test('a real document is run by its title and version, as the API accepts it', () => {
  assert.equal(
    runsUrl('http://localhost:8080', doc, 'placeOrder', false),
    'http://localhost:8080/api/arazzo/documents/Order%20scenarios:1.0.0/workflows/placeOrder/runs'
  );
});

test('the demo documents keep the id of the mockup', () => {
  assert.equal(runsUrl('http://microcks.local:8080', doc, 'placeOrder', true), 'http://microcks.local:8080/api/arazzo/documents/6b0a0911f72a61cade13e193/workflows/placeOrder/runs');
});

test('characters of a title that would break the URL are escaped, the colon is kept', () => {
  assert.equal(runsUrl('http://h', { apiId: 'x', title: 'A/B & C', version: '1.0' }, 'w', false), 'http://h/api/arazzo/documents/A%2FB%20%26%20C:1.0/workflows/w/runs');
});
