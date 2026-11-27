import { json } from '@sveltejs/kit';
import { conformanceByDomain } from '#lib/server/testing.js';

/** Extension (no Microcks equivalent): conformance of every service grouped by `domain` label, for the risk treemap. */
export const GET = () => json(conformanceByDomain());
