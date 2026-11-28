import { error, text } from '@sveltejs/kit';
import { getDocumentOverlay } from '#lib/server/workflows.js';

export const GET = async ({ params, locals }) => {
  const yaml = await getDocumentOverlay(params.docId, locals.token);
  if (yaml === undefined) error(404, `Document "${params.docId}" does not exist.`);
  if (yaml === null) error(404, `Document "${params.docId}" has no overlay.`);
  return text(yaml, { headers: { 'content-type': 'application/yaml; charset=utf-8' } });
};
