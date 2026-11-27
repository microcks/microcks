import { redirect } from '@sveltejs/kit';
import type { Service } from '#lib/types.js';

export interface Session {
  enabled: boolean;
  authenticated: boolean;
  user: { name: string; username: string; email: string | null; roles: string[] } | null;
}

export const load = async ({ fetch, depends }) => {
  depends('app:sources');
  const session: Session | null = await fetch('/api/auth/session').then((r) => (r.ok ? r.json() : null), () => null);
  if (session && !session.authenticated) redirect(307, '/login');
  const res = await fetch('/api/services');
  if (res.status === 401) redirect(307, '/login');
  const services: Service[] = await res.json();
  return { services, session };
};
