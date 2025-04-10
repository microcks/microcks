import { browse, invokeRESTMocks, invokeGraphQLMocks, invokeSOAPMocks } from './commons.js';
import { sleep } from 'k6';

// The default function runs all tests in sequence
export default function () {
    invokeRESTMocks();
    invokeGraphQLMocks();
    invokeSOAPMocks();
    browse()
    sleep(2);
}
