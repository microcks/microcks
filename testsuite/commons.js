import http from 'k6/http';
import grpc from 'k6/net/grpc';
import { check, sleep, group } from 'k6';

// Define the wait time of browse scenario
const WAIT_TIME = parseFloat(__ENV.WAIT_TIME) || 0.5;

// Define the base URL for Microcks (adjust as needed)
const HOST = __ENV.HOST || 'localhost';
const PORT = __ENV.PORT || '8080';
const BASE_URL = __ENV.BASE_URL || `http://${HOST}:${PORT}`;

const only500Callback = http.expectedStatuses(500);

const client = new grpc.Client();
client.load(['../samples/'], 'hello-v1.proto');

/* Simulate users browsing the API repository and getting details. */
export function browse() {
    const servicesRes = http.get(`${BASE_URL}/api/services`);
    check(servicesRes, {
        "status code should be 200": servicesRes => servicesRes.status === 200,
    });

    const services = servicesRes.json();
    sleep(WAIT_TIME);

    services.forEach(service => {
        const serviceViewRes = http.get(`${BASE_URL}/api/services/` + service.id + '?messages=true');
        sleep(WAIT_TIME);
        const serviceTestsRes = http.get(`${BASE_URL}/api/tests/service/` + service.id + '?page=0&size=20');
        sleep(WAIT_TIME);
    });
}

// Function to test REST API endpoints
export function invokeRESTMocks() {
    group('REST API Tests', function () {
        // Test fetching all pastries
        let pastryCall = http.get(`${BASE_URL}/rest/API+Pastry+-+2.0/2.0.0/pastry`);
        check(pastryCall, {
            'pastryCall status is 200': (r) => r.status === 200,
        });

        // Test fetching a specific pastry in JSON
        let eclairCall = http.get(`${BASE_URL}/rest/API+Pastry+-+2.0/2.0.0/pastry/Eclair%20Cafe`);
        check(eclairCall, {
            'eclairCall status is 200': (r) => r.status === 200,
        });

        // Test fetching the same pastry in XML
        let eclairXmlCall = http.get(`${BASE_URL}/rest/API+Pastry+-+2.0/2.0.0/pastry/Eclair%20Cafe`, { headers: {'Accept': 'text/xml'} });
        check(eclairXmlCall, {
            'eclairXmlCall status is 200': (r) => r.status === 200,
            'eclairXmlCall response is XML': (r) => r.body.includes("<pastry>"),
        });

        // Test fetching another pastry
        let millefeuilleCall = http.get(`${BASE_URL}/rest/API+Pastry+-+2.0/2.0.0/pastry/Millefeuille`);
        check(millefeuilleCall, {
            'millefeuilleCall status is 200': (r) => r.status === 200,
        });
    });
}

// Function to test GraphQL endpoints
export function invokeGraphQLMocks() {
    group('GraphQL API Tests', function () {
        const jsonHeaders = { 'Content-Type': 'application/json' };

        // Test a query to fetch all films
        const allFilmsQuery = `query allFilms {
            allFilms {
                films {
                    id
                    title
                }
            }
        }`;
        const allFilmsBody = { query: allFilmsQuery };
        let allFilmsCall = http.post(`${BASE_URL}/graphql/Movie+Graph+API/1.0`, JSON.stringify(allFilmsBody), { headers: jsonHeaders });
        check(allFilmsCall, {
            'allFilmsCall status is 200': (r) => r.status === 200,
        });

        // Test a query to fetch a specific film
        const aFilmQuery = `query film($id: String) {
            film(id: "ZmlsbXM6MQ==") {
                id
                title
                episodeId
            }
        }`;
        const aFilmBody = { query: aFilmQuery };
        let aFilmCall = http.post(`${BASE_URL}/graphql/Movie+Graph+API/1.0`, JSON.stringify(aFilmBody), { headers: jsonHeaders });
        check(aFilmCall, {
            'aFilmCall status is 200': (r) => r.status === 200,
        });

        // Test a query using fragments
        const aFilmFragmentsQuery = `query film($id: String) {
            film(id: "ZmlsbXM6MQ==") {
                ...filmFields
            }
        }
        fragment filmFields on Film {
            id
            title
            episodeId
            starCount
        }`;
        const aFilmFragmentBody = { query: aFilmFragmentsQuery };
        let aFilmFragmentCall = http.post(`${BASE_URL}/graphql/Movie+Graph+API/1.0`, JSON.stringify(aFilmFragmentBody), { headers: jsonHeaders });
        check(aFilmFragmentCall, {
            'aFilmFragmentCall status is 200': (r) => r.status === 200,
        });
    });
}

