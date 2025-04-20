import { browse, invokeRESTMocks, invokeGraphQLMocks, invokeSOAPMocks, invokeGRPCMocks, invokeREST_HelloAPIMocks } from './commons.js';
import { sleep } from 'k6';

// The default function runs all tests in sequence
export default function () {
    invokeRESTMocks();
    invokeGraphQLMocks();
    invokeSOAPMocks();
    browse();
    invokeGRPCMocks();
    invokeREST_HelloAPIMocks();
    sleep(2);
}
