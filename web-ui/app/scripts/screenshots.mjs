// Takes the iteration-1 screenshots (dark and light) from a running dev or preview server.
// Usage: BASE_URL=http://127.0.0.1:5173 node scripts/screenshots.mjs [outDir]
import { existsSync, mkdirSync, readdirSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { chromium } from 'playwright';

const base = process.env.BASE_URL ?? 'http://127.0.0.1:5173';
const out = process.argv[2] ?? 'screenshots';
mkdirSync(out, { recursive: true });

// Reuse any Chromium already in the Playwright cache when the pinned revision is not installed.
function cachedChromium() {
  const root = join(homedir(), '.cache', 'ms-playwright');
  if (!existsSync(root)) return undefined;
  for (const dir of readdirSync(root).filter((d) => d.startsWith('chromium-'))) {
    const exe = join(root, dir, 'chrome-linux', 'chrome');
    if (existsSync(exe)) return exe;
    const exe64 = join(root, dir, 'chrome-linux64', 'chrome');
    if (existsSync(exe64)) return exe64;
  }
  return undefined;
}

const browser = await chromium.launch({ executablePath: process.env.CHROMIUM ?? cachedChromium() });

/** @type {{name: string, path: string, before?: (p: import('playwright').Page) => Promise<unknown>}[]} */
const shots = [
  { name: 'services', path: '/services' },
  { name: 'services-wall', path: '/services', before: async (p) => p.getByRole('button', { name: 'Badge wall' }).click() },
  { name: 'sources', path: '/sources' },
  { name: 'sources-new', path: '/sources/new' },
  {
    name: 'sources-new-preview',
    path: '/sources/new',
    before: async (p) => {
      await p.getByRole('button', { name: /Swagger Petstore/ }).click();
      await p.getByRole('heading', { name: /Preview before creating/ }).waitFor();
    }
  },
  { name: 'tests-create', path: '/tests/create?service=api-pastry-2-0-2-0-0' },
  {
    name: 'tests-create-filled',
    path: '/tests/create?service=api-pastry-2-0-2-0-0',
    before: async (p) => {
      await p.getByLabel('Test endpoint').fill('https://pastry.staging.example.com/v2');
      await p.getByRole('checkbox').first().uncheck();
      await p.getByRole('button', { name: '+ x-api-key (globals)' }).click();
      await p.getByLabel('Header 1 values').fill('staging-key');
    }
  },
  { name: 'tests-create-event', path: '/tests/create?service=user-signed-up-api-0-1-1' },
  { name: 'admin-users', path: '/admin/users' },
  {
    name: 'admin-groups',
    path: '/admin/groups',
    before: async (p) => {
      await p.locator('.ad-cell[data-state=empty]').first().click();
    }
  },
  {
    name: 'admin-secrets',
    path: '/admin/secrets',
    before: async (p) => {
      await p.getByRole('button', { name: 'Edit secret git-readonly' }).click();
    }
  },
  { name: 'admin-secrets-new', path: '/admin/secrets', before: async (p) => p.getByRole('button', { name: 'New secret' }).click() },
  {
    name: 'admin-snapshots',
    path: '/admin/snapshots',
    before: async (p) => p.getByRole('button', { name: 'Use a sample snapshot' }).click()
  },
  { name: 'admin-configuration', path: '/admin/configuration' }
];

// Secrets are real state: create a few through the API so the tab is populated.
for (const body of [
  { name: 'git-readonly', kind: 'basic', description: 'Read-only access to the contracts repository', user: 'ci-bot', secret: 'pa55word' },
  { name: 'staging-token', kind: 'token', description: 'Bearer token for the staging endpoints', user: 'X-API-Key', secret: 'tok-123' },
  { name: 'broker-oauth', kind: 'oauth2', description: 'Kafka broker client credentials', user: 'microcks', secret: 'shh', tokenUrl: 'https://idp.example.com/token', scopes: 'kafka' }
]) {
  await fetch(base + '/api/admin/secrets', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ tokenUrl: '', scopes: '', ...body }) });
}

for (const theme of ['dark', 'light']) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
  await ctx.addInitScript((t) => localStorage.setItem('microcks-theme', t), theme);
  const page = await ctx.newPage();
  for (const s of shots) {
    await page.goto(base + s.path, { waitUntil: 'networkidle' });
    await page.evaluate(() => document.fonts.ready);
    if (s.before) await s.before(page);
    await page.waitForTimeout(250);
    await page.screenshot({ path: join(out, `${s.name}-${theme}.png`), fullPage: true });
    console.log('saved', `${s.name}-${theme}.png`);
  }
  await ctx.close();
}
await browser.close();
