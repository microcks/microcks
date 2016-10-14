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
package com.github.lbroudoux.microcks.service;

import com.github.lbroudoux.microcks.domain.*;
import com.github.lbroudoux.microcks.repository.RequestRepository;
import com.github.lbroudoux.microcks.repository.ResponseRepository;
import com.github.lbroudoux.microcks.repository.TestResultRepository;
import com.github.lbroudoux.microcks.util.IdBuilder;
import com.github.lbroudoux.microcks.util.test.AbstractTestRunner;
import com.github.lbroudoux.microcks.util.test.HttpTestRunner;
import com.github.lbroudoux.microcks.util.test.TestReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author laurent
 */
@org.springframework.stereotype.Service
public class TestService {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(TestService.class);

   @Autowired
   private RequestRepository requestRepository;

   @Autowired
   private ResponseRepository responseRepository;

   @Autowired
   private TestResultRepository testResultRepository;

   /**
    *
    * @param service
    * @param testEndpoint
    * @param runnerType
    * @return
    */
   public TestResult launchTests(Service service, String testEndpoint, TestRunnerType runnerType){
      TestResult testResult = new TestResult();
      testResult.setTestDate(new Date());
      testResult.setTestedEndpoint(testEndpoint);
      testResult.setServiceId(service.getId());
      testResult.setRunnerType(runnerType);
      testResultRepository.save(testResult);

      // Launch test asynchronously before returning result.
      launchTests(testResult, service, runnerType);
      return testResult;
   }

   /**
    *
    * @param testResult
    * @param service
    * @param runnerType
    * @return
    */
   @Async
   private Future<TestResult> launchTests(TestResult testResult, Service service, TestRunnerType runnerType){
      // Found next build number for this test.
      List<TestResult> older = testResultRepository.findByServiceId(service.getId(), new PageRequest(0, 2, Sort.Direction.DESC, "testNumber"));
      if (older != null && !older.isEmpty() && older.get(0).getTestNumber() != null){
         testResult.setTestNumber(older.get(0).getTestNumber() + 1L);
      } else {
         testResult.setTestNumber(1L);
      }

      for (Operation operation : service.getOperations()){
         // Prepare result container for operation tests.
         TestCaseResult testCaseResult = new TestCaseResult();
         testCaseResult.setOperationName(operation.getName());
         String testCaseId = IdBuilder.buildTestCaseId(testResult, operation);

         // Prepare collection of requests to launch.
         List<Request> requests = requestRepository.findByOperationId(IdBuilder.buildOperationId(service, operation));
         requests = cloneRequestsForTestCase(requests, testCaseId);

         List<TestReturn> results = new ArrayList<TestReturn>();
         AbstractTestRunner<HttpMethod> testRunner = retrieveRunner(runnerType);
         try {
            HttpMethod method = testRunner.buildMethod(operation.getMethod());
            results = testRunner.runTest(service, operation, requests, testResult.getTestedEndpoint(), method);
         } catch (URISyntaxException use) {
            log.error("URISyntaxException on endpoint {}, aborting current tests",
                  testResult.getTestedEndpoint(), use);
            // Set flags and add to results before exiting loop.
            testCaseResult.setSuccess(false);
            testCaseResult.setElapsedTime(0);
            testResult.getTestCaseResults().add(testCaseResult);
            break;
         } catch (Throwable t) {
            log.error("Throwable while testing operation {}", operation.getName(),  t);
         }

         // Prepare a bunch of flag we're going to complete.
         boolean successFlag = true;
         long caseElapsedTime = 0;
         List<Response> responses = new ArrayList<Response>();
         List<Request> actualRequests = new ArrayList<Request>();

         // Iterate over test results to complete flags and extracted responses.
         for (TestReturn testReturn : results){
            // Deal with elapsed time and success flag.
            caseElapsedTime += testReturn.getElapsedTime();
            TestStepResult testStepResult = testReturn.buildTestStepResult();
            if (!testStepResult.isSuccess()){
               successFlag = false;
            }

            // Extract, complete and store response and request.
            testReturn.getResponse().setTestCaseId(testCaseId);
            testReturn.getRequest().setTestCaseId(testCaseId);
            responses.add(testReturn.getResponse());
            actualRequests.add(testReturn.getRequest());

            testCaseResult.getTestStepResults().add(testStepResult);
         }

         // Save the responses into repository to get their ids.
         responseRepository.save(responses);

         // Associate responses to requests before saving requests.
         for (int i=0; i<actualRequests.size(); i++){
            actualRequests.get(i).setResponseId(responses.get(i).getId());
         }
         requestRepository.save(actualRequests);

         // Update and save the completed TestCaseResult.
         testCaseResult.setSuccess(successFlag);
         testCaseResult.setElapsedTime(caseElapsedTime);
         testResult.getTestCaseResults().add(testCaseResult);
         testResultRepository.save(testResult);
      }

      // Update success, progress indicators and total time before saving and returning.
      boolean globalSuccessFlag = true;
      long totalElapsedTime = 0;
      for (TestCaseResult testCaseResult : testResult.getTestCaseResults()){
         totalElapsedTime += testCaseResult.getElapsedTime();
         if (!testCaseResult.isSuccess()){
            globalSuccessFlag = false;
         }
      }
      testResult.setInProgress(false);
      testResult.setSuccess(globalSuccessFlag);
      testResult.setElapsedTime(totalElapsedTime);
      testResultRepository.save(testResult);

      return new AsyncResult<>(testResult);
   }


   /** Clone and prepare request for a test case usage. */
   private List<Request> cloneRequestsForTestCase(List<Request> requests, String testCaseId){
      List<Request> result = new ArrayList<Request>();
      for (Request request : requests){
         Request clone = new Request();
         clone.setName(request.getName());
         clone.setContent(request.getContent());
         clone.setHeaders(request.getHeaders());
         clone.setQueryParameters(request.getQueryParameters());
         // Assign testCaseId.
         clone.setTestCaseId(testCaseId);
         result.add(clone);
      }
      return result;
   }

   /** Retrieve correct test runner according given type. */
   private AbstractTestRunner<HttpMethod> retrieveRunner(TestRunnerType runnerType){
      // TODO: remove this ugly initialization later.
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(200);
      factory.setReadTimeout(10000);

      switch (runnerType){
         case SOAP_HTTP:
         case SOAP_UI:
         default:
            HttpTestRunner runner = new HttpTestRunner();
            runner.setClientHttpRequestFactory(factory);
            return runner;
      }
   }
}
