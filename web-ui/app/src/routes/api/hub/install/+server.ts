import { error, json } from '@sveltejs/kit';
import { BackendError } from '#lib/server/backend.js';
import { installApiVersion } from '#lib/server/hub.js';
import { refreshServices } from '#lib/server/store.js';

export const POST = async ({ request, locals }) => {
  const body = (await request.json().catch(() => ({}))) as {
    label?: string;
    mode?: string;
    role?: string;
    contracts?: { type?: string; url?: string }[];
  };
  const contracts = (body.contracts ?? []).filter(
    (c): c is { type: string; url: string } => typeof c.type === 'string' && typeof c.url === 'string' && /^https?:\/\//.test(c.url)
  );
  if (!contracts.length) error(400, { message: 'No contract to install.' });
  try {
    const artifacts = await installApiVersion(
      {
        label: body.label?.trim() || 'Hub API',
        mode: body.mode === 'one-shot' ? 'one-shot' : 'sync',
        role: body.role === 'secondary' ? 'secondary' : 'main',
        contracts
      },
      locals.token
    );
    await refreshServices(locals.token, true);
    return json({ status: 'installed', artifacts }, { status: 201 });
  } catch (e) {
    if (e instanceof BackendError) error(e.status as 502, { message: e.message });
    throw e;
  }
};
