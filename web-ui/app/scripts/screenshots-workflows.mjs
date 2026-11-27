// @ts-nocheck
// Screenshots of the Workflows screens (dark and light) from the dev server of the workflows domain.
// Usage: BASE_URL=http://127.0.0.1:5202 node scripts/screenshots-workflows.mjs [outDir]
import { existsSync, mkdirSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5202';
const out = process.argv[2] ?? '../../screenshots';
mkdirSync(out, { recursive: true });

function cachedChromium() {
  const root = join(homedir(), '.cache', 'ms-playwright');
  if (!existsSync(root)) return undefined;
  for (const dir of readdirSync(root).filter((d) => d.startsWith('chromium-'))) {
    for (const sub of ['chrome-linux64', 'chrome-linux']) {
      const exe = join(root, dir, sub, 'chrome');
      if (existsSync(exe)) return exe;
    }
  }
  return undefined;
}

const W = '/workflows/pastry/restock-pastry';
const shots = [
  { name: 'workflows-list', path: '/workflows' },
  { name: 'workflows-filtered', path: '/workflows', before: async (p) => { await p.getByRole('button', { name: /failed/i }).first().click().catch(() => {}); } },
  { name: 'workflows-link-dialog', path: '/workflows', before: async (p) => { await p.getByRole('button', { name: /^Link/ }).first().click(); } },
  { name: 'workflow-loom', path: W },
  { name: 'workflow-loom-failed', path: `${W}/loom?run=14&step=update-price` },
  { name: 'workflow-steps', path: `${W}/steps?run=14` },
  { name: 'workflow-source', path: `${W}/source` },
  { name: 'workflow-mock-url', path: `${W}/mock-url` },
  { name: 'workflow-sessions', path: `${W}/sessions` },
  { name: 'workflow-examples', path: `${W}/examples` },
  { name: 'workflow-runs', path: `${W}/runs` },
  { name: 'workflow-not-runnable', path: '/workflows/pastry/order-pastry' },
  { name: 'workflow-notfound', path: '/workflows/pastry/nope' }
];

const only = process.env.ONLY?.split(',');
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM ?? cachedChromium() });
const problems = [];
for (const theme of ['dark', 'light']) {
  for (const width of [1440, 480]) {
    const ctx = await browser.newContext({ viewport: { width, height: 900 } });
    await ctx.addInitScript((t) => localStorage.setItem('microcks-theme', t), theme);
    const page = await ctx.newPage();
    page.on('console', (m) => m.type() === 'error' && !/40[0-9]/.test(m.text()) && problems.push(`${theme}/${width} console: ${m.text()}`));
    page.on('pageerror', (e) => problems.push(`${theme}/${width} pageerror: ${e.message}`));
    for (const s of shots.filter((x) => (width === 1440 || x.name === 'workflow-loom' || x.name === 'workflows-list') && (!only || only.some((o) => x.name.startsWith(o))))) {
      await page.goto(base + s.path);
      await page.waitForSelector('h1');
      await s.before?.(page);
      await page.waitForTimeout(600);
      await page.screenshot({ path: join(out, `${s.name}-${theme}${width === 480 ? '-480' : ''}.png`), fullPage: true });
    }
    await ctx.close();
  }
}
await browser.close();
console.log(problems.length ? problems.join('\n') : 'no console or page errors');
