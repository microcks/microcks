import { error } from '@sveltejs/kit';
import { readSample } from '#lib/server/store.js';

export const GET = ({ params }) => {
  const content = readSample(params.file);
  if (content === undefined) error(404, 'Unknown sample');
  return new Response(content, { headers: { 'content-type': 'text/plain; charset=utf-8' } });
};
