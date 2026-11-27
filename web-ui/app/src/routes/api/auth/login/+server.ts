import { error } from '@sveltejs/kit';
import { MICROCKS_API_URL, getAuthInfo } from '#lib/server/auth.js';

/** Start the OIDC login: the Microcks backend talks to the IDP and redirects back to our callback with a token. */
export const GET = async ({ url }) => {
  const info = await getAuthInfo();
  if (!info.enabled || !info.oidcEnabled) error(404, 'OIDC login is not enabled');
  // adapter-node 6 assumes https when no protocol header is set: the public URL can be pinned with ORIGIN.
  const origin = (process.env.ORIGIN ?? url.origin).replace(/\/+$/, '');
  const callback = `${origin}/api/auth/callback`;
  // The target is the backend (another origin): SvelteKit's redirect() refuses external locations.
  const location = `${MICROCKS_API_URL}/api/auth/login/oidc?redirect_uri=${encodeURIComponent(callback)}`;
  return new Response(null, { status: 302, headers: { location } });
};
