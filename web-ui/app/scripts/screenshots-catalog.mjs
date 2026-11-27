// @ts-nocheck
// Screenshots of the Catalog screens (dark and light) from the dev server of the catalog domain.
// Usage: BASE_URL=http://127.0.0.1:5201 node scripts/screenshots-testing.mjs [outDir]
import { existsSync, mkdirSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5201';
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

const PASTRY = 'api-pastry-2-0-2-0-0';
const shots = [
  { name: 'cockpit', path: '/' },
  { name: 'service-operations', path: `/services/${PASTRY}` },
  { name: 'service-properties', path: `/services/${PASTRY}/properties` },
  { name: 'service-tests', path: `/services/${PASTRY}/tests` },
  { name: 'behavior', path: `/services/${PASTRY}/operations/get-pastry-name` },
  { name: 'behavior-script', path: `/services/${PASTRY}/operations/get-pastry-name`, before: async (p) => { await p.getByText('SCRIPT', { exact: true }).first().click(); } },
  { name: 'copilot', path: `/services/${PASTRY}/copilot?op=get-pastry-name` },
  {
    name: 'copilot-review',
    path: `/services/${PASTRY}/copilot?op=get-pastry-name`,
    before: async (p) => {
      await p.getByRole('button', { name: /^Generate/ }).click();
      await p.waitForSelector('.proposal', { timeout: 6000 });
      await p.getByRole('button', { name: /^Accept / }).first().click();
    }
  },
  { name: 'service-notfound', path: '/services/nope' }
];

const only = process.env.ONLY?.split(',');
const browser = await chromium.launch({ executablePath: process.env.CHROMIUM ?? cachedChromium() });
const problems = [];
for (const theme of ['dark', 'light']) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript((t) => localStorage.setItem('microcks-theme', t), theme);
  const page = await ctx.newPage();
  page.on('console', (m) => m.type() === 'error' && problems.push(`${theme} console: ${m.text()}`));
  page.on('pageerror', (e) => problems.push(`${theme} pageerror: ${e.message}`));
  for (const s of shots.filter((x) => !only || only.some((o) => x.name.startsWith(o)))) {
    await page.goto(base + s.path);
    await page.waitForSelector('h1');
    await s.before?.(page);
    await page.waitForTimeout(600);
    await page.screenshot({ path: join(out, `${s.name}-${theme}.png`), fullPage: true });
  }
  await ctx.close();
}
await browser.close();
console.log(problems.length ? problems.join('\n') : 'no console or page errors');
