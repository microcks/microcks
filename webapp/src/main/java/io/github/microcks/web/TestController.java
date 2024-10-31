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

import io.github.microcks.domain.Header;
import io.github.microcks.domain.OperationsHeaders;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Secret;
import io.github.microcks.domain.SecretRef;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestCaseResult;
import io.github.microcks.domain.TestOptionals;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestRunnerType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.repository.SecretRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.service.MessageService;
import io.github.microcks.service.ServiceService;
import io.github.microcks.service.TestService;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.web.dto.HeaderDTO;
import io.github.microcks.web.dto.TestCaseReturnDTO;
import io.github.microcks.web.dto.TestRequestDTO;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
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
   public TestController(TestService testService, MessageService messageService, ServiceService serviceService,
         TestResultRepository testResultRepository, SecretRepository secretRepository) {
      this.testService = testService;
      this.messageService = messageService;
      this.serviceService = serviceService;
      this.testResultRepository = testResultRepository;
      this.secretRepository = secretRepository;
   }

   @GetMapping(value = "/tests/service/{serviceId}")
   public List<TestResult> listTestsByService(@PathVariable("serviceId") String serviceId,
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
      log.debug("Getting tests list for service {}, page {} and size {}", serviceId, page, size);
      return testResultRepository.findByServiceId(serviceId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "testNumber")));
   }

   @GetMapping(value = "/tests/service/{serviceId}/count")
   public Map<String, Long> countTestsByService(@PathVariable("serviceId") String serviceId) {
      log.debug("Counting tests for service...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", testResultRepository.countByServiceId(serviceId));
      return counter;
   }

   @PostMapping(value = "/tests")
   public ResponseEntity<TestResult> createTest(@RequestBody TestRequestDTO test) {
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
            secretRef = new SecretRef(secrets.getFirst().getId(), secrets.getFirst().getName());
         }
         // TODO: should we return an error and refuse creating the test without secret ?
      }

      TestOptionals testOptionals = new TestOptionals(secretRef, test.getTimeout(), test.getFilteredOperations(),
            operationsHeaders, test.getOAuth2Context());
      TestResult testResult = testService.launchTests(service, test.getTestEndpoint(), testRunner, testOptionals);
      return new ResponseEntity<>(testResult, HttpStatus.CREATED);
   }

   @GetMapping(value = "/tests/{id}")
   public ResponseEntity<TestResult> getTestResult(@PathVariable("id") String testResultId) {
      log.debug("Getting TestResult with id {}", testResultId);
      return new ResponseEntity<>(testResultRepository.findById(testResultId).orElse(null), HttpStatus.OK);
   }

   @GetMapping(value = "tests/{id}/messages/{testCaseId}")
   public List<RequestResponsePair> getMessagesForTestCase(@PathVariable("id") String testResultId,
         @PathVariable("testCaseId") String testCaseId) {
      // We may have testCaseId being URLEncoded, with forbidden '/' replaced by '_' so unwrap id.
      // Switched form _ to ! in replacement as less commonly used in URL parameters, in line with other frameworks e.g. Drupal
      testCaseId = URLDecoder.decode(testCaseId, StandardCharsets.UTF_8);
      testCaseId = testCaseId.replace('!', '/');
      log.debug("Getting messages for testCase {} on test {}", testCaseId, testResultId);
      return messageService.getRequestResponseByTestCase(testCaseId);
   }

   @GetMapping(value = "tests/{id}/events/{testCaseId}")
   public List<UnidirectionalEvent> getEventMessagesForTestCase(@PathVariable("id") String testResultId,
         @PathVariable("testCaseId") String testCaseId) {
      // We may have testCaseId being URLEncoded, with forbidden '/' replaced by '_' so unwrap id.
      // Switched form _ to ! in replacement as less commonly used in URL parameters, in line with other frameworks e.g. Drupal
      testCaseId = URLDecoder.decode(testCaseId, StandardCharsets.UTF_8);
      testCaseId = testCaseId.replace('!', '/');
      log.debug("Getting messages for testCase {} on test {}", testCaseId, testResultId);
      return messageService.getEventByTestCase(testCaseId);
   }

   @PostMapping(value = "tests/{id}/testCaseResult")
   public ResponseEntity<TestCaseResult> reportTestCaseResult(@PathVariable("id") String testResultId,
         @RequestBody TestCaseReturnDTO testCaseReturn) {
      log.debug("Reporting testCase results on test {}", testResultId);
      TestCaseResult testCaseResult = testService.reportTestCaseResult(testResultId, testCaseReturn.getOperationName(),
            testCaseReturn.getTestReturns());
      if (testCaseResult != null) {
         return new ResponseEntity<>(testCaseResult, HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
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
