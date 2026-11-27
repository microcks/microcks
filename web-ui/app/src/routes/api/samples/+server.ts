import { json } from '@sveltejs/kit';
import { sampleFiles } from '#lib/server/store.js';

export const GET = () => json(sampleFiles());
