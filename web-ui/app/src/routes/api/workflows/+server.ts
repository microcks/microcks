import { json } from '@sveltejs/kit';
import { listAll } from '#lib/server/workflows.js';

/** PLACEHOLDER: stands for the future Microcks workflows listing (Arazzo documents, workflows, runs). */
export const GET = () => json(listAll());
