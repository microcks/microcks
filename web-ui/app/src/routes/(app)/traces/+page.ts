import type { TraceTopology } from '#lib/components/observe/traces.js';

export const load = async ({ fetch }) => {
  const res = await fetch('/api/traces');
  if (!res.ok) {
    const body = (await res.json().catch(() => ({}))) as { message?: string };
    return { topology: undefined as TraceTopology | undefined, error: body.message ?? 'Live traces are unavailable.' };
  }
  return { topology: (await res.json()) as TraceTopology, error: undefined as string | undefined };
};
