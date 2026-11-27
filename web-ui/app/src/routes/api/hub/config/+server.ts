import { json } from '@sveltejs/kit';
import { hubEndpoint } from '#lib/server/hub.js';

/** The Hub is read by the browser, as in v1; only its endpoint is configured server-side. */
export const GET = async ({ locals }) => json({ endpoint: await hubEndpoint(locals.token) });
