import type { Action } from './permissions.js';

export const NAV_ITEMS: readonly { id: string; label: string; href: string; action?: Action }[] = [
  { id: 'cockpit', label: 'Cockpit', href: '/' },
  { id: 'catalogue', label: 'APIs & Services', href: '/services' },
  { id: 'workflows', label: 'Workflows', href: '/workflows' },
  { id: 'tests', label: 'Tests', href: '/tests' },
  { id: 'sources', label: 'Sources', href: '/sources' },
  { id: 'metrics', label: 'Usage & conformance', href: '/metrics' },
  { id: 'traces', label: 'Live traces', href: '/traces' },
  { id: 'hub', label: 'Hub', href: '/hub' },
  { id: 'admin', label: 'Administration', href: '/admin', action: 'admin.view' }
];

export function activeNav(pathname: string): string {
  if (pathname === '/') return 'cockpit';
  const match = NAV_ITEMS.find((n) => n.href !== '/' && (pathname === n.href || pathname.startsWith(`${n.href}/`)));
  if (match) return match.id;
  return pathname.startsWith('/services') ? 'catalogue' : '';
}
