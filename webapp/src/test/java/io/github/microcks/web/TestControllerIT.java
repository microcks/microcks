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

import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.TestResult;

import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Test case for the Test controller.
 * @author laurent
 */
public class TestControllerIT extends AbstractBaseIT {

   @ClassRule
   public static GenericContainer pastryImpl = new GenericContainer("quay.io/microcks/quarkus-api-pastry:latest")
         .withExposedPorts(8282);

   @SpyBean
   private TestController testController;

   @Test
   public void testOpenAPITesting() {
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/web/pastry-for-test-openapi.yaml", true);

      String testEndpoint = String.format("http://localhost:%d", pastryImpl.getMappedPort(8282));

      StringBuilder testRequest = new StringBuilder("{").append("\"serviceId\": \"pastry-for-test:2.0.0\", ")
            .append("\"testEndpoint\": \"").append(testEndpoint).append("\", ")
            .append("\"runnerType\": \"OPEN_API_SCHEMA\", ").append("\"timeout\": 2000").append("}");

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> entity = new HttpEntity<>(testRequest.toString(), headers);

      ResponseEntity<TestResult> response = restTemplate.postForEntity("/api/tests", entity, TestResult.class);
      assertEquals(201, response.getStatusCode().value());

      TestResult testResult = response.getBody();
      assertNotNull(testResult);
      assertNotNull(testResult.getId());
      assertTrue(testResult.isInProgress());
      assertEquals(testEndpoint, testResult.getTestedEndpoint());

      // Wait till timeout and re-fetch the result.
      try {
         Thread.sleep(2000);
      } catch (InterruptedException e) {
         throw new RuntimeException(e);
      }

      response = restTemplate.getForEntity("/api/tests/" + testResult.getId(), TestResult.class);
      assertEquals(200, response.getStatusCode().value());

      testResult = response.getBody();

      assertFalse(testResult.isInProgress());
      assertTrue(testResult.isSuccess());

      // Now try accessing messages for basic operation.
      String testCaseId = testResult.getId() + "-" + testResult.getTestNumber() + "-GET%20!pastry";

      List<RequestResponsePair> pairs = testController.getMessagesForTestCase(testResult.getId(), testCaseId);
      assertEquals(1, pairs.size());
      assertEquals("pastries_json", pairs.get(0).getRequest().getName());
   }
}
