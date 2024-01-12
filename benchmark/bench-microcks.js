import http from 'k6/http';
import { check, sleep } from 'k6';

// Define the base URL of your API
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Define the options for your test
export let options = {
    stages: [
        { duration: '1m', target: 50 }, // Ramp up to 50 virtual users over 1 minute
        { duration: '3m', target: 50 }, // Stay at 50 virtual users for 3 minutes
        { duration: '30s', target: 0 }   // Ramp down to 0 virtual users over 1 minute
    ],
};

// Define the main function that represents your test scenario
export default function () {

    /* ###################### */
    /* ### Step 01        ### */
    /* ###################### */
    let step01 = http.get(`${BASE_URL}/rest/Openbanking.co.uk+Account+and+Transaction+API+v3.1.0/3.1.0/open-banking/v3.1/aisp/account-access-consents/urn-alphabank-intent-88380`);
    check(step01, {
        'Step 01 Status is 200': (r) => r.status === 200,
    });

    sleep(1);

    /* ###################### */
    /* ### Step 02        ### */
    /* ###################### */
    const step02Body = {
        "Data": {
            "Permissions": [
                "ReadAccountsBasic",
                "ReadBalances"
            ],
            "ExpirationDateTime": "2017-05-02T00:00:00+00:00",
            "TransactionFromDateTime": "2017-05-03T00:00:00+00:00",
            "TransactionToDateTime": "2017-12-03T00:00:00+00:00"
        },
        "Risk": {}
    };

    const step02Headers = {
        'Content-Type': 'application/json',
    };
    let step02Response = http.post(`${BASE_URL}/rest/Openbanking.co.uk+Account+and+Transaction+API+v3.1.0/3.1.0/open-banking/v3.1/aisp/account-access-consents`, step02Body, { headers: step02Headers });

    check(step02Response, {
        'Step 02 Status is 201': (r) => r.status === 201,
    });

    sleep(1);

    /* ###################### */
    /* ### Step 03        ### */
    /* ###################### */
    let step03 = http.get(`${BASE_URL}/rest/Petstore+API/1.0/v2/pet/findByStatus?status=available&user_key=70f735676ec46351c6699c4bb767878a`);
    check(step03, {
        'Step 03 Status is 200': (r) => r.status === 200,
    });

}