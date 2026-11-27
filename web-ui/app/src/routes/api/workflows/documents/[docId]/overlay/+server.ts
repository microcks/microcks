import { error, text } from '@sveltejs/kit';
import { getOverlay } from '#lib/server/workflows.js';

export const GET = ({ params }) => {
  const yaml = getOverlay(params.docId);
  if (yaml === undefined) error(404, `Document "${params.docId}" does not exist.`);
  if (yaml === null) error(404, `Document "${params.docId}" has no overlay.`);
  return text(yaml, { headers: { 'content-type': 'application/yaml; charset=utf-8' } });
};