// Function to test SOAP endpoints
export function invokeSOAPMocks() {
    group('SOAP API Tests', function () {
        // Define a SOAP envelope for "Andrew"
        const andrewBody = `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soapenv:Header/>
            <soapenv:Body>
                <hel:sayHello>
                    <name>Andrew</name>
                </hel:sayHello>
            </soapenv:Body>
        </soapenv:Envelope>`;
        // Set appropriate headers for SOAP 1.1
        const andrewHeaders = {
            'Content-Type': 'text/xml; charset=utf-8',
            'SOAPAction': 'sayHello'
        };
        let andrewCall = http.post(`${BASE_URL}/soap/HelloService+Mock/0.9`, andrewBody, { headers: andrewHeaders });
        check(andrewCall, {
            'andrewCall status is 200': (r) => r.status === 200,
        });
        sleep(1);

        // Define a SOAP envelope for "Karla" with SOAP 1.2 headers
        const karlaBody = `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soapenv:Header/>
            <soapenv:Body>
                <hel:sayHello>
                    <name>Karla</name>
                </hel:sayHello>
            </soapenv:Body>
        </soapenv:Envelope>`;
        const karlaHeaders = {
            'Content-Type': 'text/xml; charset=utf-8; action=sayHello'
        };
        let karlaCall = http.post(`${BASE_URL}/soap/HelloService+Mock/0.9`, karlaBody, { headers: karlaHeaders });
        check(karlaCall, {
            'karlaCall status is 200': (r) => r.status === 200,
            'karlaCall body contains expected fault or message': (r) => r.body.includes("Hello Karla") || r.body.includes("Fault"),
        });
        sleep(1);

        // Define a SOAP envelope for "Laurent" expecting a fault (500)
        const laurentHeaders = {
            'Content-Type': 'application/soap+xml; charset=utf-8; action=sayHello'
        };
        const laurentBody = `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
            <soapenv:Header/>
            <soapenv:Body>
                <hel:sayHello>
                    <name>Laurent</name>
                </hel:sayHello>
            </soapenv:Body>
        </soapenv:Envelope>`;
        let laurentCall = http.post(`${BASE_URL}/soap/HelloService+Mock/0.9`, laurentBody, { headers: laurentHeaders, responseCallback: only500Callback })
        check(laurentCall, {
            'laurentCall status is 500': (r) => r.status === 500,
            'laurentCall body contains a Fault element': (r) => r.body.includes("<soapenv:Fault>"),
        });
        sleep(1);
    });
}

// Function to test GRPC endpoints
export function invokeGRPCMocks() {
    client.connect(`${HOST}:${PORT}`, { plaintext: true });

    const payloads = [
        { firstname: 'Laurent', lastname: 'Broudoux' },
        { firstname: 'John', lastname: 'Doe' },
    ];

    payloads.forEach((payload) => {
        const response = client.invoke(
            'io.github.microcks.grpc.hello.v1.HelloService/greeting',
            payload
        );

        check(response, {
            'status is OK': (r) => r && r.status === grpc.StatusOK,
            'response contains greeting': (r) =>
                r && r.message && r.message.greeting.includes(payload.firstname),
        });
    });

    client.close();
    sleep(1);
};

// Function to test REST API endpoints for HelloAPIMock
export function invokeREST_HelloAPIMocks() {
  group('Hello API REST Mocks', () => {
    const MOCK_NAME = 'Hello%20API%20Mock';
    const VERSION   = '0.8';
    const RESOURCE  = 'v1/hello';

    const TEST_CASES = [
      { name: 'David', expStatus: 200, expGreeting: 'Hello David !' },
      { name: 'Gavin', expStatus: 200, expGreeting: 'Hello Gavin !' },
      { name: 'Nobody', expStatus: 400, expGreeting: null          },
    ];
    TEST_CASES.forEach(({ name, expStatus, expGreeting }) => {
      const url = `${BASE_URL}/rest/${MOCK_NAME}/${VERSION}/${RESOURCE}?name=${encodeURIComponent(name)}`;
      const res = http.get(url);

      // Status code
      check(res, {
        [`${name}Call status is ${expStatus}`]: (r) => r.status === expStatus,
      });

      // Header
      if (expStatus === 200) {
        check(res, {
          [`${name}Call response is JSON`]: (r) =>
            r.headers['Content-Type'] &&
            r.headers['Content-Type'].includes('application/json'),
        });
      }

      // Body assertion by substring
      if (expGreeting) {
        check(res, {
          [`${name}Call body contains "${expGreeting}"`]: (r) =>
            r.body.includes(expGreeting),
        });
      }
      sleep(1);
    });

    const url = `${BASE_URL}/rest/${MOCK_NAME}/${VERSION}/`;
    const res = http.get(url);
    check(res, {
      [`Empty body`]: (r) =>
        !r.body || r.body.trim().length === 0,
    });
  });
}

