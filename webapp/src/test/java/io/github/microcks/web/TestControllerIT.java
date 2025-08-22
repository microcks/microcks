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
import io.github.microcks.domain.TestStepResult;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for the Test controller.
 * @author laurent
 */
@Testcontainers
class TestControllerIT extends AbstractBaseIT {

   @Container
   public static GenericContainer pastryImpl = new GenericContainer("quay.io/microcks/quarkus-api-pastry:latest")
         .withExposedPorts(8282);

   @Container
   public static GenericContainer helloWorldImpl = new GenericContainer("quay.io/microcks/grpc-hello-world:nightly")
         .withExposedPorts(9000);

   @SpyBean
   private TestController testController;

   @Test
   void testOpenAPITesting() {
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
      assertNotNull(testResult);
      assertFalse(testResult.isInProgress());
      assertTrue(testResult.isSuccess());

      // Now try accessing messages for basic operation.
      String testCaseId = testResult.getId() + "-" + testResult.getTestNumber() + "-GET%20!pastry";

      List<RequestResponsePair> pairs = testController.getMessagesForTestCase(testResult.getId(), testCaseId);
      assertEquals(1, pairs.size());
      assertEquals("pastries_json", pairs.get(0).getRequest().getName());
   }

   @Test
   void testSoapUITesting() {
      // Upload Hello Service SoapUI project.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/soapui/HelloService-soapui-project.xml", true);

      String testEndpoint = getServerUrl() + "/soap/HelloService+Mock/0.9";

      StringBuilder testRequest = new StringBuilder("{").append("\"serviceId\": \"HelloService Mock:0.9\", ")
            .append("\"testEndpoint\": \"").append(testEndpoint).append("\", ").append("\"runnerType\": \"SOAP_UI\", ")
            .append("\"timeout\": 2000").append("}");

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
      assertNotNull(testResult);
      assertFalse(testResult.isInProgress());
      // 2 tests steps are ok, 1 is failing.
      assertFalse(testResult.isSuccess());

      assertEquals(1, testResult.getTestCaseResults().size());
      List<TestStepResult> testStepResults = testResult.getTestCaseResults().getFirst().getTestStepResults();
      for (TestStepResult testStepResult : testStepResults) {
         if (testStepResult.getRequestName().equals("Andrew Request")) {
            assertFalse(testStepResult.isSuccess());
            assertEquals("Assertion 'XQuery Match' is not managed by Microcks at the moment\n",
                  testStepResult.getMessage());
         } else if (testStepResult.getRequestName().equals("Karla Request")) {
            assertTrue(testStepResult.isSuccess());
         } else if (testStepResult.getRequestName().equals("World Request")) {
            assertTrue(testStepResult.isSuccess());
         } else {
            fail("Unexpected request name in test step result: " + testStepResult.getRequestName());
         }
      }

      // Now try accessing messages for basic operation.
      String testCaseId = testResult.getId() + "-" + testResult.getTestNumber() + "-sayHello";

      List<RequestResponsePair> pairs = testController.getMessagesForTestCase(testResult.getId(), testCaseId);
      assertEquals(3, pairs.size());
   }

   @Test
   void testGRPCTesting() {
      // Upload GRPC reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/hello-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/HelloService.postman.json", false);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/HelloService.metadata.yml", false);

      String testEndpoint = String.format("http://localhost:%d", helloWorldImpl.getMappedPort(9000));

      StringBuilder testRequest = new StringBuilder("{")
            .append("\"serviceId\": \"io.github.microcks.grpc.hello.v1.HelloService:v1\", ")
            .append("\"testEndpoint\": \"").append(testEndpoint).append("\", ")
            .append("\"runnerType\": \"GRPC_PROTOBUF\", ").append("\"timeout\": 2000").append("}");

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
      assertNotNull(testResult);
      assertFalse(testResult.isInProgress());
      assertTrue(testResult.isSuccess());

      // Now try accessing messages for basic operation.
      String testCaseId = testResult.getId() + "-" + testResult.getTestNumber() + "-greeting";

      List<RequestResponsePair> pairs = testController.getMessagesForTestCase(testResult.getId(), testCaseId);
      assertEquals(2, pairs.size());
      for (RequestResponsePair pair : pairs) {
         assertTrue(pair.getRequest().getName().equals("Laurent") || pair.getRequest().getName().equals("Philippe"));
      }
   }

   @Test
   void testGRPCTestingFailing() {
      // Upload GRPC reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/hello-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/HelloService.postman.json", false);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/HelloService.metadata.yml", false);

      String testEndpoint = "http://localhost:50051"; // unreachable address, will lead to UNAVAILABLE

      StringBuilder testRequest = new StringBuilder("{")
            .append("\"serviceId\": \"io.github.microcks.grpc.hello.v1.HelloService:v1\", ")
            .append("\"testEndpoint\": \"").append(testEndpoint).append("\", ")
            .append("\"runnerType\": \"GRPC_PROTOBUF\", ").append("\"timeout\": 2000").append("}");

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
      assertNotNull(testResult);
      assertFalse(testResult.isInProgress());
      assertFalse(testResult.isSuccess());
   }

}
