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

import io.github.microcks.domain.ImportJob;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Secret;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestCaseResult;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.domain.TestRunnerType;
import io.github.microcks.domain.TestStepResult;
import io.github.microcks.repository.ImportJobRepository;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.SecretRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.util.HTTPDownloader;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.asyncapi.AsyncAPITestRunner;
import io.github.microcks.util.grpc.GrpcTestRunner;
import io.github.microcks.util.openapi.OpenAPITestRunner;
import io.github.microcks.util.postman.PostmanTestStepsRunner;
import io.github.microcks.util.soapui.SoapUITestStepsRunner;
import io.github.microcks.util.test.AbstractTestRunner;
import io.github.microcks.util.test.HttpTestRunner;
import io.github.microcks.util.test.SoapHttpTestRunner;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Bean managing the launch of new Tests.
 * @author laurent
 */
@org.springframework.stereotype.Service
public class TestRunnerService {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(TestRunnerService.class);

   /** Constant representing the header line in a custom CA Cert in PEM format. */
   private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
   /** Constant representing the footer line in a custom CA Cert in PEM format. */
   private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

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

   @Value("${async-minion.url}")
   private final String asyncMinionUrl = null;

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
      List<TestResult> older = testResultRepository.findByServiceId(service.getId(), PageRequest.of(0, 2, Sort.Direction.DESC, "testNumber"));
      if (older != null && !older.isEmpty() && older.get(0).getTestNumber() != null){
         testResult.setTestNumber(older.get(0).getTestNumber() + 1L);
      } else {
         testResult.setTestNumber(1L);
      }

      Secret secret = null;
      if (testResult.getSecretRef() != null) {
         secret = secretRepository.findById(testResult.getSecretRef().getSecretId()).orElse(null);
         log.debug("Using a secret to test endpoint? '{}'", secret != null ? secret.getName() : "none");
      }

      // Initialize runner once as it is shared for each test.
      AbstractTestRunner<HttpMethod> testRunner = retrieveRunner(runnerType, secret, testResult.getTimeout(), service.getId());
      if (testRunner == null) {
         // Set failure and stopped flags and save before exiting.
         testResult.setSuccess(false);
         testResult.setInProgress(false);
         testResult.setElapsedTime(0);
         testResultRepository.save(testResult);
         return CompletableFuture.completedFuture(testResult);
      }

      for (TestCaseResult testCaseResult : testResult.getTestCaseResults()) {
         // Retrieve operation corresponding to testCase.
         Operation operation = service.getOperations().stream()
               .filter(o -> o.getName().equals(testCaseResult.getOperationName())).findFirst().get();
         String testCaseId = IdBuilder.buildTestCaseId(testResult, operation);

         // Prepare collection of requests to launch.
         List<Request> requests = requestRepository.findByOperationId(IdBuilder.buildOperationId(service, operation));
         requests = cloneRequestsForTestCase(requests, testCaseId);

         List<TestReturn> results = new ArrayList<>();
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
      List<Response> responses = new ArrayList<>();
      List<Request> actualRequests = new ArrayList<>();

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
      responseRepository.saveAll(responses);

      // Associate responses to requests before saving requests.
      for (int i=0; i<actualRequests.size(); i++){
         actualRequests.get(i).setResponseId(responses.get(i).getId());
      }
      log.debug("Saving {} requests with testCaseId {}", responses.size(), testCaseId);
      requestRepository.saveAll(actualRequests);

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
      List<Request> result = new ArrayList<>();
      for (Request request : requests){
         Request clone = new Request();
         clone.setName(request.getName());
         clone.setContent(request.getContent());
         clone.setHeaders(request.getHeaders());
         clone.setQueryParameters(request.getQueryParameters());
         clone.setResponseId(request.getResponseId());
         // Assign testCaseId.
         clone.setTestCaseId(testCaseId);
         result.add(clone);
      }
      return result;
   }

