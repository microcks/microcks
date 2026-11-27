import assert from 'node:assert/strict';
import { test } from 'node:test';
import { callsOneOf, distinctSources, sourceKey } from './source-key.ts';
import type { ArazzoDocument, SourceDescription, Workflow } from './types.ts';

const linked = (name: string, serviceId: string): SourceDescription => ({
  name,
  type: 'openapi',
  url: '',
  link: { status: 'linked', serviceId, protocol: 'REST', operationsFound: '1 of 1 found' }
});
const unlinked = (name: string): SourceDescription => ({ name, type: 'openapi', url: '', link: { status: 'not-linked', expectedFile: name } });
const document = (id: string, sources: SourceDescription[]): ArazzoDocument => ({
  id, apiId: id, file: `${id}.yaml`, title: id, arazzoVersion: '1.0.1', version: '1', origin: '', sources, workflowIds: ['wf'], importIssues: []
});
const workflow = (documentId: string, sourceName: string): Workflow => ({
  workflowId: 'wf', documentId, summary: '', inputs: [], outputs: [], mockUrl: '', runnability: { runnable: true },
  steps: [{ stepId: 's', sourceName, operationId: 'op', method: 'GET', path: '/', description: '', parameters: [], successCriteria: [], onSuccess: [], onFailure: [], outputs: [] }]
});

test('two documents naming a source alike keep one chip per API they link it to', () => {
  const docs = [document('a', [linked('orderApi', 'o1')]), document('b', [linked('orderApi', 'o1')]), document('c', [unlinked('orderApi')])];
  assert.deepEqual(distinctSources(docs).map(sourceKey), ['orderApi|o1', 'orderApi|']);
});

test('a source filter keeps the workflows that call that source description through their own document', () => {
  const docs = [document('a', [linked('orderApi', 'o1')]), document('c', [unlinked('orderApi')])];
  const workflows = [workflow('a', 'orderApi'), workflow('c', 'orderApi')];
  assert.equal(callsOneOf(workflows[0]!, docs[0]!, ['orderApi|o1']), true);
  assert.equal(callsOneOf(workflows[1]!, docs[1]!, ['orderApi|o1']), false);
  assert.equal(callsOneOf(workflows[1]!, docs[1]!, ['orderApi|']), true);
});
