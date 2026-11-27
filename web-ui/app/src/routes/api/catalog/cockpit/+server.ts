import { json } from '@sveltejs/kit';
import { cockpit } from '#lib/server/catalog.js';

export const GET = async ({ locals }) => json(await cockpit(locals.token));
