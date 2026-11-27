/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.web;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.io.File;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

/**
 * End-to-end test: import an OpenAPI contract through the upload API, then call the generated mock over HTTP.
 * @author laurent
 */
@QuarkusTest
class ArtifactImportMockEndToEndTest {

   private static final String OPENAPI_DIR = "target/test-classes/io/microcks/artifact/openapi/";

   private String upload(String artifact) {
      return given().multiPart("file", new File(OPENAPI_DIR + artifact)).when().post("/api/artifact/upload").then()
            .statusCode(201).extract().asString();
   }

   @Test
   void testOpenAPIImportThenMock() {
      String serviceId = upload("petstore-openapi.json");
      org.junit.jupiter.api.Assertions.assertEquals("PetStore API:1.0.0", serviceId);

      // The imported service is visible through the services API.
      given().when().get("/api/services/count").then().statusCode(200).body("counter", greaterThanOrEqualTo(1));
      given().when().get("/api/services/search?name=PetStore").then().statusCode(200).body("name",
            hasItem("PetStore API"));

      // The mock honors the examples of the contract.
      given().when().get("/rest/PetStore+API/1.0.0/pets").then().statusCode(200).body("$", hasSize(4)).body("[0].name",
            equalTo("Zaza"));
      given().when().get("/rest/PetStore+API/1.0.0/pets/1").then().statusCode(200).body("name", equalTo("Zaza"))
            .body("tag", equalTo("cat"));

      // The service details expose its operations and messages (ServiceView wraps them as in 1.x).
      given().when().get("/api/services/PetStore API:1.0.0").then().statusCode(200)
            .body("service.name", equalTo("PetStore API")).body("service.operations.name", hasItem("GET /pets"))
            .body("messagesMap.'GET /pets'", hasSize(1));
   }

   @Test
   void testOpenAPIValidatedMocking() {
      upload("pastry-with-details-openapi.yaml");

      given().contentType("application/json").accept("application/json").body("{\"price\":2.6}").when()
            .patch("/rest-valid/pastry-details/1.0.0/pastry/Eclair+Cafe").then().statusCode(200)
            .body("price", equalTo(2.6f));

      given().contentType("application/json").accept("application/json").body("{\"price\":\"2.6\"}").when()
            .patch("/rest-valid/pastry-details/1.0.0/pastry/Eclair+Cafe").then().statusCode(400)
            .body(containsString("string found, number expected"));
   }

   @Test
   void testDeleteService() {
      upload("pastry-with-headers-openapi.yaml");
      String id = given().when().get("/api/services/search?name=pastry-headers").then().statusCode(200).extract()
            .path("[0].id");
      given().when().delete("/api/services/" + id).then().statusCode(200);
      given().when().get("/api/services/" + id).then().statusCode(404);
   }
}
