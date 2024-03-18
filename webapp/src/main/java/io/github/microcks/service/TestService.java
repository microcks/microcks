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
package io.github.microcks.service;

import io.github.microcks.domain.*;
import io.github.microcks.event.TestCompletionEvent;
import io.github.microcks.repository.EventMessageRepository;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.util.IdBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Bean defining service operations around Test domain objects.
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
   private EventMessageRepository eventMessageRepository;

   @Autowired
   private TestResultRepository testResultRepository;

   @Autowired
   private TestRunnerService testRunnerService;

   @Autowired
   private ApplicationContext applicationContext;

   /**
    * Launch tests for a Service on dedicated endpoint URI.
    * @param service       Service to launch tests for
    * @param testEndpoint  Endpoint URI for running the tests
    * @param runnerType    The type of runner fo tests
    * @param testOptionals Additional / optionals elements for test
    * @return An initialized TestResults (mostly empty for now since tests run asynchronously)
    */
   public TestResult launchTests(Service service, String testEndpoint, TestRunnerType runnerType,
         TestOptionals testOptionals) {
      TestResult testResult = new TestResult();
      testResult.setTestDate(new Date());
      testResult.setTestedEndpoint(testEndpoint);
      testResult.setServiceId(service.getId());
      testResult.setRunnerType(runnerType);
      testResult.setTimeout(testOptionals.getTimeout());
      testResult.setSecretRef(testOptionals.getSecretRef());
      testResult.setOperationsHeaders(testOptionals.getOperationsHeaders());

      // Initialize the TestCaseResults containers before saving it.
      initializeTestCaseResults(testResult, service, testOptionals);
      testResultRepository.save(testResult);

      // Launch test asynchronously before returning result.
      log.debug("Calling launchTestsInternal() marked as Async");
      testRunnerService.launchTestsInternal(testResult, service, runnerType, testOptionals.getOAuth2Context());
      log.debug("Async launchTestsInternal() as now finished");
      return testResult;
   }

   /**
    * Endpoint for reporting test case results
    * @param testResultId  Unique identifier of test results we report results for
    * @param operationName Name of operation to report a result for
    * @param testReturns   List of test returns to add to this test case.
    * @return A completed TestCaseResult object
    */
   public TestCaseResult reportTestCaseResult(String testResultId, String operationName, List<TestReturn> testReturns) {
      log.info("Reporting a TestCaseResult for testResult {} on operation '{}'", testResultId, operationName);
      TestResult testResult = testResultRepository.findById(testResultId).orElse(null);
      TestCaseResult updatedTestCaseResult = null;

      // This part can be done safely with no race condition because we only
      // record new requests/responses corresponding to testReturns.
      // So just find the correct testCase to build a suitable id and then createTestReturns.

      for (TestCaseResult testCaseResult : testResult.getTestCaseResults()) {
         // Ensure we have a testCaseResult matching operation name.
         if (testCaseResult.getOperationName().equals(operationName)) {
            // If results, we need to create requests/responses pairs and associate them to testCase.
            if (testReturns != null && !testReturns.isEmpty()) {
               String testCaseId = IdBuilder.buildTestCaseId(testResult, operationName);
               createTestReturns(testReturns, testCaseId);
            }
            break;
         }
      }

      // There may be a race condition while updating testResult at each testReturn report.
      // So be prepared to catch a org.springframework.dao.OptimisticLockingFailureException and retry
      // saving a bunch of time. Hopefully, we'll succeed. It does not matter if it takes time because
      // everything runs asynchronously.
      int times = 0;
      boolean saved = false;

      while (!saved && times < 5) {

         for (TestCaseResult testCaseResult : testResult.getTestCaseResults()) {
            // Ensure we have a testCaseResult matching operation name.
            if (testCaseResult.getOperationName().equals(operationName)) {
               updatedTestCaseResult = testCaseResult;
               // If results we now update the success flag and elapsed time of testCase?
               if (testReturns == null || testReturns.isEmpty()) {
                  log.info("testReturns are null or empty, setting elapsedTime to -1 and success to false for {}",
                        operationName);
                  testCaseResult.setElapsedTime(-1);
                  testCaseResult.setSuccess(false);
               } else {
                  updateTestCaseResultWithReturns(testCaseResult, testReturns,
                        TestRunnerType.ASYNC_API_SCHEMA != testResult.getRunnerType(),
                        TestRunnerType.ASYNC_API_SCHEMA == testResult.getRunnerType());
               }
               break;
            }
         }

         // Finally, update success, progress indicators and total time before saving and returning.
         try {
            updateTestResult(testResult);
            saved = true;
            log.debug("testResult {} has been updated !", testResult.getId());
            // If test is completed, publish a completion event.
            if (!testResult.isInProgress()) {
               publishTestCompletionEvent(testResult);
            }
         } catch (org.springframework.dao.OptimisticLockingFailureException olfe) {
            // Update counter and refresh domain object.
            log.warn("Caught an OptimisticLockingFailureException, trying refreshing for {} times", times);
            saved = false;
            waitSomeRandomMS(5, 50);
            testResult = testResultRepository.findById(testResult.getId()).orElse(null);
            times++;
         }
      }
      return updatedTestCaseResult;
   }

   /** */
   private void initializeTestCaseResults(TestResult testResult, Service service, TestOptionals testOptionals) {
      for (Operation operation : service.getOperations()) {
         // Pick operation if no filter or present in filtered operations.
         if (testOptionals.getFilteredOperations() == null || testOptionals.getFilteredOperations().isEmpty()
               || testOptionals.getFilteredOperations().contains(operation.getName())) {
            TestCaseResult testCaseResult = new TestCaseResult();
            testCaseResult.setOperationName(operation.getName());
            testResult.getTestCaseResults().add(testCaseResult);
         }
      }
   }

   /** */
   private void createTestReturns(List<TestReturn> testReturns, String testCaseId) {
      List<Response> responses = new ArrayList<>();
      List<Request> actualRequests = new ArrayList<>();
      List<EventMessage> eventMessages = new ArrayList<>();

      for (TestReturn testReturn : testReturns) {
         if (testReturn.isRequestResponseTest()) {
            // Extract, complete and store response and request.
            testReturn.getResponse().setTestCaseId(testCaseId);
            testReturn.getRequest().setTestCaseId(testCaseId);
            responses.add(testReturn.getResponse());
            actualRequests.add(testReturn.getRequest());
         } else if (testReturn.isEventTest()) {
            // Complete and store event messages for tracking testCaseId.
            testReturn.getEventMessage().setTestCaseId(testCaseId);
            eventMessages.add(testReturn.getEventMessage());
         }
      }

      if (!responses.isEmpty() && !actualRequests.isEmpty()) {
         // Save the responses into repository to get their ids.
         log.debug("Saving {} responses with testCaseId {}", responses.size(), testCaseId);
         responseRepository.saveAll(responses);

         // Associate responses to requests before saving requests.
         for (int i = 0; i < actualRequests.size(); i++) {
            actualRequests.get(i).setResponseId(responses.get(i).getId());
         }
         log.debug("Saving {} requests with testCaseId {}", responses.size(), testCaseId);
         requestRepository.saveAll(actualRequests);
      }

      if (!eventMessages.isEmpty()) {
         // Save the eventMessages into repository.
         log.debug("Saving {} eventMessages with testCaseId {}", eventMessages.size(), testCaseId);
         eventMessageRepository.saveAll(eventMessages);
      }
   }

   /**
    *
    */
   private void updateTestCaseResultWithReturns(TestCaseResult testCaseResult, List<TestReturn> testReturns,
         boolean sumElapsedTimes, boolean findMaxElapsedTime) {

      // Prepare a bunch of flag we're going to complete.
      boolean successFlag = true;
      long caseElapsedTime = 0;

      for (TestReturn testReturn : testReturns) {
         // Deal with elapsed time and success flag.
         if (sumElapsedTimes) {
            caseElapsedTime += testReturn.getElapsedTime();
         } else if (findMaxElapsedTime) {
            if (testReturn.getElapsedTime() > caseElapsedTime) {
               caseElapsedTime = testReturn.getElapsedTime();
            }
         }
         TestStepResult testStepResult = testReturn.buildTestStepResult();
         if (!testStepResult.isSuccess()) {
            successFlag = false;
         }

         // Add testStepResult to testCase.
         testCaseResult.getTestStepResults().add(testStepResult);
      }

      // Update and save the completed TestCaseResult.
      // We cannot consider as success if we have no TestStepResults associated...
      if (!testCaseResult.getTestStepResults().isEmpty()) {
         testCaseResult.setSuccess(successFlag);
      }
      testCaseResult.setElapsedTime(caseElapsedTime);
      log.debug("testCaseResult for {} have been updated with {} elapsedTime and success flag to {}",
            testCaseResult.getOperationName(), testCaseResult.getElapsedTime(), testCaseResult.isSuccess());
   }

   /**
    *
    */
   private void updateTestResult(TestResult testResult) {
      // Update success, progress indicators and total time before saving and returning.
      boolean globalSuccessFlag = true;
      boolean globalProgressFlag = false;
      long totalElapsedTime = 0;
      for (TestCaseResult testCaseResult : testResult.getTestCaseResults()) {
         totalElapsedTime += testCaseResult.getElapsedTime();
         if (!testCaseResult.isSuccess()) {
            globalSuccessFlag = false;
         }
         // -1 is default elapsed time for testcase so it means that still in
         // progress because not updated yet.
         if (testCaseResult.getElapsedTime() == -1) {
            log.debug("testCaseResult.elapsedTime is -1, set globalProgressFlag to true");
            globalProgressFlag = true;
         }
      }

      // Update aggregated flags before saving whole testResult.
      testResult.setSuccess(globalSuccessFlag);
      testResult.setInProgress(globalProgressFlag);
      testResult.setElapsedTime(totalElapsedTime);

      log.debug("Trying to update testResult {} with {} elapsedTime and success flag to {}", testResult.getId(),
            testResult.getElapsedTime(), testResult.isSuccess());
      testResultRepository.save(testResult);
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

   /** Publish a TestCompletionEvent towards asynchronous consumers. */
   private void publishTestCompletionEvent(TestResult testResult) {
      TestCompletionEvent event = new TestCompletionEvent(this, testResult);
      applicationContext.publishEvent(event);
      log.debug("Test completion event has been published");
   }
}
