// Screenshots of the runs domain (agent test, workflow run) in dark and light, with console/page error checks.
// Usage: BASE_URL=http://127.0.0.1:5203 node scripts/screenshots-runs.mjs [outDir]
import { existsSync, mkdirSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5203';
const out = process.argv[2] ?? '../../screenshots';
mkdirSync(out, { recursive: true });

function cachedChromium() {
  const root = join(homedir(), '.cache', 'ms-playwright');
  if (!existsSync(root)) return undefined;
  for (const dir of readdirSync(root).filter((d) => d.startsWith('chromium-'))) {
    for (const sub of ['chrome-linux', 'chrome-linux64']) {
      const exe = join(root, dir, sub, 'chrome');
      if (existsSync(exe)) return exe;
    }
  }
  return undefined;
}

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM ?? cachedChromium() });
const only = process.env.ONLY;

/** @type {{name: string, path: string, width?: number, before?: (p: import('playwright').Page) => Promise<unknown>}[]} */
const shots = [
  { name: 'agent-setup', path: '/tests/agent' },
  {
    name: 'agent-running',
    path: '/tests/agent',
    before: async (p) => {
      await p.getByRole('button', { name: 'Run test' }).click();
      await p.getByRole('button', { name: 'Step forward' }).click();
      await p.getByRole('button', { name: 'Step forward' }).click();
      await p.getByRole('button', { name: 'Step forward' }).click();
    }
  },
  { name: 'agent-done', path: '/tests/agent/3' },
  {
    name: 'agent-done-detail',
    path: '/tests/agent/3',
    before: async (p) => {
      await p.locator('button.failure-link').nth(1).click();
    }
  },
  { name: 'agent-notfound', path: '/tests/agent/999' },
  { name: 'agent-done-narrow', path: '/tests/agent/3', width: 480 },
  { name: 'agent-setup-narrow', path: '/tests/agent', width: 480 },
  { name: 'wfrun-setup', path: '/workflows/pastry/restock-pastry/run' },
  { name: 'wfrun-setup-mocks', path: '/workflows/pastry/restock-pastry/run?scenario=mocksEnd' },
  {
    name: 'wfrun-setup-mixed',
    path: '/workflows/pastry/compare-prices/run',
    before: async (p) => {
      await p.getByRole('radio', { name: /Mixed/ }).check();
    }
  },
  {
    name: 'wfrun-setup-errors',
    path: '/workflows/pastry/restock-pastry/run',
    before: async (p) => {
      await p.getByLabel(/Base URL/).first().fill('not a url');
      await p.getByRole('button', { name: 'Run', exact: true }).click();
    }
  },
  { name: 'wfrun-setup-notfound', path: '/workflows/pastry/nope/run' },
  { name: 'wfrun-run14', path: '/workflows/runs/6729a1d27e4c0b5f93a8c214' },
  { name: 'wfrun-run15', path: '/workflows/runs/6729a3f08b1d6e2c45f7a930' },
  { name: 'wfrun-run16', path: '/workflows/runs/6729a4b15c9e7d3a28b0f6e1' },
  { name: 'wfrun-live', path: '/workflows/runs/6729a1d27e4c0b5f93a8c214?live', before: (p) => p.waitForTimeout(2500) },
  { name: 'wfrun-notfound', path: '/workflows/runs/zzz' },
  { name: 'wfrun-setup-narrow', path: '/workflows/pastry/restock-pastry/run', width: 480 },
  { name: 'wfrun-run14-narrow', path: '/workflows/runs/6729a1d27e4c0b5f93a8c214', width: 480 },
  ...(process.env.EXTRA_SHOTS ? (await import(process.env.EXTRA_SHOTS)).default : [])
];

let failures = 0;
for (const theme of ['dark', 'light']) {
  for (const shot of shots) {
    if (only && !shot.name.includes(only)) continue;
    const context = await browser.newContext({ viewport: { width: shot.width ?? 1440, height: 900 } });
    await context.addInitScript((t) => localStorage.setItem('microcks-theme', t), theme);
    const page = await context.newPage();
    const problems = [];
    page.on('console', (m) => m.type() === 'error' && problems.push(`console: ${m.text()}`));
    page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
    await page.goto(base + shot.path, { waitUntil: 'networkidle' });
    await page.locator('#page-title').waitFor();
    if (shot.before) await shot.before(page);
    await page.waitForTimeout(300);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    if (overflow > 0) problems.push(`horizontal overflow ${overflow}px`);
    await page.screenshot({ path: join(out, `${shot.name}-${theme}.png`), fullPage: true });
    console.log(`${shot.name}-${theme}`, problems.length ? problems : 'ok');
    failures += problems.length;
    await context.close();
  }
}
await browser.close();
process.exit(failures ? 1 : 0);
