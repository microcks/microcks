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

package io.github.microcks.util.metadata;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.MockRepositoryImportException;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class ExamplesImporter.
 * @author laurent
 */
class ExamplesImporterTest {

   @Test
   void testOpenAPIAPIExamplesImporter() {
      ExamplesImporter importer = null;
      try {
         importer = new ExamplesImporter(
               "target/test-classes/io/github/microcks/util/metadata/APIPastry-2.0-examples.yml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("API Pastry - 2.0", service.getName());
      assertEquals("2.0.0", service.getVersion());

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().get(0);

      assertEquals("GET /pastry/{name}", operation.getName());

      List<Exchange> exchanges = null;
      try {
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }

      // No dispatcher has been seet but we should have at least the resource path pattern.
      assertNull(operation.getDispatcher());
      assertNotNull(operation.getResourcePaths());
      assertEquals(1, operation.getResourcePaths().size());
      assertTrue(operation.getResourcePaths().contains("/pastry/{name}"));

      assertNotNull(exchanges);
      assertEquals(3, exchanges.size());

      for (Exchange exchange : exchanges) {
         if (exchange instanceof RequestResponsePair pair) {
            assertNotNull(pair.getRequest());
            assertNotNull(pair.getResponse());
            assertEquals(pair.getRequest().getName(), pair.getResponse().getName());

            assertNotNull(pair.getRequest().getQueryParameters());
            assertEquals(1, pair.getRequest().getQueryParameters().size());

            Parameter parameter = pair.getRequest().getQueryParameters().get(0);
            assertEquals("name", parameter.getName());

            if ("Eclair Chocolat".equals(pair.getRequest().getName())) {
               assertEquals("Eclair Chocolat", parameter.getValue());

               // Check that content has been transformed in JSON.
               assertEquals("application/json", pair.getResponse().getMediaType());
               assertEquals(
                     "{\"name\":\"Eclair Chocolat\",\"description\":\"Delicieux Eclair Chocolat pas calorique du tout\",\"size\":\"M\",\"price\":2.5,\"status\":\"unknown\"}",
                     pair.getResponse().getContent());
            } else if ("Eclair Chocolat Xml".equals(pair.getRequest().getName())) {
               assertEquals("Eclair Chocolat Xml", parameter.getValue());

               // Check that content has been read as is (XML).
               assertEquals("text/xml", pair.getResponse().getMediaType());
               assertEquals("<pastry>\n" + "  <name>Eclair Cafe</name>\n"
                     + "  <description>Delicieux Eclair au Chocolat pas calorique du tout</description>\n"
                     + "  <size>M</size>\n" + "  <price>2.5</price>\n" + "  <status>unknown</status>\n" + "</pastry>",
                     pair.getResponse().getContent());
            } else if ("Eclair Chocolat Empty Status".equals(pair.getRequest().getName())) {
               assertEquals("Eclair Chocolat", parameter.getValue());

               // Check that content has been transformed in JSON.
               assertEquals("application/json", pair.getResponse().getMediaType());
               assertEquals(
                     "{\"name\":\"Eclair Chocolat\",\"description\":\"Delicieux Eclair Chocolat pas calorique du tout\",\"size\":\"M\",\"price\":2.5}",
                     pair.getResponse().getContent());
               // Check that default status value has got set
               assertEquals("200", pair.getResponse().getStatus());
            }
         } else {
            fail("Unknown extracted exchange type");
         }
      }
   }

   @Test
   void testPathAndQueryParametersSplitting() {
      ExamplesImporter importer = null;
      try {
         importer = new ExamplesImporter(
               "target/test-classes/io/github/microcks/util/metadata/weather-forecast-examples.yml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("WeatherForecast API", service.getName());
      assertEquals("1.0.0", service.getVersion());

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().get(0);

      assertEquals("GET /forecast/{region}", operation.getName());

      // Force the operation dispatcher to URI_ELEMENTS to check if importer correctly
      // splits the query from the path parameters when computing dispatch criteria.
      operation.setDispatcher("URI_ELEMENTS");
      operation.setDispatcherRules("region && apiKey");

      List<Exchange> exchanges = null;
      try {
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }

      assertNotNull(exchanges);
      assertEquals(1, exchanges.size());

      Exchange exchange = exchanges.get(0);
      assertTrue(exchange instanceof RequestResponsePair);

      RequestResponsePair pair = (RequestResponsePair) exchange;
      assertNotNull(pair.getRequest());
      assertNotNull(pair.getRequest().getQueryParameters());
      assertEquals(2, pair.getRequest().getQueryParameters().size());

      assertNotNull(pair.getResponse());
      assertEquals("/region=north?apiKey=123456", pair.getResponse().getDispatchCriteria());
   }

   @Test
   void testHeadersDispatcherExtractiong() {
      ExamplesImporter importer = null;
      try {
         importer = new ExamplesImporter(
               "target/test-classes/io/github/microcks/util/metadata/weather-forecast-headers-examples.yml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("WeatherForecast API", service.getName());
      assertEquals("1.0.0", service.getVersion());

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().get(0);

      assertEquals("GET /forecast", operation.getName());

      // Force the operation dispatcher to URI_ELEMENTS to check if importer correctly
      // splits the query from the path parameters when computing dispatch criteria.
      operation.setDispatcher("QUERY_HEADER");
      operation.setDispatcherRules("region");

      List<Exchange> exchanges = null;
      try {
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }

      assertNotNull(exchanges);
      assertEquals(1, exchanges.size());

      Exchange exchange = exchanges.get(0);
      assertTrue(exchange instanceof RequestResponsePair);

      RequestResponsePair pair = (RequestResponsePair) exchange;
      assertNotNull(pair.getRequest());
      assertNull(pair.getRequest().getQueryParameters());

      assertNotNull(pair.getResponse());
      assertEquals("?region=north", pair.getResponse().getDispatchCriteria());
   }

   @Test
   void testGRPCAPIExamplesImporter() {
      ExamplesImporter importer = null;
      try {
         importer = new ExamplesImporter(
               "target/test-classes/io/github/microcks/util/metadata/hello-grpc-v1-examples.yml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("io.github.microcks.grpc.hello.v1.HelloService", service.getName());
      assertEquals("v1", service.getVersion());

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().get(0);

      assertEquals("greeting", operation.getName());

      List<Exchange> exchanges = null;
      try {
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }

      assertNotNull(exchanges);
      assertEquals(2, exchanges.size());

      for (Exchange exchange : exchanges) {
         if (exchange instanceof RequestResponsePair pair) {
            assertNotNull(pair.getRequest());
            assertNotNull(pair.getResponse());
            assertEquals(pair.getRequest().getName(), pair.getResponse().getName());

            if ("Laurent".equals(pair.getRequest().getName())) {
               // Check that content has been transformed in JSON.
               assertEquals("{\"firstname\":\"Laurent\",\"lastname\":\"Broudoux\"}", pair.getRequest().getContent());
               assertEquals("{\"greeting\":\"Hello Laurent Broudoux !\"}", pair.getResponse().getContent());
            } else if ("John".equals(pair.getRequest().getName())) {
               // Check that content has been read and transformed in JSON.
               assertEquals("{\"firstname\": \"John\", \"lastname\": \"Doe\"}", pair.getRequest().getContent());
               assertEquals("{\"greeting\":\"Hello John Doe !\"}", pair.getResponse().getContent());
            }
         } else {
            fail("Unknown extracted exchange type");
         }
      }
   }

   @Test
   void testAsyncAPIExamplesImporter() {
      ExamplesImporter importer = null;
      try {
         importer = new ExamplesImporter(
               "target/test-classes/io/github/microcks/util/metadata/user-signedup-0.1.0-examples.yml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("User signed-up API", service.getName());
      assertEquals("0.1.0", service.getVersion());

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().get(0);

      assertEquals("SUBSCRIBE /user/signedup", operation.getName());

      List<Exchange> exchanges = null;
      try {
         service.setType(ServiceType.EVENT);
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }

      assertNotNull(exchanges);
      assertEquals(1, exchanges.size());

      Exchange exchange = exchanges.get(0);
      assertInstanceOf(UnidirectionalEvent.class, exchange);

      EventMessage event = ((UnidirectionalEvent) exchange).getEventMessage();

      assertNotNull(event);
      assertEquals("jane", event.getName());
      assertEquals("{\"fullName\":\"Jane Doe\",\"email\":\"jane@microcks.io\",\"age\":35}", event.getContent());

      assertNotNull(event.getHeaders());
      assertEquals(2, event.getHeaders().size());

      for (Header header : event.getHeaders()) {
         assertTrue("my-app-header".equals(header.getName()) || "sentAt".equals(header.getName()));
         assertTrue(header.getValues().contains("123") || header.getValues().contains("2024-07-14T18:01:28Z"));
      }
   }
}
