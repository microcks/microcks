import { error, redirect } from '@sveltejs/kit';
import type { Service } from '#lib/types.js';

/** `/tests` has no screen of its own: it opens the history of a service. */
export const load = async ({ fetch }) => {
  const res = await fetch('/api/services');
  if (!res.ok) error(res.status, 'Services could not be loaded.');
  const services: Service[] = await res.json();
  const target = services.find((s) => s.id === 'api-pastry-2-0-2-0-0') ?? services.find((s) => s.protocol === 'REST') ?? services[0];
  if (!target) redirect(307, '/tests/create');
  redirect(307, `/tests/service/${encodeURIComponent(target.id)}`);
};
