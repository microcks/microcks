// Screenshots of the observe pages (traces, hub, map, not-found), dark and light, plus console-error checks.
// Usage: BASE_URL=http://127.0.0.1:5205 node scripts/screenshots-observe.mjs [outDir]
import { existsSync, mkdirSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5205';
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

/** @type {{name: string, path: string | ((theme: string) => string), width?: number, before?: (p: import('playwright').Page) => Promise<unknown>}[]} */
const shots = [
  { name: 'traces-default', path: '/traces' },
  {
    name: 'traces-paused-filtered',
    path: '/traces',
    before: async (p) => {
      await p.getByRole('button', { name: 'Pause' }).click();
      await p.getByLabel('Status').selectOption('error');
      await p.waitForTimeout(400);
    }
  },
  { name: 'traces-narrow', path: '/traces', width: 480 },
  { name: 'hub-default', path: '/hub' },
  { name: 'hub-selected', path: '/hub/kafka-events' },
  {
    name: 'hub-installed',
    path: (theme) => (theme === 'dark' ? '/hub/telco' : '/hub/weather'),
    before: async (p) => {
      // The BFF keeps installs in memory: a package installed by a previous run is already in its final state.
      const btn = p.getByRole('button', { name: /^Install and keep in sync/ });
      if (await btn.count()) await btn.click();
      await p.getByText('Installed', { exact: true }).first().waitFor({ timeout: 6000 });
    }
  },
  { name: 'hub-narrow', path: '/hub/payments', width: 480 },
  { name: 'map', path: '/map' },
  { name: 'not-found', path: '/nope/nothing' }
];

let errors = 0;
for (const theme of ['dark', 'light']) {
  for (const s of shots) {
    const ctx = await browser.newContext({ viewport: { width: s.width ?? 1440, height: 900 } });
    await ctx.addInitScript((t) => localStorage.setItem('microcks-theme', t), theme);
    const page = await ctx.newPage();
    /** @type {string[]} */
    const problems = [];
    page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`));
    page.on('console', (m) => m.type() === 'error' && problems.push(`console: ${m.text()}`));
    await page.goto(base + (typeof s.path === 'function' ? s.path(theme) : s.path), { waitUntil: 'load' });
    await page.waitForTimeout(1200);
    await s.before?.(page);
    await page.screenshot({ path: join(out, `${s.name}-${theme}.png`), fullPage: true });
    if (problems.length) {
      errors += problems.length;
      console.log(`${s.name}-${theme}:`, problems.join(' | '));
    }
    await ctx.close();
  }
}
await browser.close();
console.log(errors ? `${errors} problem(s)` : 'no console errors');
process.exitCode = errors ? 1 : 0;
