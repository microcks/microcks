// @ts-nocheck
// Screenshots of the Testing screens (dark and light) from the dev server of the testing domain.
// Usage: BASE_URL=http://127.0.0.1:5204 node scripts/screenshots-testing.mjs [outDir]
import { existsSync, mkdirSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5204';
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
  { name: 'tests-history', path: `/tests/service/${PASTRY}` },
  { name: 'tests-history-empty', path: '/tests/service/api-pastries-0-0-2' },
  { name: 'test-run-failed', path: `/tests/runner/demo-${PASTRY}-119` },
  {
    name: 'test-run-failed-open',
    path: `/tests/runner/demo-${PASTRY}-119`,
    before: async (p) => {
      await p.getByRole('button', { name: /Add to CI/ }).click();
    }
  },
  { name: 'test-run-passed', path: `/tests/runner/demo-${PASTRY}-118` },
  { name: 'test-run-live', path: '/tests/create?service=' + PASTRY, launch: true },
  { name: 'usage-all', path: '/metrics' },
  { name: 'usage-service', path: `/metrics/${PASTRY}` }
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
    if (s.launch) {
      await page.getByLabel('Test endpoint').fill('https://pastry.staging.example.com/v2');
      await page.getByRole('button', { name: 'Launch test' }).click();
      await page.waitForURL(/\/tests\/runner\//);
    }
    await page.waitForSelector('#page-title');
    await s.before?.(page);
    await page.waitForTimeout(600);
    await page.screenshot({ path: join(out, `${s.name}-${theme}.png`), fullPage: true });
  }
  await ctx.close();
}
await browser.close();
console.log(problems.length ? problems.join('\n') : 'no console or page errors');
