/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.web;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for the Soap mock controller.
 * @author laurent
 */
class SoapControllerIT extends AbstractBaseIT {

   @Test
   void testHelloSoapWSMocking() {
      // Upload Hello Service SoapUI project.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/soapui/HelloService-soapui-project.xml", true);

      // Build the request.
      String request = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHello>\n"
            + "         <name>Karla</name>\n" + "      </hel:sayHello>\n" + "   </soapenv:Body>\n"
            + "</soapenv:Envelope>";

      // Execute and assert.
      var response = restTestClient.post().uri("/soap/HelloService+Mock/0.9")
            .header("Content-Type", "application/soap+xml;action=sayHello").body(request).exchange().expectStatus()
            .isOk().expectHeader().contentType("application/soap+xml;charset=UTF-8").returnResult(String.class);
      assertEquals(
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
                  + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHelloResponse>\n"
                  + "         <sayHello>Hello Karla !</sayHello>\n" + "      </hel:sayHelloResponse>\n"
                  + "   </soapenv:Body>\n" + "</soapenv:Envelope>",
            response.getResponseBody());

      // Build the request for SOAP 1.1.
      request = "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soap-env:Header/>\n" + "   <soap-env:Body>\n" + "      <hel:sayHello>\n"
            + "         <name>Andrew</name>\n" + "      </hel:sayHello>\n" + "   </soap-env:Body>\n"
            + "</soap-env:Envelope>";

      // Execute and assert, content-type is different for SOAP 1.1.
      response = restTestClient.post().uri("/soap/HelloService+Mock/0.9").header("SOAPAction", "\"sayHello\"")
            .header("Content-Type", "text/xml").body(request).exchange().expectStatus().isOk().expectHeader()
            .contentType("text/xml;charset=UTF-8").returnResult(String.class);
      assertEquals(
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
                  + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHelloResponse>\n"
                  + "         <sayHello>Hello Andrew !</sayHello>\n" + "      </hel:sayHelloResponse>\n"
                  + "   </soapenv:Body>\n" + "</soapenv:Envelope>",
            response.getResponseBody());

      // Test exception case.
      request = "<soap-env:Envelope xmlns:soap-env=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soap-env:Header/>\n" + "   <soap-env:Body>\n" + "      <hel:sayHello>\n"
            + "         <name>World</name>\n" + "      </hel:sayHello>\n" + "   </soap-env:Body>\n"
            + "</soap-env:Envelope>";

      // Execute and assert.
      response = restTestClient.post().uri("/soap/HelloService+Mock/0.9").header("SOAPAction", "\"sayHello\"")
            .body(request).exchange().expectStatus().is5xxServerError().returnResult(String.class);
      assertEquals(500, response.getStatus().value());
      assertEquals(
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
                  + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <soapenv:Fault>\n"
                  + "         <faultcode>soapenv:Sender</faultcode>\n"
                  + "         <faultstring>Unknown name</faultstring>\n" + "         <detail>\n"
                  + "            <hel:HelloException>\n" + "               <code>999</code>\n"
                  + "            </hel:HelloException>\n" + "         </detail>\n" + "      </soapenv:Fault>\n"
                  + "   </soapenv:Body>\n" + "</soapenv:Envelope>",
            response.getResponseBody());
   }

   @Test
   void testHelloRandomSoapWSMocking() {
      // given list of responses
      List<String> okResponses = new ArrayList<>();
      List<String> koResponses = new ArrayList<>();
      okResponses.add(
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
                  + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHelloResponse>\n"
                  + "         <sayHello>Hello Karla !</sayHello>\n" + "      </hel:sayHelloResponse>\n"
                  + "   </soapenv:Body>\n" + "</soapenv:Envelope>");
      okResponses.add(
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
                  + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHelloResponse>\n"
                  + "         <sayHello>Hello Andrew !</sayHello>\n" + "      </hel:sayHelloResponse>\n"
                  + "   </soapenv:Body>\n" + "</soapenv:Envelope>");
      koResponses.add(
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
                  + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <soapenv:Fault>\n"
                  + "         <faultcode>soapenv:Sender</faultcode>\n"
                  + "         <faultstring>Unknown name</faultstring>\n" + "         <detail>\n"
                  + "            <hel:HelloException>\n" + "               <code>999</code>\n"
                  + "            </hel:HelloException>\n" + "         </detail>\n" + "      </soapenv:Fault>\n"
                  + "   </soapenv:Body>\n" + "</soapenv:Envelope>");


      // Upload Hello Service SoapUI project.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/soapui/HelloService-random-soapui-project.xml",
            true);

