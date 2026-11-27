import { error, text } from '@sveltejs/kit';
import { getSource } from '#lib/server/workflows.js';

export const GET = ({ params }) => {
  const yaml = getSource(params.docId);
  if (yaml === undefined) error(404, `Document "${params.docId}" does not exist.`);
  return text(yaml, { headers: { 'content-type': 'application/yaml; charset=utf-8' } });
};
