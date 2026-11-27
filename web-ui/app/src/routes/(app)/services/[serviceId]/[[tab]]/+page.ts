import { withMicrocksBase } from '#lib/base-url.js';
import type { ServiceDetail } from '#lib/components/catalog/types.js';
import type { TestRunDto } from '#lib/components/testing/model.js';

const absolute = (d: ServiceDetail): ServiceDetail => ({ ...d, mockBase: withMicrocksBase(d.mockBase) });

export const load = async ({ fetch, params, depends }) => {
  depends('catalog:service');
  depends('app:sources');
  depends('app:tests');
  const res = await fetch(`/api/services/${encodeURIComponent(params.serviceId)}`);
  if (!res.ok) return { detail: null as ServiceDetail | null, status: res.status, runs: [] as TestRunDto[] };
  const runsRes = await fetch(`/api/tests/service/${encodeURIComponent(params.serviceId)}?size=500`).catch(() => undefined);
  const runs: TestRunDto[] = runsRes?.ok ? await runsRes.json() : [];
  return { detail: absolute((await res.json()) as ServiceDetail), status: 200, runs };
};
