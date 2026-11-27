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
package io.github.microcks.util.postman;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.test.AbstractTestRunner;
import io.github.microcks.util.test.TestRunnerCommons;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * An implementation of HttpTestRunner that deals with tests embedded into a Postman Collection. It delegates the actual
 * testing to the <code>microcks-postman-runner</code> component, triggering it through an API call.
 * @author laurent
 */
public class PostmanTestStepsRunner extends AbstractTestRunner<HttpMethod> {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(PostmanTestStepsRunner.class);

   private ObjectMapper mapper = new ObjectMapper();

   private JsonNode collection;

   private final ResourceRepository resourceRepository;
   private ClientHttpRequestFactory clientHttpRequestFactory;

   private String testsCallbackUrl = null;

   private String postmanRunnerUrl = null;

   /**
    * Build a new PostmanTestStepsRunner for a collection.
    * @param resourceRepository The repository that contains Postman Collection to test
    */
   public PostmanTestStepsRunner(ResourceRepository resourceRepository) {
      this.resourceRepository = resourceRepository;
   }

   /**
    * Set the ClientHttpRequestFactory used for reaching endpoint.
    * @param clientHttpRequestFactory The ClientHttpRequestFactory used for reaching endpoint
    */
   public void setClientHttpRequestFactory(ClientHttpRequestFactory clientHttpRequestFactory) {
      this.clientHttpRequestFactory = clientHttpRequestFactory;
   }

   public void setTestsCallbackUrl(String testsCallbackUrl) {
      this.testsCallbackUrl = testsCallbackUrl;
   }

   public void setPostmanRunnerUrl(String postmanRunnerUrl) {
      this.postmanRunnerUrl = postmanRunnerUrl;
   }

   @Override
   public List<TestReturn> runTest(Service service, Operation operation, TestResult testResult, List<Request> requests,
         String endpointUrl, HttpMethod method) throws URISyntaxException, IOException {
      if (log.isDebugEnabled()) {
         log.debug("Launching test run on {} for {} request(s)", endpointUrl, requests.size());
      }

      // Retrieve the resource corresponding to OpenAPI specification if any.
      Resource collectionResource = null;
      List<Resource> resources = resourceRepository.findByServiceId(service.getId());
      for (Resource resource : resources) {
         if (ResourceType.POSTMAN_COLLECTION.equals(resource.getType())) {
            collectionResource = resource;
            break;
         }
      }

      // Convert them to Node using Jackson object mapper.
      try {
         collection = mapper.readTree(collectionResource.getContent());
      } catch (Exception e) {
         throw new IOException("Postman collection file cannot be found or parsed", e);
      }

      // Sanitize endpoint url.
      if (endpointUrl.endsWith("/")) {
         endpointUrl = endpointUrl.substring(0, endpointUrl.length() - 1);
      }

      // Microcks-postman-runner interface object building.
      ObjectNode jsonArg = mapper.createObjectNode();
      jsonArg.put("operation", operation.getName());
      jsonArg.put("callbackUrl", testsCallbackUrl + "/api/tests/" + testResult.getId() + "/testCaseResult");

      // First we have to retrieved and add the test script for this operation from within Postman collection.
      JsonNode testScript = extractOperationTestScript(operation);
      if (testScript != null) {
         log.debug("Found a testScript for this operation !");
         jsonArg.set("testScript", testScript);
      } else {
         // We have nothing to test and Postman runner will reject the request without testScript.
         log.info("No testScript found for operation '{}', marking it as failed", operation.getName());
         return requests.stream().map(request -> new TestReturn(TestReturn.FAILURE_CODE, 0,
               "Not executed cause no Test Script found for this operation", request, new Response())).toList();
      }

      // Then we have to add the corresponding 'requests' objects.
      ArrayNode jsonRequests = mapper.createArrayNode();
      for (Request request : requests) {
         ObjectNode jsonRequest = mapper.createObjectNode();

         String operationName = operation.getName().substring(operation.getName().indexOf(" ") + 1);
         String customizedEndpointUrl = endpointUrl
               + URIBuilder.buildURIFromPattern(operationName, request.getQueryParameters());
         log.debug("Using customized endpoint url: {}", customizedEndpointUrl);

         jsonRequest.put("endpointUrl", customizedEndpointUrl);
         jsonRequest.put("method", operation.getMethod());
         jsonRequest.put("name", request.getName());

         if (request.getContent() != null && !request.getContent().isEmpty()) {
            jsonRequest.put("body", request.getContent());
         }
         if (request.getQueryParameters() != null && !request.getQueryParameters().isEmpty()) {
            ArrayNode jsonParams = buildQueryParams(request.getQueryParameters());
            jsonRequest.set("queryParams", jsonParams);
         }
         // Set headers to request if any. Start with those coming from request itself.
         // Add or override existing headers with test specific ones for operation and globals.
         Set<Header> headers = TestRunnerCommons.collectHeaders(testResult, request, operation);
         if (!headers.isEmpty()) {
            ArrayNode jsonHeaders = buildHeaders(headers);
            jsonRequest.set("headers", jsonHeaders);
         }

         jsonRequests.add(jsonRequest);
      }
      jsonArg.set("requests", jsonRequests);

      URI postmanRunnerURI = new URI(postmanRunnerUrl + "/tests/" + testResult.getId());
      ClientHttpRequest httpRequest = clientHttpRequestFactory.createRequest(postmanRunnerURI, HttpMethod.POST);
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

   private JsonNode extractOperationTestScript(Operation operation) {
      List<JsonNode> collectedScripts = new ArrayList<>();

      Iterator<JsonNode> items = collection.path("item").elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         extractTestScript("", item, operation, collectedScripts);
      }
      if (!collectedScripts.isEmpty()) {
         return collectedScripts.get(0);
      }
      return null;
   }

