/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.microcks.web;

import io.microcks.domain.Header;
import io.microcks.domain.Operation;
import io.microcks.domain.Request;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.repository.RequestRepository;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.util.IdBuilder;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * End-to-end test of the REST mock endpoint: data is seeded in MongoDB and mocks are invoked over HTTP.
 * @author laurent
 */
@QuarkusTest
class RestControllerTest {

   @Inject
   ServiceRepository serviceRepository;
   @Inject
   ResponseRepository responseRepository;
   @Inject
   RequestRepository requestRepository;

   @BeforeEach
   void seed() {
      requestRepository.deleteAll();
      responseRepository.deleteAll();
      serviceRepository.deleteAll();

      Service service = new Service();
      service.setName("Pet Store");
      service.setVersion("1.0.0");
      service.setType(ServiceType.REST);
      service.setXmlNS("");

      Operation list = operation("GET /pets", "GET", null, null);
      Operation byId = operation("GET /pets/{id}", "GET", "URI_PARTS", "id");
      service.setOperations(List.of(list, byId));
      serviceRepository.persist(service);

      response(service, list, "200", "application/json", "[{\"id\":1}]", null);
      response(service, byId, "200", "application/json", "{\"id\":1,\"name\":\"rex\"}", "/id=1");
      response(service, byId, "200", "application/json", "{\"id\":2,\"name\":\"tom\"}", "/id=2");
   }

   @Test
   void mockReturnsDispatchedResponse() {
      given().when().get("/rest/Pet+Store/1.0.0/pets").then().statusCode(200).contentType("application/json")
            .header("X-Mock", equalTo("yes")).body("[0].id", equalTo(1));
      given().when().get("/rest/Pet+Store/1.0.0/pets/1").then().statusCode(200).body("name", equalTo("rex"));
      given().when().get("/rest/Pet+Store/1.0.0/pets/2").then().statusCode(200).body("name", equalTo("tom"));
   }

   @Test
   void mockHandlesTrailingSlashAndUnknownResources() {
      given().when().get("/rest/Pet+Store/1.0.0/pets/").then().statusCode(200);
      given().when().get("/rest/Pet+Store/1.0.0/unknown").then().statusCode(404);
      given().when().get("/rest/Unknown/1.0.0/pets").then().statusCode(404).body(containsString("Unknown"));
   }

   @Test
   void mockHandlesCorsPreflight() {
      given().header("Origin", "http://example.com").header("Access-Control-Request-Method", "GET").when()
            .options("/rest/Pet+Store/1.0.0/pets").then().statusCode(200)
            .header("Access-Control-Allow-Origin", equalTo("http://example.com"));
   }

   private Operation operation(String name, String method, String dispatcher, String rules) {
      Operation operation = new Operation();
      operation.setName(name);
      operation.setMethod(method);
      operation.setDispatcher(dispatcher);
      operation.setDispatcherRules(rules);
      String path = name.substring(name.indexOf(' ') + 1);
      operation
            .setResourcePaths(new java.util.HashSet<>(List.of(path.replace("{id}", "1"), path.replace("{id}", "2"))));
      return operation;
   }

   private void response(Service service, Operation operation, String status, String mediaType, String content,
         String dispatchCriteria) {
      Header header = new Header();
      header.setName("X-Mock");
      header.setValues(Set.of("yes"));

      String operationId = IdBuilder.buildOperationId(service, operation);
      Request request = new Request();
      request.setName("sample");
      request.setOperationId(operationId);
      requestRepository.persist(request);

      Response response = new Response();
      response.setName("sample");
      response.setOperationId(operationId);
      response.setStatus(status);
      response.setMediaType(mediaType);
      response.setContent(content);
      response.setDispatchCriteria(dispatchCriteria);
      response.setHeaders(Set.of(header));
      responseRepository.persist(response);
   }
}
