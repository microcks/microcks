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
package io.microcks.service;

import io.microcks.domain.OAuth2AuthorizedClient;
import io.microcks.domain.OAuth2ClientContext;
import io.microcks.domain.Operation;
import io.microcks.domain.Request;
import io.microcks.domain.Response;
import io.microcks.domain.Secret;
import io.microcks.domain.Service;
import io.microcks.domain.TestCaseResult;
import io.microcks.domain.TestResult;
import io.microcks.domain.TestReturn;
import io.microcks.domain.TestRunnerType;
import io.microcks.domain.TestStepResult;
import io.microcks.event.TestCompletionEvent;
import io.microcks.repository.RequestRepository;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.SecretRepository;
import io.microcks.repository.TestResultRepository;
import io.microcks.security.AuthorizationException;
import io.microcks.security.OAuth2AuthorizedClientProvider;
import io.microcks.util.IdBuilder;
import io.microcks.test.AbstractTestRunner;
import io.microcks.test.http.ApacheClientHttpRequestFactory;
import io.microcks.util.http.HttpMethod;
import io.microcks.test.TestRunnerContext;
import io.microcks.test.TestRunnerFactory;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Bean managing the launch of new Tests.
 * @author laurent
 */
@ApplicationScoped
public class TestRunnerService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(TestRunnerService.class);

   /** Constant representing the header line in a custom CA Cert in PEM format. */
   private static final String BEGIN_CERTIFICATE = "-----BEGIN CERTIFICATE-----";
   /** Constant representing the footer line in a custom CA Cert in PEM format. */
   private static final String END_CERTIFICATE = "-----END CERTIFICATE-----";

   private final ResourceRepository resourceRepository;
   private final RequestRepository requestRepository;
   private final ResponseRepository responseRepository;
   private final TestResultRepository testResultRepository;
   private final SecretRepository secretRepository;
   private final Event<TestCompletionEvent> testCompletionEvent;

   @ConfigProperty(name = "tests-callback.url", defaultValue = "http://localhost:8080")
   String testsCallbackUrl;

   @ConfigProperty(name = "postman-runner.url", defaultValue = "http://localhost:3000")
   String postmanRunnerUrl;

   @ConfigProperty(name = "async-minion.url", defaultValue = "http://localhost:8081")
   String asyncMinionUrl;

   @ConfigProperty(name = "validation.resourceUrl", defaultValue = "http://localhost:8080/api/resources/")
   String validationResourceUrl;

   /**
    * Build a new TestRunnerService with the required dependencies.
    * @param resourceRepository   The repository to manage persistent resources
    * @param requestRepository    The repository to manage persistent requests
    * @param responseRepository   The repository to manage persistent responses
    * @param testResultRepository The repository to manage persistent testResults
    * @param secretRepository     The repository to manage persistent secrets
    * @param testCompletionEvent  The CDI event used to publish test completions
    */
   @Inject
   public TestRunnerService(ResourceRepository resourceRepository, RequestRepository requestRepository,
         ResponseRepository responseRepository, TestResultRepository testResultRepository,
         SecretRepository secretRepository, Event<TestCompletionEvent> testCompletionEvent) {
      this.resourceRepository = resourceRepository;
      this.requestRepository = requestRepository;
      this.responseRepository = responseRepository;
      this.testResultRepository = testResultRepository;
      this.secretRepository = secretRepository;
      this.testCompletionEvent = testCompletionEvent;
   }

   /**
    * Launch tests using asynchronous/completable future pattern.
    * @param testResult    TestResults to aggregate results within
    * @param service       Service to test
    * @param runnerType    Type of runner for launching the tests
    * @param oAuth2Context An optional OAuth2ClientContext that may complement Secret information
    * @return A Future wrapping test results
    */
   public CompletableFuture<TestResult> launchTestsInternal(TestResult testResult, Service service,
         TestRunnerType runnerType, OAuth2ClientContext oAuth2Context) {
      log.info("Launching test {} on {} with runner {}", testResult.getId(), testResult.getTestedEndpoint(),
            runnerType);
      return CompletableFuture.supplyAsync(() -> runTests(testResult, service, runnerType, oAuth2Context))
            .exceptionally(t -> failTest(testResult, t));
   }

   /** Nothing else observes the async future: log the failure and do not leave the test in progress forever. */
   private TestResult failTest(TestResult testResult, Throwable t) {
      log.error("Test {} failed unexpectedly", testResult.getId(), t);
      testResult.setSuccess(false);
      testResult.setInProgress(false);
      testResult.setElapsedTime(0);
      testResult.setRunnerMessage("Test execution failed: " + t.getMessage());
      testResultRepository.persistOrUpdate(testResult);
      return testResult;
   }

   private TestResult runTests(TestResult testResult, Service service, TestRunnerType runnerType,
         OAuth2ClientContext oAuth2Context) {
      // Found next build number for this test.
      List<TestResult> older = testResultRepository.findByServiceId(service.getId(), Page.ofSize(2),
            Sort.descending("testNumber"));
      if (older != null && !older.isEmpty() && older.get(0).getTestNumber() != null) {
         testResult.setTestNumber(older.get(0).getTestNumber() + 1L);
      } else {
         testResult.setTestNumber(1L);
      }

      Secret secret = null;
      if (testResult.getSecretRef() != null) {
         secret = secretRepository.findById(testResult.getSecretRef().getSecretId());
         log.debug("Using a secret to test endpoint? '{}'", secret != null ? secret.getName() : "none");
      }

      if (oAuth2Context != null) {
         log.debug("Applying OAuth2 grant before actually running the test '{}'", oAuth2Context.getGrantType());
         OAuth2AuthorizedClientProvider tokenProvider = new OAuth2AuthorizedClientProvider();
         try {
            OAuth2AuthorizedClient authorizedClient = tokenProvider.authorize(oAuth2Context);
            testResult.setAuthorizedClient(authorizedClient);
            if (secret != null) {
               log.debug("Updating the Secret with token from OAuth2 for '{}'", authorizedClient.getPrincipalName());
               secret.setToken(authorizedClient.getEncodedAccessToken());
               secret.setTokenHeader(null);
            } else {
               secret = new Secret();
               secret.setToken(authorizedClient.getEncodedAccessToken());
            }
         } catch (AuthorizationException authorizationException) {
            log.error("OAuth2 token flow '{}' failed with: {}", oAuth2Context.getGrantType(),
                  authorizationException.getMessage());
            log.error("Marking the test as a failure before cancelling it");
            // Set flags and add to results before exiting loop.
            testResult.setSuccess(false);
            testResult.setInProgress(false);
            testResult.setElapsedTime(0);
            testResult.setRunnerMessage("OAuth2 token flow failed: " + authorizationException.getMessage());
            testResultRepository.persistOrUpdate(testResult);
            return testResult;
         }
      }

      // Initialize runner once as it is shared for each test.
      AbstractTestRunner<HttpMethod> testRunner = retrieveRunner(runnerType, secret, testResult.getTimeout(),
            service.getId());
      if (testRunner == null) {
         // Set failure and stopped flags and save before exiting.
         testResult.setSuccess(false);
         testResult.setInProgress(false);
         testResult.setElapsedTime(0);
         testResult.setRunnerMessage("Could not retrieve runner for " + runnerType);
         testResultRepository.persistOrUpdate(testResult);
         return testResult;
      }

      for (TestCaseResult testCaseResult : testResult.getTestCaseResults()) {
         // Retrieve operation corresponding to testCase.
         Optional<Operation> operationOpt = service.getOperations().stream()
               .filter(o -> o.getName().equals(testCaseResult.getOperationName())).findFirst();
         if (!operationOpt.isPresent()) {
            log.warn("No operation named '{}' found on service '{}', skipping test case",
                  testCaseResult.getOperationName(), service.getName());
            continue;
         }
         Operation operation = operationOpt.get();
         String testCaseId = IdBuilder.buildTestCaseId(testResult, operation);

         // Prepare collection of requests to launch.
         List<Request> requests = requestRepository.findByOperationId(IdBuilder.buildOperationId(service, operation));
         requests = cloneRequestsForTestCase(requests, testCaseId);

         List<TestReturn> results = new ArrayList<>();
         try {
            HttpMethod method = testRunner.buildMethod(operation.getMethod());
            results = testRunner.runTest(service, operation, testResult, requests, testResult.getTestedEndpoint(),
                  method);
         } catch (URISyntaxException use) {
            log.error("URISyntaxException on endpoint {}, aborting current tests", testResult.getTestedEndpoint(), use);
            // Set flags and add to results before exiting loop.
            testCaseResult.setSuccess(false);
            testCaseResult.setElapsedTime(0);
            testResult.setRunnerMessage("URISyntaxException on endpoint: " + testResult.getTestedEndpoint());
            testResultRepository.persistOrUpdate(testResult);
            break;
         } catch (Throwable t) {
            log.error("Throwable while testing operation {}", operation.getName(), t);
         }

         // Update result if we got returns. If no returns, it means that there's no
         // sample request for that operation -> mark it as failed.
         if (results == null) {
            testCaseResult.setSuccess(false);
            testCaseResult.setElapsedTime(0);
            testResultRepository.persistOrUpdate(testResult);
         } else if (!results.isEmpty()) {
            updateTestCaseResultWithReturns(testCaseResult, results, testCaseId);
            testResultRepository.persistOrUpdate(testResult);
         }
      }

      // Update success, progress indicators and total time before saving and returning.
      updateTestResult(testResult);

      return testResult;
   }

   /**
    * Update the testsCallbackUrl property. This is just here for testing purposes.
    * @param testsCallbackUrl The URL to set
    */
   public void setTestsCallbackUrl(String testsCallbackUrl) {
      this.testsCallbackUrl = testsCallbackUrl;
   }

   /**
    * Update the postmanRunnerUrl property. This is just here for testing purposes.
    * @param postmanRunnerUrl The URL to set
    */
   public void setPostmanRunnerUrl(String postmanRunnerUrl) {
      this.postmanRunnerUrl = postmanRunnerUrl;
   }

   /**
    *
    */
   private void updateTestCaseResultWithReturns(TestCaseResult testCaseResult, List<TestReturn> testReturns,
         String testCaseId) {
      // Prepare a bunch of flag we're going to complete.
      boolean successFlag = true;
      long caseElapsedTime = 0;
      List<Response> responses = new ArrayList<>();
      List<Request> actualRequests = new ArrayList<>();

      for (TestReturn testReturn : testReturns) {
         // Deal with elapsed time and success flag.
         caseElapsedTime += testReturn.getElapsedTime();
         TestStepResult testStepResult = testReturn.buildTestStepResult();
         if (!testStepResult.isSuccess()) {
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
      responseRepository.persistOrUpdate(responses);

      // Associate responses to requests before saving requests.
      for (int i = 0; i < actualRequests.size(); i++) {
         actualRequests.get(i).setResponseId(responses.get(i).getId());
      }
      log.debug("Saving {} requests with testCaseId {}", responses.size(), testCaseId);
      requestRepository.persistOrUpdate(actualRequests);

      // Update and save the completed TestCaseResult.
      // We cannot consider as success if we have no TestStepResults associated...
      if (!testCaseResult.getTestStepResults().isEmpty()) {
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
      for (TestCaseResult testCaseResult : testResult.getTestCaseResults()) {
         totalElapsedTime += testCaseResult.getElapsedTime();
         if (!testCaseResult.isSuccess()) {
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

      testResultRepository.persistOrUpdate(testResult);

      // If test is completed, publish a completion event.
      if (!testResult.isInProgress()) {
         publishTestCompletionEvent(testResult);
      }
   }

   /** Clone and prepare request for a test case usage. */
   private List<Request> cloneRequestsForTestCase(List<Request> requests, String testCaseId) {
      List<Request> result = new ArrayList<>();
      for (Request request : requests) {
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
   private AbstractTestRunner<HttpMethod> retrieveRunner(TestRunnerType runnerType, Secret secret, Long runnerTimeout,
         String serviceId) {
      // TODO: remove this ugly initialization later.
      // Initialize new HttpComponentsClientHttpRequestFactory that supports https connections.
      SSLContext sslContext = null;
      try {
         // Initialize trusting material depending on Secret content.
         if (secret != null && secret.getCaCertPem() != null) {
            log.debug("Test Secret contains a CA Cert, installing certificate into SSLContext");
            sslContext = SSLContexts.custom()
                  .loadTrustMaterial(buildCustomCaCertTruststore(secret.getCaCertPem()), null).build();
         } else {
            log.debug("No Test Secret or no CA Cert found, installing accept everything strategy");
            sslContext = SSLContexts.custom().loadTrustMaterial(null, (cert, authType) -> true).build();
         }
      } catch (Exception e) {
         log.error("Exception while building SSLContext with acceptingTrustStrategy", e);
         return null;
      }
      SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
            new String[] { "TLSv1.2", "TLSv1.3" }, null, NoopHostnameVerifier.INSTANCE);

      // BasicHttpClientConnectionManager was facing issues detecting close connections and re-creating new ones.
      // Switching to PoolingHttpClientConnectionManager for HC 5.2 solves this issue.
      final PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setSSLSocketFactory(sslsf).build();

      CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager)
            .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(200))
                  .setResponseTimeout(Timeout.ofMilliseconds(runnerTimeout)).build())
            .build();

      ApacheClientHttpRequestFactory factory = new ApacheClientHttpRequestFactory(httpClient);

      TestRunnerContext context = new TestRunnerContext(resourceRepository, responseRepository, secretRepository,
            factory, secret, runnerTimeout, validationResourceUrl, asyncMinionUrl, testsCallbackUrl, postmanRunnerUrl);
      return TestRunnerFactory.getTestRunner(runnerType, context);
   }

   /** Build a customer truststore with provided certificate in PEM format. */
   private KeyStore buildCustomCaCertTruststore(String caCertPem) throws Exception {
      // First compute a stripped PEM certificate and decode it from base64.
      String strippedPem = caCertPem.replace(BEGIN_CERTIFICATE, "").replace(END_CERTIFICATE, "");
      InputStream is = new ByteArrayInputStream(java.util.Base64.getMimeDecoder().decode(strippedPem));

      // Generate a new x509 certificate from the stripped decoded pem.
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      X509Certificate caCert = (X509Certificate) cf.generateCertificate(is);

      KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null); // You don't need the KeyStore instance to come from a file.
      ks.setCertificateEntry("caCert", caCert);

      return ks;
   }

   /** Publish a TestCompletionEvent towards asynchronous consumers. */
   private void publishTestCompletionEvent(TestResult testResult) {
      TestCompletionEvent event = new TestCompletionEvent(testResult);
      testCompletionEvent.fireAsync(event);
      log.debug("Test completion event has been published");
   }
}
