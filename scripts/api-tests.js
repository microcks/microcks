import http from 'k6/http';
import { check, sleep, group } from 'k6';

// Define the base URL for Microcks (adjust as needed)
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

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
        let laurentCall = http.post(`${BASE_URL}/soap/HelloService+Mock/0.9`, laurentBody, { headers: laurentHeaders });
        check(laurentCall, {
            'laurentCall status is 500': (r) => r.status === 500,
            'laurentCall body contains a Fault element': (r) => r.body.includes("<soapenv:Fault>"),
        });
        sleep(1);
    });
}

// The default function runs all tests in sequence
export default function () {
    invokeRESTMocks();
    invokeGraphQLMocks();
    // invokeSOAPMocks();
    sleep(2); // pause between iterations
}
