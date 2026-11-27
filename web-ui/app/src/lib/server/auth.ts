import type { Cookies } from '@sveltejs/kit';

/** Base URL of the Microcks backend, whose /api/auth endpoints issue the session token. */
export const MICROCKS_API_URL = (process.env.MICROCKS_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');

const SESSION_COOKIE = 'microcks_session';
const INFO_TTL_MS = 10_000;

export interface AuthInfo {
  enabled: boolean;
  oidcEnabled: boolean;
}

export interface SessionUser {
  name: string;
  username: string;
  email: string | null;
  roles: string[];
}

let cachedInfo: { value: AuthInfo; at: number } | undefined;

/**
 * Ask the backend whether authentication is enforced. An unreachable backend is treated as "no authentication" so
 * that the UI keeps working standalone; the backend remains the only authority on access decisions.
 */
export async function getAuthInfo(): Promise<AuthInfo> {
  if (cachedInfo && Date.now() - cachedInfo.at < INFO_TTL_MS) return cachedInfo.value;
  let value: AuthInfo = { enabled: false, oidcEnabled: false };
  try {
    const res = await fetch(`${MICROCKS_API_URL}/api/auth/config`, { signal: AbortSignal.timeout(3000) });
    if (res.ok) {
      const body = await res.json();
      value = { enabled: body.enabled === true, oidcEnabled: body.oidcEnabled === true };
    }
  } catch {
    /* backend not reachable */
  }
  cachedInfo = { value, at: Date.now() };
  return value;
}

/** Decode the payload of a JWT. The signature is not checked here: the backend verifies it on every call. */
export function decodeClaims(token: string): Record<string, unknown> | undefined {
  const parts = token.split('.');
  if (parts.length !== 3) return undefined;
  try {
    return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
  } catch {
    return undefined;
  }
}

/** Seconds before expiry, or undefined when the token is malformed or already expired. */
export function remainingSeconds(token: string): number | undefined {
  const exp = decodeClaims(token)?.exp;
  if (typeof exp !== 'number') return undefined;
  const left = Math.floor(exp - Date.now() / 1000);
  return left > 0 ? left : undefined;
}

/** The Microcks token stored in the HttpOnly session cookie, if still valid. */
export function getSessionToken(cookies: Cookies): string | undefined {
  const token = cookies.get(SESSION_COOKIE);
  return token && remainingSeconds(token) !== undefined ? token : undefined;
}

export function setSessionCookie(cookies: Cookies, token: string, secure: boolean): void {
  cookies.set(SESSION_COOKIE, token, {
    path: '/',
    httpOnly: true,
    sameSite: 'lax',
    secure,
    maxAge: remainingSeconds(token) ?? 0
  });
}

export function clearSessionCookie(cookies: Cookies): void {
  cookies.delete(SESSION_COOKIE, { path: '/' });
}

export function userFromToken(token: string): SessionUser | undefined {
  const claims = decodeClaims(token);
  if (!claims) return undefined;
  const username = String(claims.sub ?? '');
  return {
    name: String(claims.name ?? username),
    username,
    email: typeof claims.email === 'string' ? claims.email : null,
    roles: Array.isArray(claims.roles) ? claims.roles.map(String) : []
  };
}
