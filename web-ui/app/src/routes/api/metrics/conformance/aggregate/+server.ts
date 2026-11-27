import { json } from '@sveltejs/kit';
import { conformanceAggregate } from '#lib/server/testing.js';

/** Microcks: GET /api/metrics/conformance/aggregate -> [{ name, weight, value }] per `domain` label value. */
export const GET = () => json(conformanceAggregate());
