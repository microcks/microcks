import { withMicrocksBase } from '#lib/base-url.js';
import type { ServiceDetail } from '#lib/components/catalog/types.js';

const absolute = (d: ServiceDetail): ServiceDetail => ({ ...d, mockBase: withMicrocksBase(d.mockBase) });

export const load = async ({ fetch, params, depends }) => {
  depends('catalog:service');
  const res = await fetch(`/api/services/${encodeURIComponent(params.serviceId)}`);
  if (!res.ok) return { detail: null as ServiceDetail | null, status: res.status, operationId: params.operationId };
  return { detail: absolute((await res.json()) as ServiceDetail), status: 200, operationId: params.operationId };
};
