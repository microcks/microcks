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

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestCaseResult;
import io.github.microcks.domain.TestOptionals;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.domain.TestRunnerType;
import io.github.microcks.domain.TestStepResult;
import io.github.microcks.event.TestCompletionEvent;
import io.github.microcks.repository.EventMessageRepository;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.util.IdBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bean defining service operations around Test domain objects.
 * @author laurent
 */
@org.springframework.stereotype.Service
public class TestService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(TestService.class);

   private final RequestRepository requestRepository;
   private final ResponseRepository responseRepository;
   private final EventMessageRepository eventMessageRepository;
   private final TestResultRepository testResultRepository;
   private final TestRunnerService testRunnerService;
   private final ApplicationContext applicationContext;

   /**
    * Build a new TestService with the required dependencies.
    * @param requestRepository      The repository to manage persistent requests
    * @param responseRepository     The repository to manage persistent responses
    * @param eventMessageRepository The repository to manage persistent eventMessages
    * @param testResultRepository   The repository to manage persistent testResults
    * @param testRunnerService      The service for running tests
    * @param applicationContext     The Spring application context
    */
   public TestService(RequestRepository requestRepository, ResponseRepository responseRepository,
         EventMessageRepository eventMessageRepository, TestResultRepository testResultRepository,
         TestRunnerService testRunnerService, ApplicationContext applicationContext) {
      this.requestRepository = requestRepository;
      this.responseRepository = responseRepository;
      this.eventMessageRepository = eventMessageRepository;
      this.testResultRepository = testResultRepository;
      this.testRunnerService = testRunnerService;
      this.applicationContext = applicationContext;
   }

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
      AtomicReference<TestCaseResult> updatedTestCaseResult = new AtomicReference<>();

      // This part can be done safely with no race condition because we only
      // record new requests/responses corresponding to testReturns.
      // So just find the correct testCase to build a suitable id and then createTestReturns.

      if (testReturns != null && !testReturns.isEmpty()) {
         TestResult finalTestResult = testResult;
         testResult.getTestCaseResults().stream().filter(tcr -> tcr.getOperationName().equals(operationName))
               .findFirst().ifPresent(tcr -> {
                  String testCaseId = IdBuilder.buildTestCaseId(finalTestResult, operationName);
                  createTestReturns(testReturns, testCaseId);
               });
      }

      // There may be a race condition while updating testResult at each testReturn report.
      // So be prepared to catch a org.springframework.dao.OptimisticLockingFailureException and retry
      // saving a bunch of time. Hopefully, we'll succeed. It does not matter if it takes time because
      // everything runs asynchronously.
      int times = 0;
      boolean saved = false;

      while (!saved && times < 5) {

         TestResult finalTestResult1 = testResult;
         testResult.getTestCaseResults().stream().filter(tcr -> tcr.getOperationName().equals(operationName))
               .findFirst().ifPresent(tcr -> {
                  updatedTestCaseResult.set(tcr);
                  // If results we now update the success flag and elapsed time of testCase?
                  if (testReturns == null || testReturns.isEmpty()) {
                     log.info("testReturns are null or empty, setting elapsedTime to -1 and success to false for {}",
                           operationName);
                     tcr.setElapsedTime(-1);
                     tcr.setSuccess(false);
                  } else {
                     updateTestCaseResultWithReturns(tcr, testReturns,
                           TestRunnerType.ASYNC_API_SCHEMA != finalTestResult1.getRunnerType(),
                           TestRunnerType.ASYNC_API_SCHEMA == finalTestResult1.getRunnerType());
                  }
               });

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
      return updatedTestCaseResult.get();
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

   // Suppress SonarCloud warning about catching InterruptedException: we absolutely need not propagate it to not lose data.
   @SuppressWarnings("java:S2142")
   private void waitSomeRandomMS(int min, int max) {
      long timeout = ThreadLocalRandom.current().nextInt(min, max + 1);
      try {
         Thread.sleep(Duration.ofMillis(timeout));
      } catch (Exception e) {
         log.debug("waitSomeRandomMS semaphore was interrupted");
      }
   }

   /** Publish a TestCompletionEvent towards asynchronous consumers. */
   private void publishTestCompletionEvent(TestResult testResult) {
      TestCompletionEvent event = new TestCompletionEvent(this, testResult);
      applicationContext.publishEvent(event);
      log.debug("Test completion event has been published");
   }
}
