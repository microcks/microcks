import { json, redirect } from '@sveltejs/kit';
import type { Handle } from '@sveltejs/kit/hooks';
import { getAuthInfo, getSessionToken } from '#lib/server/auth.js';
import { refreshServices } from '#lib/server/store.js';

const PUBLIC_PREFIXES = ['/api/auth/', '/login', '/_app/'];

/**
 * Guard for the UI server. When Microcks enforces authentication, API calls need the session cookie and documents
 * redirect to the login page. The backend stays the source of truth: it re-verifies the token on each proxied call.
 */
export const handle: Handle = async ({ event, resolve }) => {
  const { pathname } = event.url;
  if (PUBLIC_PREFIXES.some((p) => pathname === p.replace(/\/$/, '') || pathname.startsWith(p))) {
    return resolve(event);
  }

  const info = await getAuthInfo();
  if (!info.enabled) {
    await refreshServices();
    return resolve(event);
  }

  const token = getSessionToken(event.cookies);
  if (token) {
    event.locals.token = token;
    await refreshServices(token);
    return resolve(event);
  }

  if (pathname.startsWith('/api/')) {
    return json({ message: 'Authentication required' }, { status: 401 });
  }
  if (event.request.headers.get('accept')?.includes('text/html')) {
    redirect(303, '/login');
  }
  return resolve(event);
};
