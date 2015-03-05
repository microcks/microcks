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
package com.github.lbroudoux.microcks.web;

import com.github.lbroudoux.microcks.domain.Service;
import com.github.lbroudoux.microcks.domain.TestResult;
import com.github.lbroudoux.microcks.domain.TestRunnerType;
import com.github.lbroudoux.microcks.repository.ServiceRepository;
import com.github.lbroudoux.microcks.repository.TestResultRepository;
import com.github.lbroudoux.microcks.service.MessageService;
import com.github.lbroudoux.microcks.service.RequestResponsePair;
import com.github.lbroudoux.microcks.service.TestService;
import com.github.lbroudoux.microcks.web.dto.TestRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
      log.debug("Getting messages for testCase {} on test {}", testCaseId, testResultId);
      return messageService.getRequestResponseByTestCase(testCaseId);
   }
}
