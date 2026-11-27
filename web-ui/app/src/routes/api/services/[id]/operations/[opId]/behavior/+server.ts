import { error, json } from '@sveltejs/kit';
import { saveBehavior } from '#lib/server/catalog.js';
import { DISPATCHERS } from '#lib/components/catalog/dispatch.js';
import type { Behavior } from '#lib/components/catalog/types.js';

/** Microcks: PUT /api/services/{id}/operation (dispatcher, rules and delay of an operation). */
export const PUT = async ({ params, request, locals }) => {
  const b = (await request.json().catch(() => null)) as Behavior | null;
  if (!b || !DISPATCHERS.some((d) => d.id === b.dispatcher)) error(400, 'Unknown dispatcher');
  if (!Number.isFinite(b.delayMs) || b.delayMs < 0 || b.delayMs > 60000) error(400, 'Delay must be between 0 and 60000 ms');
  const saved = await saveBehavior(params.id, params.opId, {
    dispatcher: b.dispatcher,
    rule: String(b.rule ?? ''),
    delayMs: Math.round(b.delayMs),
    delayStrategy: b.delayStrategy === 'random' ? 'random' : 'fixed',
    fallback: b.fallback,
    proxyUrl: b.proxyUrl,
    baseDispatcher: b.baseDispatcher === 'URI_PARAMS' ? 'URI_PARAMS' : 'URI_PARTS',
    stateful: Boolean(b.stateful),
    constraints: Array.isArray(b.constraints)
      ? b.constraints.map((c) => ({ name: String(c.name ?? ''), in: c.in === 'query' ? 'query' : 'header', required: Boolean(c.required), recopy: Boolean(c.recopy), mustMatchRegexp: c.mustMatchRegexp ? String(c.mustMatchRegexp) : undefined }))
      : [],
    scriptLanguage: b.scriptLanguage === 'JS' ? 'JS' : 'GROOVY',
    script: typeof b.script === 'string' ? b.script : undefined
  }, locals.token);
  if (!saved) error(404, 'Operation not found');
  return json(saved);
};
