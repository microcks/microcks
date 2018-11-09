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

import io.github.microcks.domain.*;
import io.github.microcks.repository.*;
import io.github.microcks.util.HTTPDownloader;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.openapi.OpenAPITestRunner;
import io.github.microcks.util.postman.PostmanTestStepsRunner;
import io.github.microcks.util.soapui.SoapUITestStepsRunner;
import io.github.microcks.util.test.AbstractTestRunner;
import io.github.microcks.util.test.HttpTestRunner;
import io.github.microcks.util.test.SoapHttpTestRunner;
import io.github.microcks.util.test.TestReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author laurent
 */
@org.springframework.stereotype.Service
public class TestRunnerService {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(TestRunnerService.class);

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private RequestRepository requestRepository;

   @Autowired
   private ResponseRepository responseRepository;

   @Autowired
   private TestResultRepository testResultRepository;

   @Autowired
   private ImportJobRepository jobRepository;

   @Autowired
   private SecretRepository secretRepository;

   @Value("${tests-callback.url}")
   private final String testsCallbackUrl = null;

   @Value("${postman-runner.url}")
   private final String postmanRunnerUrl = null;

   @Value("${validation.resourceUrl}")
   private final String validationResourceUrl = null;

   /**
    *
    * @param testResult TestResults to aggregate results within
    * @param service Service to test
    * @param runnerType Type of runner for launching the tests
    * @return A Future wrapping test results
    */
   @Async
   public CompletableFuture<TestResult> launchTestsInternal(TestResult testResult, Service service, TestRunnerType runnerType){
      // Found next build number for this test.
      List<TestResult> older = testResultRepository.findByServiceId(service.getId(), new PageRequest(0, 2, Sort.Direction.DESC, "testNumber"));
      if (older != null && !older.isEmpty() && older.get(0).getTestNumber() != null){
         testResult.setTestNumber(older.get(0).getTestNumber() + 1L);
      } else {
         testResult.setTestNumber(1L);
      }

      for (Operation operation : service.getOperations()) {
         // Prepare result container for operation tests.
         TestCaseResult testCaseResult = new TestCaseResult();
         testCaseResult.setOperationName(operation.getName());
         String testCaseId = IdBuilder.buildTestCaseId(testResult, operation);
         testResult.getTestCaseResults().add(testCaseResult);
         testResultRepository.save(testResult);

         // Prepare collection of requests to launch.
         List<Request> requests = requestRepository.findByOperationId(IdBuilder.buildOperationId(service, operation));
         requests = cloneRequestsForTestCase(requests, testCaseId);

         List<TestReturn> results = new ArrayList<TestReturn>();
         AbstractTestRunner<HttpMethod> testRunner = retrieveRunner(runnerType, service.getId());
         try {
            HttpMethod method = testRunner.buildMethod(operation.getMethod());
            results = testRunner.runTest(service, operation, testResult, requests, testResult.getTestedEndpoint(), method);
         } catch (URISyntaxException use) {
            log.error("URISyntaxException on endpoint {}, aborting current tests",
                  testResult.getTestedEndpoint(), use);
            // Set flags and add to results before exiting loop.
            testCaseResult.setSuccess(false);
            testCaseResult.setElapsedTime(0);
            testResultRepository.save(testResult);
            break;
         } catch (Throwable t) {
            log.error("Throwable while testing operation {}", operation.getName(), t);
         }

         // Update result if we got returns. If no returns, it means that there's no
         // sample request for that operation -> mark it as failed.
         if (results == null) {
            testCaseResult.setSuccess(false);
            testCaseResult.setElapsedTime(0);
            testResultRepository.save(testResult);
         } else if (!results.isEmpty()) {
            updateTestCaseResultWithReturns(testCaseResult, results, testCaseId);
            testResultRepository.save(testResult);
         }
      }

      // Update success, progress indicators and total time before saving and returning.
      updateTestResult(testResult);

      return CompletableFuture.completedFuture(testResult);
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
      log.debug("Updating total testResult");
      // Update success, progress indicators and total time before saving and returning.
      boolean globalSuccessFlag = true;
      boolean globalProgressFlag = false;
      long totalElapsedTime = 0;
      for (TestCaseResult testCaseResult : testResult.getTestCaseResults()){
         totalElapsedTime += testCaseResult.getElapsedTime();
         if (!testCaseResult.isSuccess()){
            globalSuccessFlag = false;
         }
         // -1 is default elapsed time for testcase so its mean that still in
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

      testResultRepository.save(testResult);
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
   private AbstractTestRunner<HttpMethod> retrieveRunner(TestRunnerType runnerType, String serviceId){
      // TODO: remove this ugly initialization later.
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(200);
      factory.setReadTimeout(10000);

      switch (runnerType){
         case SOAP_HTTP:
            SoapHttpTestRunner soapRunner = new SoapHttpTestRunner();
            soapRunner.setClientHttpRequestFactory(factory);
            soapRunner.setResourceUrl(validationResourceUrl);
            return soapRunner;
         case OPEN_API_SCHEMA:
            OpenAPITestRunner openApiRunner = new OpenAPITestRunner(resourceRepository, responseRepository, false);
            openApiRunner.setClientHttpRequestFactory(factory);
            return openApiRunner;
         case SOAP_UI:
            // Handle local download of correct project file.
            List<ImportJob> jobs = jobRepository.findByServiceRefId(serviceId);
            if (jobs != null && !jobs.isEmpty()) {
               try {
                  String projectFile = handleJobRepositoryDownloadToFile(jobs.get(0));
                  SoapUITestStepsRunner soapUIRunner = new SoapUITestStepsRunner(projectFile);
                  return soapUIRunner;
               } catch (IOException ioe) {
                  log.error("IOException while downloading {}", jobs.get(0).getRepositoryUrl());
               }
            }
         case POSTMAN:
            // Handle local download of correct project file.
            jobs = jobRepository.findByServiceRefId(serviceId);
            if (jobs != null && !jobs.isEmpty()) {
               try {
                  String collectionFile = handleJobRepositoryDownloadToFile(jobs.get(0));
                  PostmanTestStepsRunner postmanRunner = new PostmanTestStepsRunner(collectionFile);
                  postmanRunner.setClientHttpRequestFactory(factory);
                  postmanRunner.setTestsCallbackUrl(testsCallbackUrl);
                  postmanRunner.setPostmanRunnerUrl(postmanRunnerUrl);
                  return postmanRunner;
               } catch (IOException ioe) {
                  log.error("IOException while downloading {}", jobs.get(0).getRepositoryUrl());
               }
            }
         default:
            HttpTestRunner httpRunner = new HttpTestRunner();
            httpRunner.setClientHttpRequestFactory(factory);
            return httpRunner;
      }
   }

   /** Download a remote HTTP URL repository into a temporary local file. */
   private String handleJobRepositoryDownloadToFile(ImportJob job) throws IOException {
      // Check if job has an associated secret to retrieve.
      Secret jobSecret = null;
      if (job.getSecretRef() != null) {
         log.debug("Retrieving secret {} for job {}", job.getSecretRef().getName(), job.getName());
         jobSecret = secretRepository.findOne(job.getSecretRef().getSecretId());
      }

      File localFile = HTTPDownloader.handleHTTPDownloadToFile(job.getRepositoryUrl(),
            jobSecret, job.isRepositoryDisableSSLValidation());
      return localFile.getAbsolutePath();
   }
}
