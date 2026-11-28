import { error, text } from '@sveltejs/kit';
import { getDocumentExamples } from '#lib/server/workflows.js';

export const GET = async ({ params, locals }) => {
  const yaml = await getDocumentExamples(params.docId, locals.token);
  if (yaml === undefined) error(404, `Document "${params.docId}" does not exist.`);
  if (yaml === null) error(404, `Document "${params.docId}" has no ArazzoExamples.`);
  return text(yaml, { headers: { 'content-type': 'application/yaml; charset=utf-8' } });
};
