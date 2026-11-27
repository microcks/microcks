import type { AdminGroup, AdminUser, FeatureFlag, SecretSummary, SnapshotServiceRef } from '#lib/admin.js';

export const load = async ({ fetch, depends }) => {
  depends('app:admin');
  const get = async <T>(path: string): Promise<T> => (await fetch(path)).json();
  const [users, groups, secrets, snapshotServices, flags] = await Promise.all([
    get<AdminUser[]>('/api/admin/users'),
    get<AdminGroup[]>('/api/admin/groups'),
    get<SecretSummary[]>('/api/admin/secrets'),
    get<SnapshotServiceRef[]>('/api/admin/snapshot'),
    get<FeatureFlag[]>('/api/admin/config')
  ]);
  return { users, groups, secrets, snapshotServices, flags };
};
