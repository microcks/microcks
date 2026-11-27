import { error, text } from '@sveltejs/kit';
import { getDocumentSource } from '#lib/server/workflows.js';

export const GET = async ({ params, locals }) => {
  const yaml = await getDocumentSource(params.docId, locals.token);
  if (yaml === undefined) error(404, `Document "${params.docId}" does not exist.`);
  return text(yaml, { headers: { 'content-type': 'application/yaml; charset=utf-8' } });
};
