import { json } from '@sveltejs/kit';
import { listServices } from '#lib/server/store.js';

export const GET = () => json(listServices());
