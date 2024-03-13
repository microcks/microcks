import http from 'k6/http';
import { check, sleep } from 'k6';

// Define the wait time of browse scenario
const WAIT_TIME = parseFloat(__ENV.WAIT_TIME) || 0.5;

// Define the base URL of your API
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const only500Callback = http.expectedStatuses(500);

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

/* Simulate applications issuing REST mock invocations . */
export function invokeRESTMocks() {
    let pastryCall = http.get(`${BASE_URL}/rest/API+Pastry+-+2.0/2.0.0/pastry`);
    check(pastryCall, {
        'pastryCall status is 200': (r) => r.status === 200,
    });


    let eclairCall = http.get(`${BASE_URL}/rest/API+Pastry+-+2.0/2.0.0/pastry/Eclair%20Cafe`);
    check(eclairCall, {
        'eclairCall status is 200': (r) => r.status === 200,
    });

    let eclairXmlCall = http.get(`${BASE_URL}/rest/API+Pastry+-+2.0/2.0.0/pastry/Eclair%20Cafe`, { headers: {'Accept': 'text/xml'} });
    check(eclairXmlCall, {
        'eclairXmlCall status is 200': (r) => r.status === 200,
        'eclairXmlCall response is Xml': (r) => r.body.includes("<pastry>")
    });

    let millefeuilleCall = http.get(`${BASE_URL}/rest/API+Pastry+-+2.0/2.0.0/pastry/Millefeuille`);
    check(millefeuilleCall, {
        'millefeuilleCall status is 200': (r) => r.status === 200,
    });
}

/* Simulate applications issuing GraphQL mock invocations . */
export function invokeGraphQLMocks() {
    const jsonHeaders = {
        'Content-Type': 'application/json',
    };

    const allFilmsQuery = "query allFilms {\n" +
                                " allFilms {\n" +
                                "   films {\n" +
                                "      id\n" +
                                "      title\n" +
                                "    }\n" +
                                "  }\n" +
                                "}";
    const allFilmsBody = {
      "query": allFilmsQuery
    };
    let allFilmsCall = http.post(`${BASE_URL}/graphql/Movie+Graph+API/1.0`, JSON.stringify(allFilmsBody), { headers: jsonHeaders });
    check(allFilmsCall, {
        'allFilmsCall status is 200': (r) => r.status === 200,
    });

    const aFilmQuery = "query film($id: String) {\n" +
                        " film(id: \"ZmlsbXM6MQ==\") {\n" +
                        "   id\n" +
                        "   title\n" +
                        "   episodeId\n" +
                        " }\n" +
                        "}";
    const aFilmBody = {
      "query": aFilmQuery
    };
    let aFilmCall = http.post(`${BASE_URL}/graphql/Movie+Graph+API/1.0`, JSON.stringify(aFilmBody), { headers: jsonHeaders });
    check(aFilmCall, {
        'aFilmCall status is 200': (r) => r.status === 200,
    });


    const aFilmFragmentsQuery = "query film($id: String) {\n" +
                                  " film(id: \"ZmlsbXM6MQ==\") {\n" +
                                  "   ...filmFields\n" +
                                  " }\n" +
                                  " }\n" +
                                  " fragment filmFields on Film {\n" +
                                  "   id\n" +
                                  "   title\n" +
                                  "   episodeId\n" +
                                  "   starCount\n" +
                                  " }";
    const aFilmFragmentBody = {
      "query": aFilmFragmentsQuery
    };
    let aFilmFragmentCall = http.post(`${BASE_URL}/graphql/Movie+Graph+API/1.0`, JSON.stringify(aFilmFragmentBody), { headers: jsonHeaders });
    check(aFilmFragmentCall, {
        'aFilmFragmentCall status is 200': (r) => r.status === 200,
    });
}

/* Simulate applications issuing SOAP mock invocations . */
export function invokeSOAPMocks() {
    const andrewBody = `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
                         <soapenv:Header/>
                         <soapenv:Body>
                            <hel:sayHello>
                               <name>Andrew</name>
                            </hel:sayHello>
                         </soapenv:Body>
                      </soapenv:Envelope>`;

    // Check SOAP 1.2 headers.
    const andrewHeaders = {
        'Content-Type': 'text/xml',
        'SOAPAction': 'sayHello'
    }
    let andrewCall = http.post(`${BASE_URL}/soap/HelloService+Mock/0.9`, andrewBody, { headers: andrewHeaders });
    check(andrewCall, {
        'andrewCall status is 200': (r) => r.status === 200,
    });
    sleep(1);

    const karlaBody = `<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
                             <soapenv:Header/>
                             <soapenv:Body>
                                <hel:sayHello>
                          	 	      <name>Karla</name>
                                </hel:sayHello>
                             </soapenv:Body>
                          </soapenv:Envelope>`;

    // Check SOAP 1.2 headers.
    const karlaHeaders = {
        'Content-Type': 'application/soap+xml;action=sayHello'
    }
    let karlaCall = http.post(`${BASE_URL}/soap/HelloService+Mock/0.9`, karlaBody, { headers: karlaHeaders });
    check(karlaCall, {
        'karlaCall status is 200': (r) => r.status === 200,
        'karlaCall body is correct': (r) => r.body.includes("<sayHello>Hello Karla !</sayHello>")
    });
    sleep(1);

    const laurentHeaders = {
          'Content-Type': 'application/soap+xml;action=sayHello'
    }
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
        'laurentCall body is fault': (r) => r.body.includes("<soapenv:Fault>")
    });
    sleep(1);
}