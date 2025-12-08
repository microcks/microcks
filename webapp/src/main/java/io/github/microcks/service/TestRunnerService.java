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

import io.github.microcks.domain.OAuth2AuthorizedClient;
import io.github.microcks.domain.OAuth2ClientContext;
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
import io.github.microcks.event.TestCompletionEvent;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.SecretRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.security.AuthorizationException;
import io.github.microcks.security.OAuth2AuthorizedClientProvider;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.asyncapi.AsyncAPITestRunner;
import io.github.microcks.util.graphql.GraphQLTestRunner;
import io.github.microcks.util.grpc.GrpcTestRunner;
import io.github.microcks.util.openapi.OpenAPITestRunner;
import io.github.microcks.util.postman.PostmanTestStepsRunner;
import io.github.microcks.util.soapui.SoapUIAssertionsTestRunner;
import io.github.microcks.util.test.AbstractTestRunner;
import io.github.microcks.util.test.HttpTestRunner;
import io.github.microcks.util.test.SoapHttpTestRunner;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
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
   private final ApplicationContext applicationContext;

   @Value("${tests-callback.url}")
   private String testsCallbackUrl = null;

   @Value("${postman-runner.url}")
   private String postmanRunnerUrl = null;

   @Value("${async-minion.url}")
   private String asyncMinionUrl = null;

   @Value("${validation.resourceUrl}")
   private String validationResourceUrl = null;

   /**
    * Build a new TestRunnerService with the required dependencies.
    * @param resourceRepository   The repository to manage persistent resources
    * @param requestRepository    The repository to manage persistent requests
    * @param responseRepository   The repository to manage persistent responses
    * @param testResultRepository The repository to manage persistent testResults
    * @param secretRepository     The repository to manage persistent secrets
    * @param applicationContext   The Spring application context
    */
   public TestRunnerService(ResourceRepository resourceRepository, RequestRepository requestRepository,
         ResponseRepository responseRepository, TestResultRepository testResultRepository,
         SecretRepository secretRepository, ApplicationContext applicationContext) {
      this.resourceRepository = resourceRepository;
      this.requestRepository = requestRepository;
      this.responseRepository = responseRepository;
      this.testResultRepository = testResultRepository;
      this.secretRepository = secretRepository;
      this.applicationContext = applicationContext;
   }

   /**
    * Launch tests using asynchronous/completable future pattern.
    * @param testResult    TestResults to aggregate results within
    * @param service       Service to test
    * @param runnerType    Type of runner for launching the tests
    * @param oAuth2Context An optional OAuth2ClientContext that may complement Secret information
    * @return A Future wrapping test results
    */
   @Async
   public CompletableFuture<TestResult> launchTestsInternal(TestResult testResult, Service service,
         TestRunnerType runnerType, OAuth2ClientContext oAuth2Context) {
      // Found next build number for this test.
      List<TestResult> older = testResultRepository.findByServiceId(service.getId(),
            PageRequest.of(0, 2, Sort.Direction.DESC, "testNumber"));
      if (older != null && !older.isEmpty() && older.get(0).getTestNumber() != null) {
         testResult.setTestNumber(older.get(0).getTestNumber() + 1L);
      } else {
         testResult.setTestNumber(1L);
      }

      Secret secret = null;
      if (testResult.getSecretRef() != null) {
         secret = secretRepository.findById(testResult.getSecretRef().getSecretId()).orElse(null);
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
            testResultRepository.save(testResult);
            return CompletableFuture.completedFuture(testResult);
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
            results = testRunner.runTest(service, operation, testResult, requests, testResult.getTestedEndpoint(),
                  method);
         } catch (URISyntaxException use) {
            log.error("URISyntaxException on endpoint {}, aborting current tests", testResult.getTestedEndpoint(), use);
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
    * Update the testsCallbackUrl property. This is just here for testing purposes.
    * @param testsCallbackUrl The URL to set
    */
   public void setTestsCallbackUrl(String testsCallbackUrl) {
      this.testsCallbackUrl = testsCallbackUrl;
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
      responseRepository.saveAll(responses);

      // Associate responses to requests before saving requests.
      for (int i = 0; i < actualRequests.size(); i++) {
         actualRequests.get(i).setResponseId(responses.get(i).getId());
      }
      log.debug("Saving {} requests with testCaseId {}", responses.size(), testCaseId);
      requestRepository.saveAll(actualRequests);

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

      testResultRepository.save(testResult);

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
            .setDefaultRequestConfig(
                  RequestConfig.custom().setResponseTimeout(Timeout.ofMilliseconds(runnerTimeout)).build())
            .build();

      HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
      factory.setConnectTimeout(200);

      switch (runnerType) {
         case SOAP_HTTP:
            SoapHttpTestRunner soapRunner = new SoapHttpTestRunner(resourceRepository);
            soapRunner.setClientHttpRequestFactory(factory);
            soapRunner.setResourceUrl(validationResourceUrl);
            soapRunner.setSecret(secret);
            return soapRunner;
         case OPEN_API_SCHEMA:
            OpenAPITestRunner openApiRunner = new OpenAPITestRunner(resourceRepository, responseRepository, true);
            openApiRunner.setClientHttpRequestFactory(factory);
            openApiRunner.setResourceUrl(validationResourceUrl);
            openApiRunner.setSecret(secret);
            return openApiRunner;
         case ASYNC_API_SCHEMA:
            AsyncAPITestRunner asyncApiRunner = new AsyncAPITestRunner(resourceRepository, secretRepository);
            asyncApiRunner.setClientHttpRequestFactory(factory);
            asyncApiRunner.setAsyncMinionUrl(asyncMinionUrl);
            return asyncApiRunner;
         case GRPC_PROTOBUF:
            GrpcTestRunner grpcRunner = new GrpcTestRunner(resourceRepository, responseRepository, true);
            grpcRunner.setSecret(secret);
            grpcRunner.setTimeout(runnerTimeout);
            return grpcRunner;
         case GRAPHQL_SCHEMA:
            GraphQLTestRunner graphqlRunner = new GraphQLTestRunner(resourceRepository);
            graphqlRunner.setClientHttpRequestFactory(factory);
            graphqlRunner.setSecret(secret);
            return graphqlRunner;
         case POSTMAN:
            PostmanTestStepsRunner postmanRunner = new PostmanTestStepsRunner(resourceRepository);
            postmanRunner.setClientHttpRequestFactory(factory);
            postmanRunner.setTestsCallbackUrl(testsCallbackUrl);
            postmanRunner.setPostmanRunnerUrl(postmanRunnerUrl);
            return postmanRunner;
         case SOAP_UI:
            SoapUIAssertionsTestRunner soapUIRunner = new SoapUIAssertionsTestRunner(resourceRepository);
            soapUIRunner.setClientHttpRequestFactory(factory);
            soapUIRunner.setResourceUrl(validationResourceUrl);
            soapUIRunner.setSecret(secret);
            return soapUIRunner;
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
      String strippedPem = caCertPem.replace(BEGIN_CERTIFICATE, "").replace(END_CERTIFICATE, "");
      InputStream is = new ByteArrayInputStream(org.apache.commons.codec.binary.Base64.decodeBase64(strippedPem));

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
      TestCompletionEvent event = new TestCompletionEvent(this, testResult);
      applicationContext.publishEvent(event);
      log.debug("Test completion event has been published");
   }
}
