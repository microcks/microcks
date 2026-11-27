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

import io.microcks.domain.Exchange;
import io.microcks.domain.Header;
import io.microcks.domain.OperationsHeaders;
import io.microcks.domain.RequestResponsePair;
import io.microcks.domain.Secret;
import io.microcks.domain.SecretRef;
import io.microcks.domain.Service;
import io.microcks.domain.TestCaseResult;
import io.microcks.domain.TestOptionals;
import io.microcks.domain.TestResult;
import io.microcks.domain.TestRunnerType;
import io.microcks.repository.SecretRepository;
import io.microcks.repository.TestResultRepository;
import io.microcks.service.MessageService;
import io.microcks.service.ServiceService;
import io.microcks.service.TestService;
import io.microcks.util.SafeLogger;
import io.microcks.web.dto.HeaderDTO;
import io.microcks.web.dto.TestCaseReturnDTO;
import io.microcks.web.dto.TestRequestDTO;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Rest controller for API defined on test results.
 * @author laurent
 */
@ApplicationScoped
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class TestController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(TestController.class);

   private final TestResultRepository testResultRepository;
   private final SecretRepository secretRepository;
   private final TestService testService;
   private final MessageService messageService;
   private final ServiceService serviceService;

   /**
    * Create a TestController with required dependencies.
    * @param testService          Service to launch tests and report results
    * @param messageService       Service to report new test messages
    * @param testResultRepository Get access to test results
    * @param serviceService       Service to get access to Services
    * @param secretRepository     Get access to Secrets
    */
   @Inject
   public TestController(TestService testService, MessageService messageService, ServiceService serviceService,
         TestResultRepository testResultRepository, SecretRepository secretRepository) {
      this.testService = testService;
      this.messageService = messageService;
      this.serviceService = serviceService;
      this.testResultRepository = testResultRepository;
      this.secretRepository = secretRepository;
   }

   @GET
   @Path("/tests/service/{serviceId}")
   public List<TestResult> listTestsByService(@PathParam("serviceId") String serviceId,
         @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("20") int size) {
      log.debug("Getting tests list for service {}, page {} and size {}", serviceId, page, size);
      return testResultRepository.findByServiceId(serviceId, Page.of(page, size), Sort.descending("testNumber"));
   }

   @GET
   @Path("/tests/service/{serviceId}/count")
   public Map<String, Long> countTestsByService(@PathParam("serviceId") String serviceId) {
      log.debug("Counting tests for service...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", testResultRepository.countByServiceId(serviceId));
      return counter;
   }

   @POST
   @Path("/tests")
   public Response createTest(TestRequestDTO test) {
      log.debug("Creating new test for {} on endpoint {}", test.getServiceId(), test.getTestEndpoint());
      // serviceId may have the form of <service_name>:<service_version> or just <service_id>
      Service service = serviceService.getServiceById(test.getServiceId());
      TestRunnerType testRunner = TestRunnerType.valueOf(test.getRunnerType());

      // Build additional header entries for operations.
      OperationsHeaders operationsHeaders = buildOperationsHeaders(test.getOperationsHeaders());

      // Deal with Secret check and retrieval if specified.
      SecretRef secretRef = null;
      if (test.getSecretName() != null) {
         List<Secret> secrets = secretRepository.findByName(test.getSecretName());
         if (!secrets.isEmpty()) {
            secretRef = new SecretRef(secrets.get(0).getId(), secrets.get(0).getName());
         } else {
            log.warn("Secret '" + test.getSecretName() + "' specified in test request but not found");
            return Response.status(Response.Status.BAD_REQUEST)
                  .entity(String.format(
                        "Secret '%s' not found. Please ensure the secret exists before creating " + "the test.",
                        test.getSecretName()))
                  .build();
         }
      }

      TestOptionals testOptionals = new TestOptionals(secretRef, test.getTimeout(), test.getFilteredOperations(),
            operationsHeaders, test.getOAuth2Context());
      TestResult testResult = testService.launchTests(service, test.getTestEndpoint(), testRunner, testOptionals);
      return Response.status(Response.Status.CREATED).entity(testResult).build();
   }

   @GET
   @Path("/tests/{id}")
   public Response getTestResult(@PathParam("id") String testResultId) {
      log.debug("Getting TestResult with id {}", testResultId);
      return Response.status(Response.Status.OK).entity(testResultRepository.findById(testResultId)).build();
   }

   @GET
   @Path("tests/{id}/messages/{testCaseId}")
   public List<RequestResponsePair> getMessagesForTestCase(@PathParam("id") String testResultId,
         @PathParam("testCaseId") String testCaseId) {
      // We may have testCaseId being URLEncoded, with forbidden '/' replaced by '_' so unwrap id.
      // Switched form _ to ! in replacement as less commonly used in URL parameters, as in other frameworks
      testCaseId = URLDecoder.decode(testCaseId, StandardCharsets.UTF_8);
      testCaseId = testCaseId.replace('!', '/');
      log.debug("Getting messages for testCase {} on test {}", testCaseId, testResultId);
      return messageService.getRequestResponseByTestCase(testCaseId);
   }

   @GET
   @Path("tests/{id}/events/{testCaseId}")
   public List<? extends Exchange> getEventMessagesForTestCase(@PathParam("id") String testResultId,
         @PathParam("testCaseId") String testCaseId) {
      // We may have testCaseId being URLEncoded, with forbidden '/' replaced by '_' so unwrap id.
      // Switched form _ to ! in replacement as less commonly used in URL parameters, as in other frameworks
      testCaseId = URLDecoder.decode(testCaseId, StandardCharsets.UTF_8);
      testCaseId = testCaseId.replace('!', '/');
      log.debug("Getting messages for testCase {} on test {}", testCaseId, testResultId);
      return messageService.getEventByTestCase(testCaseId);
   }

   @POST
   @Path("tests/{id}/testCaseResult")
   public Response reportTestCaseResult(@PathParam("id") String testResultId, TestCaseReturnDTO testCaseReturn) {
      log.debug("Reporting testCase results on test {}", testResultId);
      TestCaseResult testCaseResult = testService.reportTestCaseResult(testResultId, testCaseReturn.getOperationName(),
            testCaseReturn.getTestReturns());
      if (testCaseResult != null) {
         return Response.status(Response.Status.OK).entity(testCaseResult).build();
      }
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   /**
    * Build OperationsHeaders domain object from basic Map. Key is operation name, Value is a header data transfer
    * object.
    */
   private OperationsHeaders buildOperationsHeaders(Map<String, List<HeaderDTO>> operationsHeaders) {
      OperationsHeaders result = new OperationsHeaders();
      if (operationsHeaders != null) {
         // Now browse different operations (globals included).
         for (Map.Entry<String, List<HeaderDTO>> operationsHeadersEntry : operationsHeaders.entrySet()) {
            String operationName = operationsHeadersEntry.getKey();
            List<HeaderDTO> operationHeaders = operationsHeadersEntry.getValue();
            Set<Header> headers = new HashSet<>();
            // Browse each header entry. Values are comma separated.
            for (HeaderDTO operationHeadersEntry : operationHeaders) {
               String[] headerValues = operationHeadersEntry.getValues().split(",");
               Header header = new Header();
               header.setName(operationHeadersEntry.getName());
               header.setValues(new HashSet<>(Arrays.asList(headerValues)));
               headers.add(header);
            }
            result.put(operationName, headers);
         }
         return result;
      }
      return null;
   }
}
