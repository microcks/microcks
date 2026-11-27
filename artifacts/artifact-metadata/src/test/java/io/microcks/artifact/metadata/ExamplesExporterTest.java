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
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.MockRepositoryExportException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is a test case for class ExamplesExporter.
 * @author laurent
 */
class ExamplesExporterTest {

   private static final String EXPECTED_REST_SERVICE_EXPORT = """
         apiVersion: mocks.microcks.io/v1alpha1
         kind: APIExamples
         metadata:
           name: API Pastry - 2.1
           version: 2.1.0
         operations:
           GET /pastry/{name}:
             Eclair Chocolat:
               request:
                 parameters:
                   name: Eclair Chocolat
               response:
                 headers:
                   x-powered-by: microcks
                 status: 200
                 mediaType: application/json
                 body: |-
                   {
                     "name": "Eclair Chocolat",
                     "price": 2.5
                   }
         """;

   private static final String EXPECTED_EVENT_SERVICE_EXPORT = """
         apiVersion: mocks.microcks.io/v1alpha1
         kind: APIExamples
         metadata:
           name: User signed-up API
           version: 0.1.0
         operations:
           SUBSCRIBE /user/signedup:
             jane:
               eventMessage:
                 headers:
                   my-app-header: 123
                 payload: |-
                   {
                     "fullName": "Jane Doe",
                     "email": "jane@microcks.io",
                     "age": 35
                   }
         """;

   @Test
   void testExportRESTService() {
      Service service = new Service();
      service.setId("123-456-789");
      service.setName("API Pastry - 2.1");
      service.setVersion("2.1.0");
      service.setType(ServiceType.REST);

      Operation operation = new Operation();
      operation.setName("GET /pastry/{name}");
      service.setOperations(List.of(operation));

      Request request = new Request();
      request.setName("Eclair Chocolat");
      Parameter nameParameter = new Parameter();
      nameParameter.setName("name");
      nameParameter.setValue("Eclair Chocolat");
      request.addQueryParameter(nameParameter);

      Response response = new Response();
      response.setName("Eclair Chocolat");
      response.setMediaType("application/json");
      response.setStatus("200");
      response.setContent("{\n  \"name\": \"Eclair Chocolat\",\n  \"price\": 2.5\n}");
      Header poweredHeader = new Header();
      poweredHeader.setName("x-powered-by");
      poweredHeader.setValues(Set.of("microcks"));
      response.addHeader(poweredHeader);

      String exportResult = null;
      ExamplesExporter exporter = new ExamplesExporter();

      try {
         exporter.addServiceDefinition(service);
         exporter.addMessageDefinitions(service, operation, List.of(new RequestResponsePair(request, response)));
         exportResult = exporter.exportAsString();
      } catch (MockRepositoryExportException e) {
         fail("Exception should not be thrown");
      }

      assertEquals(EXPECTED_REST_SERVICE_EXPORT, exportResult);
   }

   @Test
   void testExportEventService() {
      Service service = new Service();
      service.setId("123-456-789");
      service.setName("User signed-up API");
      service.setVersion("0.1.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE /user/signedup");
      service.setOperations(List.of(operation));

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("jane");

      Header appHeader = new Header();
      appHeader.setName("my-app-header");
      appHeader.setValues(Set.of("123"));
      eventMessage.setHeaders(Set.of(appHeader));
      eventMessage.setContent("{\n  \"fullName\": \"Jane Doe\",\n  \"email\": \"jane@microcks.io\",\n  \"age\": 35\n}");

      String exportResult = null;
      ExamplesExporter exporter = new ExamplesExporter();

      try {
         exporter.addServiceDefinition(service);
         exporter.addMessageDefinitions(service, operation, List.of(new UnidirectionalEvent(eventMessage)));
         exportResult = exporter.exportAsString();
      } catch (MockRepositoryExportException e) {
         fail("Exception should not be thrown");
      }

      assertEquals(EXPECTED_EVENT_SERVICE_EXPORT, exportResult);
   }
}
