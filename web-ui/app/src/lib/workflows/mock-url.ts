import type { RunOrigin, WorkflowInput } from './types.ts';

/** The Mock URL of a workflow (story S27): the title writes its spaces as `+`, like the Mock URLs of APIs. */
export function mockUrlOf(base: string, title: string, version: string, workflowId: string): string {
  return `${base}/arazzo/${encodeURIComponent(title).replace(/%20/g, '+')}/${encodeURIComponent(version)}/${encodeURIComponent(workflowId)}`;
}

/** Where a run came from: Microcks records MOCK_URL for the runs of an application posting to a Mock URL. */
export function originOf(origin: string | null | undefined): RunOrigin {
  return origin === 'MOCK_URL' ? { kind: 'mock-url' } : { kind: 'api' };
}

/** A body for the Mock URL curl: the example of each input, else a value of its type. */
export function sampleInputs(inputs: readonly WorkflowInput[]): Record<string, string | number | boolean> {
  return Object.fromEntries(
    inputs.map((i) => {
      const numeric = i.type === 'number' || i.type === 'integer';
      if (i.example !== undefined) return [i.name, numeric ? Number(i.example) : i.type === 'boolean' ? i.example === 'true' : i.example];
      return [i.name, numeric ? 0 : i.type === 'boolean' ? false : ''];
    })
  );
}
