import * as tests from './commons.js';
import { flavorConfig } from './flavor-config.js';
import { sleep } from 'k6';

const FLAVOR = __ENV.FLAVOR || 'regular-auth';

export default function () {
  const toRun = flavorConfig[FLAVOR];
  if (!toRun) {
    console.error(`Unknown flavor "${FLAVOR}"`);
    return;
  }
  for (const fn of toRun) {
    const testFn = tests[fn];
    if (typeof testFn !== 'function') {
      console.error(`Test function "${fn}" not found in commons.js`);
      continue;
    }
    console.log(`Running ${fn}`);
    testFn();
  }
}
