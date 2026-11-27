import { json } from '@sveltejs/kit';
import { listWorkflowsPayload } from '#lib/server/workflows.js';

/** Arazzo documents and workflows from Microcks (`/api/arazzo/documents`); placeholder data when it is unreachable. */
export const GET = async ({ locals }) => json(await listWorkflowsPayload(locals.token));
