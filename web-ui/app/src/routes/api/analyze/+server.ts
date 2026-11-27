import { error, json } from '@sveltejs/kit';
import { analyze, fetchArtifact } from '#lib/server/store.js';
import type { SourceOrigin } from '#lib/types.js';

export const POST = async ({ request, locals }) => {
  const body = (await request.json()) as { origin: SourceOrigin; url?: string; secret?: string; fileName?: string; content?: string };
  try {
    if (body.url) {
      const { fileName, content } = await fetchArtifact(body.url, body.secret || undefined, locals.token);
      return json(analyze(body.origin, fileName, content, body.url));
    }
    if (body.fileName && typeof body.content === 'string') return json(analyze(body.origin, body.fileName, body.content));
  } catch (e) {
    error(422, e instanceof Error ? e.message : 'Could not fetch the artifact');
  }
  error(400, 'Provide a url, or a fileName and content');
};
