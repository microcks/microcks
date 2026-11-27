import { getContext, setContext } from 'svelte';

/** Global roles asserted by the backend, from the least to the most privileged. */
export type Role = 'user' | 'manager' | 'admin';
const RANK: Record<Role, number> = { user: 0, manager: 1, admin: 2 };

/**
 * UI capabilities and the minimum role they require. Add an entry here, then guard the element with
 * `<Can action="...">` or `can('...')`. This only shapes the UI: the backend remains the enforcement point.
 */
export const POLICY = {
  'contract.import': 'manager',
  'admin.view': 'admin'
} as const satisfies Record<string, Role>;

export type Action = keyof typeof POLICY;

export interface SessionLike {
  enabled: boolean;
  user: { roles: string[] } | null;
}

/** True when one of the roles is at least `required` (admin > manager > user). */
export function hasRole(roles: readonly string[], required: Role): boolean {
  return roles.some((r) => r in RANK && RANK[r as Role] >= RANK[required]);
}

/** Authentication disabled means an anonymous, fully privileged instance. */
export function canWith(session: SessionLike | null | undefined, action: Action): boolean {
  if (!session?.enabled) return true;
  return !!session.user && hasRole(session.user.roles, POLICY[action]);
}

const KEY = Symbol('microcks.permissions');

/** Called once by the app layout; the getter keeps the check reactive to session changes. */
export function providePermissions(getSession: () => SessionLike | null | undefined) {
  const can = (action: Action) => canWith(getSession(), action);
  setContext(KEY, can);
  return can;
}

export function usePermissions(): (action: Action) => boolean {
  return getContext<(action: Action) => boolean>(KEY) ?? (() => true);
}
