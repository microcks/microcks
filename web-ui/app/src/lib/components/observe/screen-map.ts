/** Screens of the Microcks v2 Web UI, grouped by step of the contract-to-proof path. */
export interface MapScreen {
  n: string;
  title: string;
  /** Route pattern shown to the reader. */
  route: string;
  /** Route pattern with every parameter replaced by `:`, matched against the pages that exist. */
  match: string;
  /** Concrete URL built from real catalogue ids; undefined when the data it needs does not exist. */
  href?: string;
  what: string;
  wow: string;
  /** Delivery wave 1..7. */
  wave: number;
}

export interface MapPhase {
  step: number;
  name: string;
  verb: string;
  screens: MapScreen[];
}

export const WAVES = ['Mock', 'Test', 'Enrich', 'Observe', 'Govern', 'Orchestrate', 'Agents'] as const;

export interface MapIds {
  serviceId?: string;
  testId?: string;
  agentRunId?: string;
  operationId?: string;
}

/** Turns the page files of the app into patterns where each parameter is `:`; optional parameters give both forms. */
export function routePatterns(files: string[]): Set<string> {
  const out = new Set<string>();
  for (const f of files) {
    const path = f
      .replace(/^.*\/src\/routes\//, '/')
      .replace(/\/\([^/]+\)/g, '')
      .replace(/\/\+page\.svelte$/, '');
    const segments = path.split('/').filter(Boolean);
    if (segments.some((x) => x.startsWith('[...'))) continue;
    let forms: string[][] = [[]];
    for (const s of segments) {
      if (/^\[\[/.test(s)) forms = [...forms, ...forms.map((p) => [...p, ':'])];
      else forms = forms.map((p) => [...p, /^\[/.test(s) ? ':' : s]);
    }
    for (const p of forms) out.add(`/${p.join('/')}`);
  }
  return out;
}

const s = (n: string, title: string, route: string, match: string, href: string | undefined, what: string, wow: string, wave: number): MapScreen => ({
  n, title, route, match, href, what, wow, wave
});

export function buildMap(ids: MapIds, existing: Set<string>): MapPhase[] {
  const svc = ids.serviceId;
  const op = ids.operationId;
  const phases: MapPhase[] = [
    { step: 1, name: 'Import', verb: 'The contract comes in', screens: [
      s('01', 'Add a source', '/sources/new', '/sources/new', '/sources/new', 'Upload a file, a URL or a Git repository; Direct API wizard.', 'Detected services and operations previewed before import.', 1),
      s('02', 'Sources', '/sources', '/sources', '/sources', 'Every import: uploads, URLs, Git, Hub; scheduled sync.', 'Sync timeline, diff between two contract versions.', 3)
    ] },
    { step: 2, name: 'Explore', verb: 'See the landscape', screens: [
      s('03', 'Cockpit', '/', '/', '/', 'Services, mock traffic, recent tests, conformance.', 'Live wall of API badges, filterable by protocol.', 4),
      s('04', 'APIs & Services', '/services', '/services', '/services', 'Dense list, filters by label / protocol / test status.', 'Table ⇄ badge wall, unified ⌘K search.', 1)
    ] },
    { step: 3, name: 'Enrich', verb: 'Make the mock alive', screens: [
      s('05', 'Service', '/services/:id', '/services/:', svc && `/services/${svc}`, 'Operations, mock URL, examples, properties, MCP, webhooks.', '“Agent-ready” panel: MCP tools as an agent sees them.', 1),
      s('06', 'Behavior', '/services/:id/operations/:opId', '/services/:/operations/:', svc && op && `/services/${svc}/operations/${op}`, 'Dispatcher, rules, delays, parameter constraints, stateful.', 'Visual dispatcher builder with a request simulator.', 3),
      s('07', 'AI Copilot', '/services/:id/copilot', '/services/:/copilot', svc && `/services/${svc}/copilot`, 'Generate examples when the contract has none.', 'Inline ghost rows: accept, edit, reject.', 3)
    ] },
    { step: 4, name: 'Orchestrate', verb: 'Weave APIs into workflows', screens: [
      s('15', 'Workflows', '/workflows', '/workflows', '/workflows', 'Arazzo documents, their workflows and the services they depend on.', 'Loom thumbnail per workflow; unresolved sources flagged.', 6),
      s('16', 'Workflow', '/workflows/:doc/:id', '/workflows/:/:', undefined, 'Steps, actions, Mock URL, verification sessions, examples.', 'The Loom: one lane per source, data threads between steps.', 6),
      s('17', 'Workflow run', '/workflows/runs/:runId', '/workflows/runs/:', undefined, 'Run on the mocks, the implementations or a mix (proposed).', 'Success criteria + schema validation per step; missing mock examples named.', 6)
    ] },
    { step: 5, name: 'Test', verb: 'Prove conformance', screens: [
      s('08', 'Run a test', '/tests/create', '/tests/create', svc && `/tests/create?service=${svc}`, 'Endpoint, runner, timeout, secret, headers.', 'CI snippet (GitHub Actions, CLI) generated live.', 2),
      s('09', 'Live test', '/tests/runner/:id', '/tests/runner/:', ids.testId && `/tests/runner/${ids.testId}`, 'Execution and per-operation results.', 'Contract ⇄ actual response diff, line by line.', 2),
      s('10', 'History', '/tests/service/:id', '/tests/service/:', svc && `/tests/service/${svc}`, 'All runs for a service, trends.', 'Conformance timeline.', 2)
    ] },
    { step: 6, name: 'Observe', verb: 'Understand usage', screens: [
      s('11', 'Usage & score', '/metrics/:id', '/metrics/:', svc && `/metrics/${svc}`, 'Daily/hourly invocations, conformance score.', 'Calendar heatmap + treemap by domain.', 4),
      s('12', 'Live traces', '/traces', '/traces', '/traces', 'Real-time graph of mock calls.', 'Replay a call, jump to the example that answered.', 4)
    ] },
    { step: 7, name: 'Govern', verb: 'Share & administer', screens: [
      s('13', 'Hub', '/hub', '/hub', '/hub', 'Public API packages to install.', 'Badge preview + one-click install.', 5),
      s('14', 'Admin', '/admin', '/admin', '/admin', 'Users, groups, secrets, snapshots.', 'Roles × groups matrix at a glance.', 5)
    ] },
    { step: 8, name: 'Agents', verb: 'Mock & test A2A agents', screens: [
      s('19', 'Agent test', '/tests/agent/:id', '/tests/agent/:', ids.agentRunId ? `/tests/agent/${ids.agentRunId}` : '/tests/agent', 'Card, protocol and behaviour on each transport.', 'Conformance matrix + state-machine diff.', 7)
    ] }
  ];
  return phases
    .map((p) => ({ ...p, screens: p.screens.filter((sc) => existing.has(sc.match)) }))
    .filter((p) => p.screens.length > 0);
}
