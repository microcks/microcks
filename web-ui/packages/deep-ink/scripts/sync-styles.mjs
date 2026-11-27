#!/usr/bin/env node
// Copies the built Deep Ink stylesheets from a microcks-deep-ink checkout into this package.
// Usage: node scripts/sync-styles.mjs /path/to/microcks-deep-ink   (run `pnpm build` there first)
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const source = resolve(process.argv[2] ?? '../../../../microcks-deep-ink');
const pkg = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const out = join(pkg, 'src', 'styles');
mkdirSync(out, { recursive: true });

const files = [
  [join(source, 'packages/registry/dist/deep-ink.no-fonts.css'), 'deep-ink.css'],
  [join(source, 'packages/tokens/tokens.json'), 'tokens.json'],
  [join(source, 'apps/demo-react/src/ui/app/microcks-logo.svg'), '../assets/microcks-logo.svg']
];
for (const [from, to] of files) {
  if (!existsSync(from)) {
    console.error(`missing ${from}: build microcks-deep-ink first`);
    process.exit(1);
  }
  mkdirSync(dirname(join(out, to)), { recursive: true });
  copyFileSync(from, join(out, to));
}

const commit = process.env.DEEP_INK_COMMIT ?? 'unknown';
const css = join(out, 'deep-ink.css');
writeFileSync(css, `/* Synced from microcks-deep-ink (commit ${commit}) by scripts/sync-styles.mjs. Do not edit. */\n${readFileSync(css, 'utf8')}`);
console.log(`synced ${files.length} files into ${out}`);
