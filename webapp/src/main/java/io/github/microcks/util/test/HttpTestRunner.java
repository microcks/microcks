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
package io.github.microcks.util.test;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Secret;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.util.URIBuilder;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

/**
 * An extension of AbstractTestRunner that checks that returned response is valid according the HTTP code of the
 * response (OK if code in the 20x range, KO in the 30x and over range).
 * @author laurent
 */
public class HttpTestRunner extends AbstractTestRunner<HttpMethod> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(HttpTestRunner.class);

   private Secret secret;

   private ClientHttpRequestFactory clientHttpRequestFactory;

   private Map<String, Secret> cachedSecrets;

   /**
    * Set the Secret used for securing the requests.
    * @param secret The Secret used or securing the requests.
    */
   public void setSecret(Secret secret) {
      this.secret = secret;
   }

   /**
    * Set the ClientHttpRequestFactory used for reaching endpoint.
    * @param clientHttpRequestFactory The ClientHttpRequestFactory used for reaching endpoint
    */
   public void setClientHttpRequestFactory(ClientHttpRequestFactory clientHttpRequestFactory) {
      this.clientHttpRequestFactory = clientHttpRequestFactory;
   }

   @Override
   public List<TestReturn> runTest(Service service, Operation operation, TestResult testResult, List<Request> requests,
         String endpointUrl, HttpMethod method) throws URISyntaxException, IOException {
      if (log.isDebugEnabled()) {
         log.debug("Launching test run on {} for {} request(s)", endpointUrl, requests.size());
      }

      if (requests.isEmpty()) {
         return null;
      }

      // Initialize result container.
      List<TestReturn> result = new ArrayList<TestReturn>();

      for (Request request : requests) {
         // Reset status code, message and request each time.
         int code = TestReturn.SUCCESS_CODE;
         String message = null;
         String customizedEndpointUrl = endpointUrl;
         if (service.getType().equals(ServiceType.REST)) {
            String operationName = operation.getName();
            // Name may start with verb, remove it if present.
            if (operationName.indexOf(' ') > 0 && operationName.indexOf(' ') < operationName.length()) {
               operationName = operationName.split(" ")[1];
            }

            customizedEndpointUrl += URIBuilder.buildURIFromPattern(operationName, request.getQueryParameters());
            log.debug("Using customized endpoint url: {}", customizedEndpointUrl);
         }
         ClientHttpRequest httpRequest = clientHttpRequestFactory.createRequest(new URI(customizedEndpointUrl), method);

         // Set headers to request if any. Start with those coming from request itself.
         // Add or override existing headers with test specific ones for operation and globals.
         Set<Header> headers = new HashSet<>();
         if (request.getHeaders() != null) {
            headers.addAll(request.getHeaders());
         }
         if (testResult.getOperationsHeaders() != null) {
            if (testResult.getOperationsHeaders().getGlobals() != null) {
               headers.addAll(testResult.getOperationsHeaders().getGlobals());
            }
            if (testResult.getOperationsHeaders().get(operation.getName()) != null) {
               headers.addAll(testResult.getOperationsHeaders().get(operation.getName()));
            }
         }
         if (!headers.isEmpty()) {
            for (Header header : headers) {
               log.debug("Adding header {} to request", header.getName());
               httpRequest.getHeaders().add(header.getName(), buildValue(header.getValues()));
            }
            // Update request headers for traceability of possibly added ones.
            request.setHeaders(headers);
         }

         // Now manage specific authorization headers if there's a secret.
         if (secret != null) {
            addAuthorizationHeadersFromSecret(httpRequest, request, secret);
         }

         // Allow extensions to realize some pre-processing of request.
         prepareRequest(request);

         // If there's input content, add it to request.
         if (request.getContent() != null) {
            // Update request content with rendered body if necessary.
            request.setContent(TestRunnerCommons.renderRequestContent(request, headers));
            log.trace("Sending following request content: {}", request.getContent());
            httpRequest.getBody().write(request.getContent().getBytes());
         }

         // Actually execute request.
         long startTime = System.currentTimeMillis();
         ClientHttpResponse httpResponse = null;
         try {
            httpResponse = httpRequest.execute();
         } catch (IOException ioe) {
            log.error("IOException while executing request {} on {}", request.getName(), customizedEndpointUrl, ioe);
            code = TestReturn.FAILURE_CODE;
            message = ioe.getMessage();
         }
         long duration = System.currentTimeMillis() - startTime;

         // Extract and store response body so that stream may not be consumed more than o1 time ;-)
         String responseContent = null;
         if (httpResponse != null) {
            StringWriter writer = new StringWriter();
            IOUtils.copy(httpResponse.getBody(), writer);
            responseContent = writer.toString();
         }

         // If still in success, check if http code is out of correct ranges (20x and 30x).
         if (code == TestReturn.SUCCESS_CODE) {
            code = extractTestReturnCode(service, operation, request, httpResponse, responseContent);
            message = extractTestReturnMessage(service, operation, request, httpResponse);
         }

         // Create a Response object for returning.
         Response response = new Response();
         if (httpResponse != null) {
            response.setContent(responseContent);
            response.setStatus(String.valueOf(httpResponse.getStatusCode().value()));
            log.debug("Response Content-Type: {}", httpResponse.getHeaders().getContentType());
            if (httpResponse.getHeaders().getContentType() != null) {
               response.setMediaType(httpResponse.getHeaders().getContentType().toString());
            }
            headers = buildHeaders(httpResponse);
            if (headers != null) {
               response.setHeaders(headers);
            }
            log.info("Closing http response");
            httpResponse.close();
         }

         result.add(new TestReturn(code, duration, message, request, response));
      }
      return result;
   }

   /**
    * Build the HttpMethod corresponding to string. Default to POST if unknown or unrecognized.
    */
   @Override
   public HttpMethod buildMethod(String method) {
      if (method != null) {
         if ("GET".equals(method.toUpperCase().trim())) {
            return HttpMethod.GET;
         } else if ("PUT".equals(method.toUpperCase().trim())) {
            return HttpMethod.PUT;
         } else if ("DELETE".equals(method.toUpperCase().trim())) {
            return HttpMethod.DELETE;
         } else if ("PATCH".equals(method.toUpperCase().trim())) {
            return HttpMethod.PATCH;
         } else if ("OPTIONS".equals(method.toUpperCase().trim())) {
            return HttpMethod.OPTIONS;
         }
      }
      return HttpMethod.POST;
   }

   /**
    * This is a hook for allowing sub-classes to redefine the criteria for telling a response is a success or a failure.
    * This implementation check raw http code (success if in 20x range, failure if not).
    * @param service         The service under test
    * @param operation       The tested operation
    * @param request         The tested reference request
    * @param httpResponse    The received response from endpoint
    * @param responseContent The response body content if any (may be null)
    * @return The test result code, whether TestReturn.SUCCESS_CODE or TestReturn.FAILURE_CODE
    */
   protected int extractTestReturnCode(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse, String responseContent) {
      int code = TestReturn.SUCCESS_CODE;
      // Set code to failure if http code out of correct ranges (20x and 30x).
      try {
         if (httpResponse.getStatusCode().value() > 299) {
            log.debug("Http status code is {}, marking test as failed.", httpResponse.getStatusCode().value());
            code = TestReturn.FAILURE_CODE;
         }
      } catch (IOException ioe) {
         log.debug("IOException while getting raw status code in response", ioe);
         code = TestReturn.FAILURE_CODE;
      }
      return code;
   }

   /**
    * This is a hook for allowing sub-classes to realize some pre-processing on request before it is actually issued to
    * test endpoint.
    * @param request The request that will be sent to endpoint
    */
   protected void prepareRequest(Request request) {
      // Nothing to do in this base implementation, use raw request.
   }

   /**
    * This is a hook for allowing sub-classes to redefine the extraction of success or failure message. This
    * implementation just extract raw http code.
    * @param service      The service under test
    * @param operation    The tested operation
    * @param request      The tested reference request
    * @param httpResponse The received response from endpoint
    * @return The test result message.
    */
   protected String extractTestReturnMessage(Service service, Operation operation, Request request,
         ClientHttpResponse httpResponse) {
      String message = null;
      // Set code to failure if http code out of correct ranges (20x and 30x).
      try {
         message = String.valueOf(httpResponse.getStatusCode().value());
      } catch (IOException ioe) {
         log.debug("IOException while getting raw status code in response", ioe);
         message = "IOException while getting raw status code in response";
      }
      return message;
   }

   /** Build domain headers from ClientHttpResponse ones. */
   private Set<Header> buildHeaders(ClientHttpResponse httpResponse) {
      if (httpResponse.getHeaders() != null && !httpResponse.getHeaders().isEmpty()) {
         Set<Header> headers = new HashSet<>();
         HttpHeaders responseHeaders = httpResponse.getHeaders();
         for (Entry<String, List<String>> responseHeader : responseHeaders.entrySet()) {
            Header header = new Header();
            header.setName(responseHeader.getKey());
            header.setValues(new HashSet<>(responseHeader.getValue()));
            headers.add(header);
         }
         return headers;
      }
      return null;
   }

   /** Complete the test request with authorization data coming from secret. */
   private void addAuthorizationHeadersFromSecret(ClientHttpRequest httpRequest, Request request, Secret secret) {
      if (secret != null) {
         // If Basic authentication required, set request property.
         if (secret.getUsername() != null && secret.getPassword() != null) {
            log.debug("Secret contains username/password, assuming Authorization Basic");
            // Building a base64 string.
            String encoded = Base64.getEncoder()
                  .encodeToString((secret.getUsername() + ":" + secret.getPassword()).getBytes(StandardCharsets.UTF_8));
            httpRequest.getHeaders().set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
            // Add to Microcks request for traceability.
            request.getHeaders().add(buildAuthHeaderWithSecret(HttpHeaders.AUTHORIZATION, "Basic "));
         }

         // If Token authentication required, set request property.
         if (secret.getToken() != null) {
            if (secret.getTokenHeader() != null && secret.getTokenHeader().trim().length() > 0) {
               log.debug("Secret contains token and token header, adding them as request header");
               httpRequest.getHeaders().set(secret.getTokenHeader().trim(), secret.getToken());
               // Add to Microcks request for traceability.
               request.getHeaders().add(buildAuthHeaderWithSecret(secret.getTokenHeader().trim(), ""));
            } else {
               log.debug("Secret contains token only, assuming Authorization Bearer");
               httpRequest.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + secret.getToken());
               // Add to Microcks request for traceability.
               request.getHeaders().add(buildAuthHeaderWithSecret(HttpHeaders.AUTHORIZATION, "Bearer "));
            }
         }
      }
   }

   /** Build a Microcks header for traceability. */
   private Header buildAuthHeaderWithSecret(String name, String type) {
      Header authHeader = new Header();
      authHeader.setName(name);
      authHeader.setValues(new TreeSet<>(Arrays.asList(type + "<value-from-secret>")));
      return authHeader;
   }
}
