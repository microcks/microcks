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
import org.junit.jupiter.api.Test;

/**
 * Test case for the import endpoint error handling.
 * @author laurent
 */
@QuarkusTest
public class ImportControllerIT extends AbstractBaseIT {

   @Test
   public void shouldReturnBadRequestWhenFileIsEmpty() {
      RestAssured.given().multiPart("file", "empty.json", new byte[0], "application/json").when().post("/api/import")
            .then().statusCode(400);
   }

   @Test
   public void shouldReturnUnsupportedMediaTypeWhenContentTypeIsInvalid() {
      RestAssured.given().multiPart("file", "test.txt", "invalid".getBytes(), "text/plain").when().post("/api/import")
            .then().statusCode(415);
   }

   @Test
   public void shouldReturnInternalServerErrorWhenImportFails() {
      RestAssured.given().multiPart("file", "broken.json", "{}".getBytes(), "application/json").when()
            .post("/api/import").then().statusCode(500);
   }
}
