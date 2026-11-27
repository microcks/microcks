import { error, redirect } from '@sveltejs/kit';
import { remainingSeconds, setSessionCookie } from '#lib/server/auth.js';

/** Receive the Microcks token issued by the backend after the IDP login and keep it in an HttpOnly cookie. */
export const GET = async ({ url, cookies }) => {
  const token = url.searchParams.get('token');
  if (!token || remainingSeconds(token) === undefined) error(400, 'Missing or expired token');
  setSessionCookie(cookies, token, url.protocol === 'https:');
  redirect(303, '/');
};
