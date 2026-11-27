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
package io.microcks.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

/**
 * Test case for the artifact download endpoint.
 * @author laurent
 */
@QuarkusTest
public class UploadArtifactControllerIT extends AbstractBaseIT {

   @Test
   public void shouldNotCreateWhenUrlIsEmpty() {
      RestAssured.given().queryParam("url", "").when().post("/api/artifact/download").then().statusCode(204);
   }

   @Test
   public void shouldCreateServiceFromOpenAPIImporter() {
      String apiPastry = "https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml";
      RestAssured.given().queryParam("url", apiPastry).when().post("/api/artifact/download").then().statusCode(201)
            .body("name", Matchers.is("API Pastry - 2.0:2.0.0"));
   }

   @Test
   public void shouldCreateServiceFromPostmanCorrectly() {
      String beerCatalogAPI = "https://raw.githubusercontent.com/microcks/microcks/master/samples/BeerCatalogAPI-collection.json";
      RestAssured.given().queryParam("url", beerCatalogAPI).when().post("/api/artifact/download").then().statusCode(201)
            .body("name", Matchers.is("Beer Catalog API:0.99"));
   }

   @Test
   public void shouldNotCreateServiceWhenTheUrlIsWrong() {
      String wrongUrl = "https://raw.githubusercontent.com/microcks/microcks/master/samples/wrong-collection.json";
      RestAssured.given().queryParam("url", wrongUrl).when().post("/api/artifact/download").then().statusCode(500)
            .body(Matchers.containsString("Exception while retrieving remote item"));
   }

   @Test
   public void shouldNotCreateServiceWithoutUrl() {
      RestAssured.when().post("/api/artifact/download").then().statusCode(204);
   }
}