      // Build the request.
      String request = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:hel=\"http://www.example.com/hello\">\n"
            + "   <soapenv:Header/>\n" + "   <soapenv:Body>\n" + "      <hel:sayHello>\n"
            + "         <name>whatever</name>\n" + "      </hel:sayHello>\n" + "   </soapenv:Body>\n"
            + "</soapenv:Envelope>";

      // Execute and assert.
      for (int i = 0; i < 10; ++i) {
         var response = restTestClient.post().uri("/soap/HelloService+Mock/0.9")
               .header("Content-Type", "application/soap+xml;action=sayHello").body(request).exchange()
               .returnResult(String.class);
         switch (response.getStatus().value()) {
            case 200:
               assertTrue(okResponses.contains(response.getResponseBody()));
               break;
            case 500:
               assertTrue(koResponses.contains(response.getResponseBody()));
               break;
            default:
               fail();
         }
      }
   }

   @Test
   void testProxy() {
      // Upload SoapUI projects for proxy test.
      uploadArtifactFile(
            "target/test-classes/io/github/microcks/util/soapui/HelloService-to-set-proxy-soapui-project.xml", true);
      uploadArtifactFile(
            "target/test-classes/io/github/microcks/util/soapui/HelloService-to-test-proxy-soapui-project.xml", true);

      // Override the dispatcher to PROXY
      Service service = serviceRepository.findByNameAndVersion("HelloService Mock", "0.9");
      Operation operation = service.getOperations().stream().findFirst().orElseThrow();
      operation.setDispatcher("PROXY");
      operation.setDispatcherRules(getServerUrl() + "/soap/HelloService+Real/0.9");
      serviceRepository.save(service);

      // Execute and assert.
      var response = restTestClient.post().uri("/soap/HelloService+Mock/0.9")
            .header("Content-Type", "application/soap+xml;action=sayHello").body(createBaseBodyForName("Andrew"))
            .exchange().expectStatus().isOk().returnResult(String.class);
      assertResponseIsOkAndContains(response, "<sayHello>Hello Real Andrew !</sayHello>");
   }

   @Test
   void testProxyFallback() {
      // Upload SoapUI projects for proxy test.
      uploadArtifactFile(
            "target/test-classes/io/github/microcks/util/soapui/HelloService-to-set-proxy-soapui-project.xml", true);
      uploadArtifactFile(
            "target/test-classes/io/github/microcks/util/soapui/HelloService-to-test-proxy-soapui-project.xml", true);

      // Override the dispatcher to PROXY_FALLBACK
      Service service = serviceRepository.findByNameAndVersion("HelloService Mock", "0.9");
      Operation operation = service.getOperations().stream().findFirst().orElseThrow();
      operation.setDispatcher("PROXY_FALLBACK");
      operation.setDispatcherRules(String.format("""
            {"dispatcher": "QUERY_MATCH",
            "dispatcherRules": "declare namespace ser='http://www.example.com/hello';\\n//ser:sayHello/name",
            "proxyUrl": "%s/soap/HelloService+Real/0.9"}""", getServerUrl()));
      serviceRepository.save(service);

      // Build the request that matches QUERY_MATCH.
      // Execute and assert that it wasn't proxy.
      var response = restTestClient.post().uri("/soap/HelloService+Mock/0.9")
            .header("Content-Type", "application/soap+xml;action=sayHello").body(createBaseBodyForName("Andrew"))
            .exchange().expectStatus().isOk().returnResult(String.class);
      assertResponseIsOkAndContains(response, "<sayHello>Hello Andrew !</sayHello>");

      // Build the request that doesn't match QUERY_MATCH.
      // Execute and assert that it was proxy.
      response = restTestClient.post().uri("/soap/HelloService+Mock/0.9")
            .header("Content-Type", "application/soap+xml;action=sayHello").body(createBaseBodyForName("Garry"))
            .exchange().expectStatus().isOk().returnResult(String.class);
      assertResponseIsOkAndContains(response, "<sayHello>Hello Real Garry !</sayHello>");
   }

   private String createBaseBodyForName(String name) {
      return String.format(
            """
                  <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://www.example.com/hello">
                     <soapenv:Header/><soapenv:Body><hel:sayHello><name>%s</name></hel:sayHello></soapenv:Body>
                  </soapenv:Envelope>""",
            name);
   }
}
