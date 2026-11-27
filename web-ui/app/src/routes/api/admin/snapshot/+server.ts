import { json } from '@sveltejs/kit';
import { snapshotServices } from '#lib/server/snapshot.js';
import type { SnapshotServiceRef } from '#lib/admin.js';

/** Services a snapshot can hold. */
export const GET = () =>
  json(
    snapshotServices().map(
      (s): SnapshotServiceRef => ({ id: s.id, name: s.name, version: s.version, protocol: s.protocol, operationCount: s.operations.length })
    )
  );
