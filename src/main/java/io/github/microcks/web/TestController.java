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

import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestCaseResult;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestRunnerType;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.service.MessageService;
import io.github.microcks.service.RequestResponsePair;
import io.github.microcks.service.TestService;
import io.github.microcks.web.dto.TestCaseReturnDTO;
import io.github.microcks.web.dto.TestRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Rest controller for API defined on test results.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class TestController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(TestController.class);

   @Autowired
   private TestResultRepository testResultRepository;

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private TestService testService;

   @Autowired
   private MessageService messageService;


   @RequestMapping(value = "/tests/service/{serviceId}", method = RequestMethod.GET)
   public List<TestResult> listTestsByService(
         @PathVariable("serviceId") String serviceId,
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size
      ) {
      log.debug("Getting tests list for service {}, page {} and size {}", serviceId, page, size);
      return testResultRepository.findByServiceId(serviceId,
            new PageRequest(page, size, new Sort(Sort.Direction.DESC, "testNumber")));
   }

   @RequestMapping(value = "/tests/service/{serviceId}/count", method = RequestMethod.GET)
   public Map<String, Long> countTestsByService(
         @PathVariable("serviceId") String serviceId
      ) {
      log.debug("Counting tests for service...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", testResultRepository.countByServiceId(serviceId));
      return counter;
   }

   @RequestMapping(value = "/tests", method = RequestMethod.POST)
   public ResponseEntity<TestResult> createTest(@RequestBody TestRequestDTO test) {
      log.debug("Creating new test for {} on endpoint {}", test.getServiceId(), test.getTestEndpoint());
      Service service = serviceRepository.findOne(test.getServiceId());
      TestRunnerType testRunner = TestRunnerType.valueOf(test.getRunnerType());
      TestResult testResult = testService.launchTests(service, test.getTestEndpoint(), testRunner);
      return new ResponseEntity<TestResult>(testResult, HttpStatus.CREATED);
   }

   @RequestMapping(value = "/tests/{id}", method = RequestMethod.GET)
   public ResponseEntity<TestResult> getTestResult(@PathVariable("id") String testResultId) {
      log.debug("Getting TestResult with id {}", testResultId);
      return new ResponseEntity<>(testResultRepository.findOne(testResultId), HttpStatus.OK);
   }

   @RequestMapping(value = "tests/{id}/messages/{testCaseId}", method = RequestMethod.GET)
   public List<RequestResponsePair> getMessagesForTestCase(
         @PathVariable("id") String testResultId,
         @PathVariable("testCaseId") String testCaseId
      ) {
      // We may have testCaseId being URLEncoded, with forbidden '/' replaced by '_' so unwrap id.
      try {
         testCaseId = URLDecoder.decode(testCaseId, StandardCharsets.UTF_8.toString());
         testCaseId = testCaseId.replace('_', '/');
      } catch (UnsupportedEncodingException e) {
         return null;
      }
      log.debug("Getting messages for testCase {} on test {}", testCaseId, testResultId);
      return messageService.getRequestResponseByTestCase(testCaseId);
   }

   @RequestMapping(value = "tests/{id}/testCaseResult", method = RequestMethod.POST)
   public ResponseEntity<TestCaseResult> reportTestCaseResult(
         @PathVariable("id") String testResultId,
         @RequestBody TestCaseReturnDTO testCaseReturn
         ) {
      log.debug("Reporting testCase results on test {}", testResultId);
      TestCaseResult testCaseResult = testService.reportTestCaseResult(
            testResultId, testCaseReturn.getOperationName(), testCaseReturn.getTestReturns());
      if (testCaseResult != null) {
         return new ResponseEntity<>(testCaseResult, HttpStatus.OK);
      }
      return new ResponseEntity<TestCaseResult>(HttpStatus.BAD_REQUEST);
   }
}
