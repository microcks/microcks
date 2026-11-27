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
package io.microcks.artifact.asyncapi;

import io.microcks.domain.Operation;
import io.microcks.domain.Request;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Secret;
import io.microcks.domain.Service;
import io.microcks.domain.TestResult;
import io.microcks.domain.TestReturn;
import io.microcks.domain.TestRunnerType;
import io.microcks.test.ResourceReader;
import io.microcks.test.SecretReader;
import io.microcks.test.AbstractTestRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.microcks.util.http.HttpMethod;
import io.microcks.util.http.ClientHttpRequest;
import io.microcks.util.http.ClientHttpRequestFactory;
import io.microcks.util.http.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * This is an implementation of HttpTestRunner that deals with AsyncAPI schema testing. It delegates the consumption of
 * asynchronous messages and the actual validation to the <b>microcks-async-minion</b> component, triggering it through
 * an API call.
 * @author laurent
 */
public class AsyncAPITestRunner extends AbstractTestRunner<HttpMethod> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(AsyncAPITestRunner.class);

   private ObjectMapper mapper = new ObjectMapper();

   private ResourceReader resourceReader;

   private SecretReader secretReader;

   private ClientHttpRequestFactory clientHttpRequestFactory;

   private String asyncMinionUrl = null;

   /**
    * Build a new AsyncAPITestRunner using a resource repository for retrieving AsyncAPI specification.
    * @param resourceReader The repository that contains AsyncAPI specification to validate
    * @param secretReader   The repository for accessing secrets for connecting test endpoints
    */
   public AsyncAPITestRunner(ResourceReader resourceReader, SecretReader secretReader) {
      this.resourceReader = resourceReader;
      this.secretReader = secretReader;
   }

   /**
    * Set the ClientHttpRequestFactory used for reaching endpoint.
    * @param clientHttpRequestFactory The ClientHttpRequestFactory used for reaching endpoint
    */
   public void setClientHttpRequestFactory(ClientHttpRequestFactory clientHttpRequestFactory) {
      this.clientHttpRequestFactory = clientHttpRequestFactory;
   }

   public void setAsyncMinionUrl(String asyncMinionUrl) {
      this.asyncMinionUrl = asyncMinionUrl;
   }

   @Override
   public List<TestReturn> runTest(Service service, Operation operation, TestResult testResult, List<Request> requests,
         String endpointUrl, HttpMethod method) throws URISyntaxException, IOException {

      if (log.isDebugEnabled()) {
         log.debug("Launching test run on " + endpointUrl + " for ms");
      }

      // Retrieve the resource corresponding to OpenAPI specification if any.
      Resource asyncAPISpecResource = null;
      List<Resource> resources = resourceReader.findByServiceId(service.getId());
      for (Resource resource : resources) {
         if (ResourceType.ASYNC_API_SPEC.equals(resource.getType())) {
            asyncAPISpecResource = resource;
            break;
         }
      }

      // Microcks-async-minion interface object building.
      ObjectNode jsonArg = mapper.createObjectNode();
      jsonArg.put("runnerType", TestRunnerType.ASYNC_API_SCHEMA.toString());
      jsonArg.put("testResultId", testResult.getId());
      jsonArg.put("serviceId", service.getId());
      jsonArg.put("operationName", operation.getName());
      jsonArg.put("endpointUrl", endpointUrl);
      jsonArg.put("timeoutMS", testResult.getTimeout());
      jsonArg.put("asyncAPISpec", asyncAPISpecResource.getContent());

      if (testResult.getSecretRef() != null) {
         Secret secret = secretReader.findById(testResult.getSecretRef().getSecretId());
         if (secret != null) {
            log.debug("Adding the secret '{}' to test specification request", secret.getName());
            jsonArg.set("secret", mapper.valueToTree(secret));
         }
      }

      URI asyncMinionURI = new URI(asyncMinionUrl + "/api/tests");
      ClientHttpRequest httpRequest = clientHttpRequestFactory.createRequest(asyncMinionURI, HttpMethod.POST);
      httpRequest.getBody().write(mapper.writeValueAsBytes(jsonArg));
      httpRequest.getHeaders().add("Content-Type", "application/json");

      // Actually execute request.
      ClientHttpResponse httpResponse = null;
      try {
         httpResponse = httpRequest.execute();
      } catch (IOException ioe) {
         log.error("IOException while executing request ", ioe);
      } finally {
         if (httpResponse != null) {
            httpResponse.close();
         }
      }

      return new ArrayList<>();
   }

   @Override
   public HttpMethod buildMethod(String method) {
      return null;
   }
}
