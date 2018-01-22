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
package io.github.microcks.service;

import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.test.TestReturn;
import io.github.microcks.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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

   @Autowired
   private TestRunnerService testRunnerService;


   /**
    * Launch tests for a Service on dediated endpoint URI.
    * @param service Service to launch tests for
    * @param testEndpoint Endpoint URI for running the tests
    * @param runnerType The type of runner fo tests
    * @return An initialized TestResults (mostly empty for now since tests run asynchronously)
    */
   public TestResult launchTests(Service service, String testEndpoint, TestRunnerType runnerType){
      TestResult testResult = new TestResult();
      testResult.setTestDate(new Date());
      testResult.setTestedEndpoint(testEndpoint);
      testResult.setServiceId(service.getId());
      testResult.setRunnerType(runnerType);
      testResultRepository.save(testResult);

      // Launch test asynchronously before returning result.
      log.debug("Calling launchTestsInternal() marked as Async");
      testRunnerService.launchTestsInternal(testResult, service, runnerType);
      log.debug("Async launchTestsInternal() as now finished");
      return testResult;
   }

   /**
    * Endpoint for reporting test case results
    * @param testResultId Unique identifier of test results we report results for
    * @param operationName Name of operation to report a result for
    * @param testReturns List of test returns to add to this test case.
    * @return A completed TestCaseResult object
    */
   public TestCaseResult reportTestCaseResult(String testResultId, String operationName, List<TestReturn> testReturns) {
      log.info("Reporting a TestCaseResult for testResult {} on operation '{}'", testResultId, operationName);
      TestResult testResult = testResultRepository.findOne(testResultId);
      TestCaseResult updatedTestCaseResult = null;

      for (TestCaseResult testCaseResult : testResult.getTestCaseResults()) {
         // Find the testCaseResult matching operation name.
         if (testCaseResult.getOperationName().equals(operationName)) {
            updatedTestCaseResult = testCaseResult;
            if (testReturns == null || testReturns.isEmpty()) {
               testCaseResult.setElapsedTime(-1);
               testCaseResult.setSuccess(false);
            } else {
               String testCaseId = IdBuilder.buildTestCaseId(testResult, operationName);
               updateTestCaseResultWithReturns(testCaseResult, testReturns, testCaseId);
            }
         }
      }

      // Finally, Update success, progress indicators and total time before saving and returning.
      updateTestResult(testResult);
      return updatedTestCaseResult;
   }


   /**
    *
    */
   private void updateTestCaseResultWithReturns(TestCaseResult testCaseResult,
                                     List<TestReturn> testReturns, String testCaseId) {
      // Prepare a bunch of flag we're going to complete.
      boolean successFlag = true;
      long caseElapsedTime = 0;
      List<Response> responses = new ArrayList<Response>();
      List<Request> actualRequests = new ArrayList<Request>();

      for (TestReturn testReturn : testReturns) {
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
      log.debug("Saving {} responses with testCaseId {}", responses.size(), testCaseId);
      responseRepository.save(responses);

      // Associate responses to requests before saving requests.
      for (int i=0; i<actualRequests.size(); i++){
         actualRequests.get(i).setResponseId(responses.get(i).getId());
      }
      log.debug("Saving {} requests with testCaseId {}", responses.size(), testCaseId);
      requestRepository.save(actualRequests);

      // Update and save the completed TestCaseResult.
      // We cannot consider as success if we have no TestStepResults associated...
      if (testCaseResult.getTestStepResults().size() > 0) {
         testCaseResult.setSuccess(successFlag);
      }
      testCaseResult.setElapsedTime(caseElapsedTime);
   }

   /**
    *
    */
   private void updateTestResult(TestResult testResult) {
      // There may be a race condition while updating testResult at each testReturn report.
      // So be prepared to catch a org.springframework.dao.OptimisticLockingFailureException and retry
      // saving a bunch of time. Hopefully, we'll succeed. It does not matter if it takes time because
      // everything runs asynchronously.
      int times = 0;
      boolean saved = false;

      while (!saved && times < 5) {
         // Update success, progress indicators and total time before saving and returning.
         boolean globalSuccessFlag = true;
         boolean globalProgressFlag = false;
         long totalElapsedTime = 0;
         for (TestCaseResult testCaseResult : testResult.getTestCaseResults()) {
            totalElapsedTime += testCaseResult.getElapsedTime();
            if (!testCaseResult.isSuccess()) {
               globalSuccessFlag = false;
            }
            if (testCaseResult.getElapsedTime() == 0) {
               globalProgressFlag = true;
            }
         }

         // Update aggregated flags before saving whole testResult.
         testResult.setSuccess(globalSuccessFlag);
         testResult.setInProgress(globalProgressFlag);
         testResult.setElapsedTime(totalElapsedTime);

         try {
            log.debug("Trying to update testResult {}", testResult.getId());
            testResultRepository.save(testResult);
            saved = true;
         } catch (org.springframework.dao.OptimisticLockingFailureException olfe) {
            // Update counter and refresh domain object.
            log.debug("Caught an OptimisticLockingFailureException, trying refreshing for {} times", times);
            waitSomeRandomMS(5, 50);
            testResult = testResultRepository.findOne(testResult.getId());
            times++;
         }
      }
   }

   private void waitSomeRandomMS(int min, int max) {
      Object semaphore = new Object();
      long timeout = ThreadLocalRandom.current().nextInt(min, max + 1);
      synchronized (semaphore) {
         try {
            semaphore.wait(timeout);
         } catch (Exception e) {
            log.debug("waitSomeRandomMS semaphore was interrupted");
         }
      }
   }
}
