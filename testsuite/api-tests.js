import { browse, invokeRESTMocks, invokeGraphQLMocks, invokeSOAPMocks, invokeGRPCMocks } from './commons.js';
import { sleep } from 'k6';

// The default function runs all tests in sequence
export default function () {
    invokeRESTMocks();
    invokeGraphQLMocks();
    invokeSOAPMocks();
    browse();
    invokeGRPCMocks();
    sleep(2);
}