   private void extractTestScript(String operationNameRadix, JsonNode itemNode, Operation operation,
         List<JsonNode> collectedScripts) {
      String itemNodeName = itemNode.path("name").asText();

      // Item may be a folder or an operation description.
      if (!itemNode.has("request")) {
         // Item is simply a folder that may contain some other folders recursively.
         Iterator<JsonNode> items = itemNode.path("item").elements();
         while (items.hasNext()) {
            JsonNode item = items.next();
            extractTestScript(operationNameRadix + "/" + itemNodeName, item, operation, collectedScripts);
         }
      } else {
         // Item is here an operation description.
         String operationName = PostmanCollectionImporter.buildOperationName(itemNode, operationNameRadix);
         log.debug("Found operation '{}', comparing with '{}'", operationName, operation.getName());
         if (PostmanUtil.areOperationsEquivalent(operation.getName(), operationName)) {
            // We've got the correct operation.
            JsonNode events = itemNode.path("event");
            for (JsonNode event : events) {
               if ("test".equals(event.path("listen").asText())) {
                  log.debug("Found a matching event where listen=test");
                  collectedScripts.add(event);
               }
            }
         }
      }
   }

   private ArrayNode buildQueryParams(List<Parameter> queryParameters) {
      ArrayNode jsonQPS = mapper.createArrayNode();
      for (Parameter parameter : queryParameters) {
         ObjectNode jsonQP = mapper.createObjectNode();
         jsonQP.put("key", parameter.getName());
         jsonQP.put("value", parameter.getValue());
         jsonQPS.add(jsonQP);
      }
      return jsonQPS;
   }

   private ArrayNode buildHeaders(Set<Header> headers) {
      ArrayNode jsonHS = mapper.createArrayNode();
      for (Header header : headers) {
         ObjectNode jsonH = mapper.createObjectNode();
         jsonH.put("key", header.getName());
         jsonH.put("value", buildValue(header.getValues()));
         jsonHS.add(jsonH);
      }
      return jsonHS;
   }
}