export function invokeREST_PetStoreAPI() {
  group('Petstore API', () => {
    const userKeys = {
        1: '998bac0775b1d5f588e0a6ca7c11b852',
        2: '70f735676ec46351c6699c4bb767878a',
    };

    // Test for petId = 1 (expected 404)
    const petRes1 = http.get(`${BASE_URL}/rest/Petstore+API/1.0/v2/pet/1?user_key=${userKeys[1]}`);
    check(petRes1, {
        'GET /v2/pet/1 - status is 404': (r) => r.status === 404,
    });
    sleep(1);

    // Test for petId = 2 (expected 200 + content validation)
    const petRes2 = http.get(`${BASE_URL}/rest/Petstore+API/1.0/v2/pet/2?user_key=${userKeys[2]}`);
    check(petRes2, {
        'GET /v2/pet/2 - status is 200': (r) => r.status === 200,
        'GET /v2/pet/2 - has name "cat"': (r) => r.json().name === 'cat',
    });
    sleep(1);

    // Test GET /v2/pet/findByStatus for status=available
    const status = 'available';
    const response = http.get(`${BASE_URL}/rest/Petstore+API/1.0/v2/pet/findByStatus?status=${status}&user_key=70f735676ec46351c6699c4bb767878a`);
    check(response, {
        'status is 200': (r) => r.status === 200,
        'response is non-empty array': (r) => Array.isArray(r.json()) && r.json().length > 0,
        'first pet has id and name': (r) => {
            const data = r.json();
            return data.length > 0 && data[0].id !== undefined && data[0].name !== undefined;
        },
    });
    sleep(1);
  });
}

export function authenticate() {
  const url = 'http://localhost:18080/realms/microcks/protocol/openid-connect/token';
  const authHeader = 'Basic bWljcm9ja3Mtc2VydmljZWFjY291bnQ6YWI1NGQzMjktZTQzNS00MWFlLWE5MDAtZWM2YjNmZTE1YzU0Cg=';

  const headers = {
      'Content-Type': 'application/x-www-form-urlencoded',
      'Authorization': authHeader,
  };

  const payload = {
      grant_type: 'client_credentials',
  };

  const response = http.post(url, payload, { headers: headers });

  check(response, {
      'authentication successful': (r) => r.status === 200,
      'access token is present': (r) => r.json('access_token') !== '',
  });

  return response.json('access_token');
}

const TESTS = [
  { path: '/api/features/config', expect: 200 },
  { path: '/api/keycloak/config', expect: 200 },
  { path: '/api/services', expect: 401 },
  { path: '/api/jobs',     expect: 401 },
];

export function ownAPIsNoAuth () {
  group("Microcks' own APIs without authentication", () => {
    const responses = http.batch(
      TESTS.map((t) => ['GET', BASE_URL + t.path])
    );

    TESTS.forEach((t, i) => {
      check(responses[i], {
        [`GET ${t.path} returns 200`]: (r) => r.status === 200,
      });
    });
  });
}

export function ownAPIsAuth () {
  group("Microcks' own APIs with authentication", () => {
    const responses = http.batch(
      TESTS.map((t) => ['GET', BASE_URL + t.path])
    );

    TESTS.forEach((t, i) => {
      check(responses[i], {
        [`GET ${t.path} returns ${t.expect}`]: (r) => r.status === t.expect,
      });
    });

    const token = authenticate();
    const authHeaders = { headers: { Authorization: `Bearer ${token}` } };

    const auth_responses = http.batch(
      TESTS.map((t) => ['GET', `${BASE_URL}${t.path}`, null, authHeaders])
    );

    TESTS.forEach((t, i) => {
      check(auth_responses[i], {
        [`GET ${t.path} auth returns 200`]: (r) => r.status === 200,
      });
    });
  });
}
