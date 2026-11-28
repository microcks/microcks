import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isWorkflowArtifact, parseArtifact } from '../server/parser.ts';

const metadata = `apiVersion: mocks.microcks.io/v1alpha1
kind: ArazzoMetadata
metadata: { name: Pastry examples, version: 1.0.0 }
sources:
  pastryApi: { name: Pastry Examples API, version: 1.0.0 }
`;

const examples = `apiVersion: mocks.microcks.io/v1alpha1
kind: ArazzoExamples
metadata: { name: Pastry examples, version: 1.0.0 }
workflows:
  listThenReadFirst:
    unknown-first-pastry: { steps: { readFirst: { status: 404 } }, stopsAt: readFirst }
    first-pastry-read: { steps: { readFirst: { status: 200 } } }
`;

test('an ArazzoMetadata is a secondary workflow artifact that links the sources of its document', () => {
  const artifact = parseArtifact('pastry.arazzo-metadata.yaml', metadata);

  assert.equal(isWorkflowArtifact(artifact), true);
  assert.deepEqual(
    [artifact.kind, artifact.role, artifact.name, artifact.version, artifact.operations.map((o) => `${o.name}: ${o.resource}`)],
    ['arazzo-metadata', 'SECONDARY', 'Pastry examples', '1.0.0', ['pastryApi: Pastry Examples API 1.0.0']]
  );
});

test('an ArazzoExamples is a secondary workflow artifact that counts the examples of each workflow', () => {
  const artifact = parseArtifact('pastry.arazzo-examples.yaml', examples);

  assert.equal(isWorkflowArtifact(artifact), true);
  assert.deepEqual(
    [artifact.kind, artifact.role, artifact.name, artifact.version, artifact.operations.map((o) => `${o.name}: ${o.resource}`)],
    ['arazzo-examples', 'SECONDARY', 'Pastry examples', '1.0.0', ['listThenReadFirst: 2 examples']]
  );
});

test('the kind decides, not the name of the file', () => {
  assert.equal(parseArtifact('whatever.yaml', examples).kind, 'arazzo-examples');
  assert.equal(parseArtifact('whatever.yaml', metadata).kind, 'arazzo-metadata');
});
