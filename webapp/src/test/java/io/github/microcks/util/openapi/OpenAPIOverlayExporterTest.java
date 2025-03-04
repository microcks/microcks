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
package io.github.microcks.util.openapi;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.MockRepositoryExportException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class OpenAPIOverlayExporter.
 * @author laurent
 */
public class OpenAPIOverlayExporterTest {

   private static final String EXPECTED_EXPORT_HEADER = """
         overlay: 1.0.0
         info:
           title: API Pastry - 2.1 Overlay for examples
           version: 2.1.0
         actions:
         """;

   private static final String EXPECTED_EXPORT_ACTION_1 = """
         - target: "$.paths['/pastry/{name}'].patch.parameters[?@.name=='name'].examples"
           update:
             Eclair Cafe:
               value: Eclair Cafe
         """;
   private static final String EXPECTED_EXPORT_ACTION_2 = """
         - target: "$.paths['/pastry/{name}'].patch.requestBody.content['application/json'].examples"
           update:
             Eclair Cafe:
               value: |-
                 {
                   "price": 2.6
                 }
         """;
   private static final String EXPECTED_EXPORT_ACTION_3 = """
         - target: "$.paths['/pastry/{name}'].patch.responses.200.content['application/json'].examples"
           update:
             Eclair Cafe:
               value: |-
                 {
                   "name": "Eclair Cafe",
                   "description": "Delicieux Eclair au Cafe pas calorique du tout",
                   "size": "M",
                   "price": 2.6,
                   "status": "available"
                 }
         """;
   private static final String EXPECTED_EXPORT_ACTION_4 = """
         - target: "$.paths['/pastry/{name}'].get.parameters[?@.name=='name'].examples"
           update:
             Eclair Chocolat:
               value: Eclair Chocolat
         """;
   private static final String EXPECTED_EXPORT_ACTION_5 = """
         - target: "$.paths['/pastry/{name}'].get.responses.200.content['application/json'].examples"
           update:
             Eclair Chocolat:
               value: |-
                 {
                   "name": "Eclair Chocolat",
                   "price": 2.5
                 }
         """;

   @Test
   void testExportRESTService() throws Exception {
      Service service = new Service();
      service.setId("123-456-789");
      service.setName("API Pastry - 2.1");
      service.setVersion("2.1.0");
      service.setType(ServiceType.REST);

      Operation getOperation = new Operation();
      getOperation.setName("GET /pastry/{name}");

      Request getRequest = new Request();
      getRequest.setName("Eclair Chocolat");
      Parameter nameParameter = new Parameter();
      nameParameter.setName("name");
      nameParameter.setValue("Eclair Chocolat");
      getRequest.addQueryParameter(nameParameter);

      Response getResponse = new Response();
      getResponse.setName("Eclair Chocolat");
      getResponse.setMediaType("application/json");
      getResponse.setStatus("200");
      getResponse.setContent("{\n  \"name\": \"Eclair Chocolat\",\n  \"price\": 2.5\n}");
      Header poweredHeader = new Header();
      poweredHeader.setName("x-powered-by");
      poweredHeader.setValues(Set.of("microcks"));
      getResponse.addHeader(poweredHeader);

      Operation patchOperation = new Operation();
      patchOperation.setName("PATCH /pastry/{name}");

      Request patchRequest = new Request();
      patchRequest.setName("Eclair Cafe");
      patchRequest.setContent("{\n" + "  \"price\": 2.6\n" + "}");
      Parameter nameParameter2 = new Parameter();
      nameParameter2.setName("name");
      nameParameter2.setValue("Eclair Cafe");
      patchRequest.addQueryParameter(nameParameter2);
      Header contentHeader = new Header();
      contentHeader.setName("Content-Type");
      contentHeader.setValues(Set.of("application/json"));
      patchRequest.addHeader(contentHeader);

      Response patchResponse = new Response();
      patchResponse.setName("Eclair Cafe");
      patchResponse.setMediaType("application/json");
      patchResponse.setStatus("200");
      patchResponse.setContent("{\n" + "  \"name\": \"Eclair Cafe\",\n"
            + "  \"description\": \"Delicieux Eclair au Cafe pas calorique du tout\",\n" + "  \"size\": \"M\",\n"
            + "  \"price\": 2.6,\n" + "  \"status\": \"available\"\n" + "}");
      patchResponse.addHeader(poweredHeader);

      service.setOperations(List.of(getOperation, patchOperation));

      String exportResult = null;
      OpenAPIOverlayExporter exporter = new OpenAPIOverlayExporter();

      try {
         exporter.addServiceDefinition(service);
         exporter.addMessageDefinitions(service, getOperation,
               List.of(new RequestResponsePair(getRequest, getResponse)));
         exporter.addMessageDefinitions(service, patchOperation,
               List.of(new RequestResponsePair(patchRequest, patchResponse)));
         exportResult = exporter.exportAsString();
      } catch (MockRepositoryExportException e) {
         fail("Exception should not be thrown");
      }

      assertTrue(exportResult.contains(EXPECTED_EXPORT_HEADER));
      assertTrue(exportResult.contains(EXPECTED_EXPORT_ACTION_1));
      assertTrue(exportResult.contains(EXPECTED_EXPORT_ACTION_2));
      assertTrue(exportResult.contains(EXPECTED_EXPORT_ACTION_3));
      assertTrue(exportResult.contains(EXPECTED_EXPORT_ACTION_4));
      assertTrue(exportResult.contains(EXPECTED_EXPORT_ACTION_5));
   }
}
