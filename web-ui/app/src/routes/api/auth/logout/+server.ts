import { clearSessionCookie } from '#lib/server/auth.js';

export const POST = async ({ cookies }) => {
  clearSessionCookie(cookies);
  return new Response(null, { status: 204 });
};
