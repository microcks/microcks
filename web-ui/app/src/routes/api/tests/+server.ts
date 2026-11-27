import { error, json } from '@sveltejs/kit';
import { BackendError, launchBackendTest } from '#lib/server/backend.js';
import { validateTestRequest } from '#lib/server/store.js';
import { toRunDto } from '#lib/server/testing.js';

/** Microcks: POST /api/tests (TestRequestDTO) -> TestResult. The backend launches the test and answers while it runs. */
export const POST = async ({ request, locals }) => {
  const b = await request.json();
  if (!b || typeof b.serviceId !== 'string' || typeof b.endpoint !== 'string') error(400, 'serviceId and endpoint are required');
  const operations: string[] = Array.isArray(b.operations) ? b.operations.map(String) : [];
  const headers: { scope?: string; name: string; value: string }[] = Array.isArray(b.headers)
    ? b.headers.map((h: { scope?: unknown; name: unknown; value: unknown }) => ({ scope: h.scope ? String(h.scope) : undefined, name: String(h.name), value: String(h.value) }))
    : [];
  const req = {
    serviceId: b.serviceId,
    endpoint: b.endpoint.trim(),
    runner: String(b.runner ?? ''),
    timeoutMs: Number(b.timeoutMs),
    secretName: b.secretName || undefined,
    operations,
    headers
  };
  const invalid = validateTestRequest(req);
  if (invalid) error(422, invalid);

  const operationsHeaders: Record<string, { name: string; values: string }[]> = {};
  for (const h of headers) (operationsHeaders[h.scope || 'globals'] ??= []).push({ name: h.name, values: h.value });
  try {
    const result = await launchBackendTest(
      {
        serviceId: req.serviceId,
        testEndpoint: req.endpoint,
        runnerType: req.runner,
        timeout: req.timeoutMs,
        secretName: req.secretName,
        filteredOperations: operations.length ? operations : undefined,
        operationsHeaders: headers.length ? operationsHeaders : undefined
      },
      locals.token
    );
    return json(toRunDto(result), { status: 201 });
  } catch (e) {
    if (e instanceof BackendError) error(e.status, e.message);
    throw e;
  }
};
