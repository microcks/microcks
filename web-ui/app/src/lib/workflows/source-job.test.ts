import assert from 'node:assert/strict';
import { test } from 'node:test';
import { documentOrigin, unlinkedWarning, jobOfDocument } from './source-job.ts';

const job = { id: 'j1', name: 'Order journeys (Git)', lastImportDate: 1_000, arazzoDocumentRef: { documentId: 'd1', title: 'Order journeys', version: '1.0.0', unlinkedSources: ['orderApi'] } };

test('a document produced by a job is found from the document reference of the job', () => {
  assert.equal(jobOfDocument([{ id: 'j0', name: 'x', lastImportDate: 1 }, job], 'd1')?.id, 'j1');
  assert.equal(jobOfDocument([job], 'd2'), undefined);
});

test('the origin of a document says whether a job synced it or it was imported once', () => {
  const ago = (ms: number) => `${ms} ms ago`;
  assert.equal(documentOrigin(job, 5_000, ago), 'Synced 1000 ms ago from Order journeys (Git)');
  assert.equal(documentOrigin(undefined, 5_000, ago), 'Imported 5000 ms ago');
  assert.equal(documentOrigin(undefined, undefined, ago), 'Imported');
});

test('the sources a job left not linked are named in a warning, none means no warning', () => {
  assert.equal(unlinkedWarning(['orderApi']), 'Source description orderApi is not linked to an API yet: its workflows cannot run.');
  assert.equal(unlinkedWarning(['a', 'b']), 'Source descriptions a, b are not linked to an API yet: their workflows cannot run.');
  assert.equal(unlinkedWarning([]), undefined);
  assert.equal(unlinkedWarning(undefined), undefined);
});
