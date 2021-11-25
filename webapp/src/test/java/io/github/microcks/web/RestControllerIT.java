/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.web;

import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.ArraySizeComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
/**
 * Test case for all the Rest mock controller.
 * @author laurent
 */
public class RestControllerIT extends AbstractBaseIT {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(RestControllerIT.class);

   @Test
   public void testOpenAPIMocking() {
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json");

      // Check its different mocked operations.
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/PetStore+API/1.0.0/pets", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("[4]", response.getBody(), new ArraySizeComparator(JSONCompareMode.LENIENT));
         JSONAssert.assertEquals("[{\"id\":1,\"name\":\"Zaza\",\"tag\":\"cat\"},{\"id\":2,\"name\":\"Tigresse\",\"tag\":\"cat\"},{\"id\":3,\"name\":\"Maki\",\"tag\":\"cat\"},{\"id\":4,\"name\":\"Toufik\",\"tag\":\"cat\"}]",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      response = restTemplate.getForEntity("/rest/PetStore+API/1.0.0/pets/1", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"id\":1,\"name\":\"Zaza\",\"tag\":\"cat\"}",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   private void uploadArtifactFile(String artifactFilePath) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", new FileSystemResource(new File(artifactFilePath)));

      HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
      ResponseEntity<String> response = restTemplate.postForEntity("/api/artifact/upload", requestEntity, String.class);

      assertEquals(201, response.getStatusCode().value());
      log.info("Just uploaded: " + response.getBody());
   }
}
