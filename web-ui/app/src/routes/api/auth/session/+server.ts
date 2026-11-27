import { json } from '@sveltejs/kit';
import { getAuthInfo, getSessionToken, userFromToken } from '#lib/server/auth.js';

export const GET = async ({ cookies }) => {
  const info = await getAuthInfo();
  const token = getSessionToken(cookies);
  const user = token ? (userFromToken(token) ?? null) : null;
  return json({
    enabled: info.enabled,
    oidcEnabled: info.oidcEnabled,
    authenticated: !info.enabled || token !== undefined,
    user
  });
};