   /** Retrieve correct test runner according given type. */
   private AbstractTestRunner<HttpMethod> retrieveRunner(TestRunnerType runnerType, Secret secret, Long runnerTimeout, String serviceId){
      // TODO: remove this ugly initialization later.
      // Initialize new HttpComponentsClientHttpRequestFactory that supports https connections.
      SSLContext sslContext = null;
      try {
         // Initialize trusting material depending on Secret content.
         if (secret != null && secret.getCaCertPem() != null) {
            log.debug("Test Secret contains a CA Cert, installing certificate into SSLContext");
            sslContext = SSLContexts.custom().loadTrustMaterial(
                  buildCustomCaCertTruststore(secret.getCaCertPem()),
                  null).build();
         } else {
            log.debug("No Test Secret or no CA Cert found, installing accept everything strategy");
            sslContext = SSLContexts.custom().loadTrustMaterial(
                  null,
                  (cert, authType) -> true).build();
         }

      } catch (Exception e) {
         log.error("Exception while building SSLContext with acceptingTrustStrategy", e);
         return null;
      }

      SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);
      Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
                  .register("https", sslsf)
                  .register("http", new PlainConnectionSocketFactory())
                  .build();

      BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry);
      CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf)
            .setConnectionManager(connectionManager).build();

      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
      factory.setConnectTimeout(200);
      factory.setReadTimeout(runnerTimeout.intValue());

      switch (runnerType){
         case SOAP_HTTP:
            SoapHttpTestRunner soapRunner = new SoapHttpTestRunner();
            soapRunner.setClientHttpRequestFactory(factory);
            soapRunner.setResourceUrl(validationResourceUrl);
            soapRunner.setSecret(secret);
            return soapRunner;
         case OPEN_API_SCHEMA:
            OpenAPITestRunner openApiRunner = new OpenAPITestRunner(resourceRepository, responseRepository, true);
            openApiRunner.setClientHttpRequestFactory(factory);
            openApiRunner.setSecret(secret);
            return openApiRunner;
         case ASYNC_API_SCHEMA:
            AsyncAPITestRunner asyncApiRunner = new AsyncAPITestRunner(resourceRepository, secretRepository);
            asyncApiRunner.setClientHttpRequestFactory(factory);
            asyncApiRunner.setAsyncMinionUrl(asyncMinionUrl);
            return asyncApiRunner;
         case GRPC_PROTOBUF:
            GrpcTestRunner grpcRunner = new GrpcTestRunner(resourceRepository);
            grpcRunner.setSecret(secret);
            grpcRunner.setTimeout(runnerTimeout);
            return grpcRunner;
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
                  return null;
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
                  return null;
               }
            }
         default:
            HttpTestRunner httpRunner = new HttpTestRunner();
            httpRunner.setClientHttpRequestFactory(factory);
            httpRunner.setSecret(secret);
            return httpRunner;
      }
   }

   /** Build a customer truststore with provided certificate in PEM format. */
   private KeyStore buildCustomCaCertTruststore(String caCertPem) throws Exception {
      // First compute a stripped PEM certificate and decode it from base64.
      String strippedPem = caCertPem.replaceAll(BEGIN_CERTIFICATE, "")
            .replaceAll(END_CERTIFICATE, "");
      InputStream is = new ByteArrayInputStream(org.apache.commons.codec.binary.Base64.decodeBase64(strippedPem));

      // Generate a new x509 certificate from the stripped decoded pem.
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate caCert = (X509Certificate)cf.generateCertificate(is);

      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null); // You don't need the KeyStore instance to come from a file.
      ks.setCertificateEntry("caCert", caCert);

      return ks;
   }

   /** Download a remote HTTP URL repository into a temporary local file. */
   private String handleJobRepositoryDownloadToFile(ImportJob job) throws IOException {
      // Check if job has an associated secret to retrieve.
      Secret jobSecret = null;
      if (job.getSecretRef() != null) {
         log.debug("Retrieving secret {} for job {}", job.getSecretRef().getName(), job.getName());
         jobSecret = secretRepository.findById(job.getSecretRef().getSecretId()).orElse(null);
      }

      File localFile = HTTPDownloader.handleHTTPDownloadToFile(job.getRepositoryUrl(),
            jobSecret, job.isRepositoryDisableSSLValidation());
      return localFile.getAbsolutePath();
   }
}
