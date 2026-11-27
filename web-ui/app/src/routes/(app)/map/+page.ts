import { buildMap, routePatterns } from '#lib/components/observe/screen-map.js';

interface ServiceLite {
  id: string;
  name: string;
  protocol: string;
  operations: { name: string }[];
}

// Parentheses are glob syntax: the (app) group is matched by the wildcard.
const pages = import.meta.glob('/src/routes/**/+page.svelte');

export const load = async ({ fetch }) => {
  const services = (await fetch('/api/services').then((r) => (r.ok ? r.json() : []), () => [])) as ServiceLite[];
  const service = services.find((s) => s.protocol === 'REST' && /pastry/i.test(s.name)) ?? services[0];
  const history = service
    ? ((await fetch(`/api/tests/service/${encodeURIComponent(service.id)}`).then((r) => (r.ok ? r.json() : []), () => [])) as { id: string }[])
    : [];
  const detail = service
    ? ((await fetch(`/api/services/${encodeURIComponent(service.id)}`).then((r) => (r.ok ? r.json() : undefined), () => undefined)) as
        | { operations?: { id: string }[] }
        | undefined)
    : undefined;
  const existing = routePatterns(Object.keys(pages));
  const phases = buildMap(
    {
      serviceId: service?.id,
      operationId: detail?.operations?.[0]?.id,
      testId: Array.isArray(history) ? history[0]?.id : undefined
    },
    existing
  );
  return { phases, total: phases.reduce((n, p) => n + p.screens.length, 0) };
};
