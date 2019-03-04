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
package io.github.microcks.util.postman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.microcks.domain.*;
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.test.AbstractTestRunner;
import io.github.microcks.util.test.TestReturn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * @author laurent
 */
public class PostmanTestStepsRunner extends AbstractTestRunner<HttpMethod> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(PostmanTestStepsRunner.class);

   private ObjectMapper mapper = new ObjectMapper();

   private JsonNode collection;

   private ClientHttpRequestFactory clientHttpRequestFactory;

   private String testsCallbackUrl = null;

   private String postmanRunnerUrl = null;

   /**
    * Build a new PostmanTestStepsRunner for a collection.
    * @param collectionFilePath The path to SoapUI project file
    * @throws java.io.IOException if file cannot be found or accessed.
    */
   public PostmanTestStepsRunner(String collectionFilePath) throws IOException {
      try {
         // Read Json bytes.
         byte[] jsonBytes = Files.readAllBytes(Paths.get(collectionFilePath));
         // Convert them to Node using Jackson object mapper.
         ObjectMapper mapper = new ObjectMapper();
         collection = mapper.readTree(jsonBytes);
      } catch (Exception e) {
         throw new IOException("Postman collection file");
      }
   }

   /**
    * Set the ClientHttpRequestFactory used for reaching endpoint.
    * @param clientHttpRequestFactory The ClientHttpRequestFactory used for reaching endpoint
    */
   public void setClientHttpRequestFactory( ClientHttpRequestFactory clientHttpRequestFactory) {
      this.clientHttpRequestFactory = clientHttpRequestFactory;
   }

   public void setTestsCallbackUrl(String testsCallbackUrl) {
      this.testsCallbackUrl = testsCallbackUrl;
   }

   public void setPostmanRunnerUrl(String postmanRunnerUrl) {
      this.postmanRunnerUrl = postmanRunnerUrl;
   }

   @Override
   public List<TestReturn> runTest(Service service, Operation operation, TestResult testResult,
                                   List<Request> requests, String endpointUrl, HttpMethod method) throws URISyntaxException, IOException {
      if (log. isDebugEnabled()){
         log.debug("Launching test run on " + endpointUrl + " for " + requests.size() + " request(s)");
      }

      if (endpointUrl.endsWith("/")) {
         endpointUrl = endpointUrl.substring(0, endpointUrl.length() - 1);
      }

      // Microcks-postman-runner interface object building.
      JsonNode jsonArg = mapper.createObjectNode();
      ((ObjectNode) jsonArg).put("operation", operation.getName());
      ((ObjectNode) jsonArg).put("callbackUrl", testsCallbackUrl + "/api/tests/" + testResult.getId() + "/testCaseResult");

      // First we have to retrieved and add the test script for this operation from within Postman collection.
      JsonNode testScript = extractOperationTestScript(operation);
      if (testScript != null) {
         log.debug("Found a testScript for this operation !");
         ((ObjectNode) jsonArg).set("testScript", testScript);
      }

      // Then we have to add the corresponding 'requests' objects.
      ArrayNode jsonRequests = mapper.createArrayNode();
      for (Request request : requests) {
         JsonNode jsonRequest = mapper.createObjectNode();

         String operationName = operation.getName().substring(operation.getName().indexOf(" ") + 1);
         String customizedEndpointUrl = endpointUrl + URIBuilder.buildURIFromPattern(operationName, request.getQueryParameters());
         log.debug("Using customized endpoint url: " + customizedEndpointUrl);

         ((ObjectNode) jsonRequest).put("endpointUrl", customizedEndpointUrl);
         ((ObjectNode) jsonRequest).put("method", operation.getMethod());
         ((ObjectNode) jsonRequest).put("name", request.getName());

         if (request.getContent() != null && request.getContent().length() > 0) {
            ((ObjectNode) jsonRequest).put("body", request.getContent());
         }
         if (request.getQueryParameters() != null && request.getQueryParameters().size() > 0) {
            ArrayNode jsonParams = buildQueryParams(request.getQueryParameters());
            ((ObjectNode) jsonRequest).set("queryParams", jsonParams);
         }
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
         if (headers != null && headers.size() > 0) {
            ArrayNode jsonHeaders = buildHeaders(headers);
            ((ObjectNode) jsonRequest).set("headers", jsonHeaders);
         }

         jsonRequests.add(jsonRequest);
      }
      ((ObjectNode) jsonArg).set("requests", jsonRequests);

      URI postmanRunnerURI = new URI(postmanRunnerUrl + "/tests/" + testResult.getId());
      ClientHttpRequest httpRequest = clientHttpRequestFactory.createRequest(postmanRunnerURI, HttpMethod.POST);
      httpRequest.getBody().write(mapper.writeValueAsBytes(jsonArg));
      httpRequest.getHeaders().add("Content-Type", "application/json");

      // Actually execute request.
      ClientHttpResponse httpResponse = null;
      try{
         httpResponse = httpRequest.execute();
      } catch (IOException ioe){
         log.error("IOException while executing request ", ioe);
      }

      return new ArrayList<TestReturn>();
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
      if (collectedScripts.size() > 0) {
         return collectedScripts.get(0);
      }
      return null;
   }

   private void extractTestScript(String operationNameRadix, JsonNode itemNode, Operation operation, List<JsonNode> collectedScripts) {
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
         if (operationName.equals(operation.getName())) {
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
         JsonNode jsonQP = mapper.createObjectNode();
         ((ObjectNode) jsonQP).put("key", parameter.getName());
         ((ObjectNode) jsonQP).put("value", parameter.getValue());
         jsonQPS.add(jsonQP);
      }
      return jsonQPS;
   }

   private ArrayNode buildHeaders(Set<Header> headers) {
      ArrayNode jsonHS = mapper.createArrayNode();
      for (Header header : headers) {
         JsonNode jsonH = mapper.createObjectNode();
         ((ObjectNode) jsonH).put("key", header.getName());
         ((ObjectNode) jsonH).put("value", buildValue(header.getValues()));
         jsonHS.add(jsonH);
      }
      return jsonHS;
   }
}
