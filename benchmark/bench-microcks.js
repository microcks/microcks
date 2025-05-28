import { browse, invokeRESTMocks, invokeGraphQLMocks, invokeSOAPMocks } from '../testsuite/commons.js';

// Define the options for your test
export let options = {
    /*
    stages: [
        { duration: '1m', target: 50 }, // Ramp up to 50 virtual users over 1 minute
        { duration: '3m', target: 50 }, // Stay at 50 virtual users for 3 minutes
        { duration: '30s', target: 0 }   // Ramp down to 0 virtual users over 30 seconds
    ],
    */
    scenarios: {
      browse: {
        executor: 'constant-vus',
        exec: 'browse',
        vus: 20,
        duration: '1m',
      },
      invokeRESTMocks: {
        executor: 'per-vu-iterations',
        exec: 'invokeRESTMocks',
        vus: 40,
        iterations: 200,
        startTime: '5s',
        maxDuration: '2m',
      },
      invokeGraphQLMocks: {
        executor: 'per-vu-iterations',
        exec: 'invokeGraphQLMocks',
        vus: 20,
        iterations: 100,
        startTime: '10s',
        maxDuration: '2m',
      },
      invokeSOAPMocks: {
        executor: 'per-vu-iterations',
        exec: 'invokeSOAPMocks',
        vus: 5,
        iterations: 5,
        startTime: '15s',
        maxDuration: '2m',
      }
    }
};

// The default function runs all tests in sequence
export default function () {
    invokeRESTMocks();
    invokeGraphQLMocks();
    invokeSOAPMocks();
    browse()
    sleep(2); // pause between iterations
}
export { browse, invokeRESTMocks, invokeGraphQLMocks, invokeSOAPMocks };
