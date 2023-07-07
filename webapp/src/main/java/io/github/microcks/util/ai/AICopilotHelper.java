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
package io.github.microcks.util.ai;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.opensaml.xml.signature.P;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This helper class holds general utility constants and methods for: <ul>
 *    <li>interacting with LLM (prompt template, formatting specifications</li>
 *    <li>parsing the output of LLM interaction (converting prompt templates to Microcks domain model)</li>
 * </ul>
 * It is intended to be use by {@code AICopilot} implementations so that they can focus on configuration, connection
 * and prompt refinement concerns.
 * @author laurent
 */
public class AICopilotHelper {

   protected static final ObjectMapper JSON_MAPPER = new ObjectMapper();
   protected static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

   protected static final String OPENAPI_OPERATION_PROMPT_TEMPLATE = """
         Given the OpenAPI specification below, generate %2$d full examples (request and response) for operation '%1$s' strictly (no sub-path).
         """;

   protected static final String YAML_FORMATTING_PROMPT = """
         Use only this YAML format for output (no other text or markdown):
         """;
   protected static final String REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE = """
         - example: %1$d
           request:
             url: <request url>
             headers:
               accept: application/json
             body: <request body>
           response:
             code: 200
             headers:
               content-type: application/json 
             body: <response body>
         """;
   protected static final String UNIDIRECTIONAL_EVENT_EXAMPLE_YAML_FORMATTING_TEMPLATE= """
         - example: %1$d
           message:
             headers:
               header_1: <value 1>
             payload: <message payload>
         """;

   /** Generate an OpenAPI prompt introduction, asking for generation of {@code numberOfSamples} sample for operation. */
   protected static String getOpenAPIOperationPromptIntro(String operationName, int numberOfSamples) {
      return String.format(OPENAPI_OPERATION_PROMPT_TEMPLATE, operationName, numberOfSamples);
   }

   protected static String getRequestResponseExampleYamlFormattingDirective(int numberOfSamples) {
      StringBuilder builder = new StringBuilder();
      for (int i=0; i<numberOfSamples; i++) {
         builder.append(String.format(REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE, i+1));
      }
      return builder.toString();
   }

   /** Some AI will reuse already present examples in the spec. This method cleans a specification from its examples. */
   protected static String removeExamplesFromOpenAPISpec(String specification) throws Exception {
      JsonNode specNode;
      boolean isJson = specification.trim().startsWith("{");

      if (isJson) {
         specNode = JSON_MAPPER.readTree(specification);
      } else {
         specNode = YAML_MAPPER.readTree(specification);
      }

      // Remove examples recursively from the root.
      removeExamplesInNode(specNode, specNode);

      if (isJson) {
         return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(specNode);
      }
      return YAML_MAPPER.writeValueAsString(specNode);
   }

   private static void removeExamplesInNode(JsonNode specNode, JsonNode node) {
      JsonNode target = followRefIfAny(specNode, node);
      if (target.getNodeType() == JsonNodeType.OBJECT) {
         if (target.has("examples")) {
            ((ObjectNode) node).remove("examples");
         }
         Iterator<Map.Entry<String, JsonNode>> fields = target.fields();
         while (fields.hasNext()) {
            removeExamplesInNode(specNode, fields.next().getValue());
         }
      }
      if (target.getNodeType() == JsonNodeType.ARRAY) {
         Iterator<JsonNode> elements = target.elements();
         while (elements.hasNext()){
            removeExamplesInNode(specNode, elements.next());
         }
      }
   }

   /** Transform the output respecting the {@code REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE} into Microcks domain exchanges. */
   protected static List<RequestResponsePair> parseRequestResponseTemplatizedOutput(Operation operation, String content) throws Exception {
      List<RequestResponsePair> results = new ArrayList<>();

      JsonNode root = YAML_MAPPER.readTree(content);
      if (root.getNodeType() == JsonNodeType.ARRAY) {
         Iterator<JsonNode> examples = root.elements();
         while (examples.hasNext()) {
            JsonNode example = examples.next();

            // Deal with parsing request.
            JsonNode requestNode = example.path("request");
            Request request = new Request();
            JsonNode requestHeadersNode = requestNode.path("headers");
            request.setHeaders(buildHeaders(requestHeadersNode));
            request.setContent(getMessageContent(requestHeadersNode, requestNode.path("body")));

            String url = requestNode.path("url").asText();
            if (url.contains("?")) {
               String[] kvPairs = url.substring(url.indexOf("?") + 1).split("&");
               for (String kvPair : kvPairs) {
                  String[] kv = kvPair.split("=");
                  Parameter param = new Parameter();
                  param.setName(kv[0]);
                  param.setValue(kv[1]);
                  request.addQueryParameter(param);
               }
            }

            // Deal with parsing response.
            JsonNode responseNode = example.path("response");
            Response response = new Response();
            JsonNode responseHeadersNode = responseNode.path("headers");
            response.setHeaders(buildHeaders(responseHeadersNode));
            response.setContent(getMessageContent(responseHeadersNode, responseNode.path("body")));
            response.setMediaType(responseHeadersNode.path("content-type").asText(null));
            response.setStatus(responseNode.path("code").asText("200"));
            response.setFault(response.getStatus().startsWith("4") || response.getStatus().startsWith("5"));

            //
            String dispatchCriteria = null;
            String resourcePathPattern = operation.getName().split(" ")[1];

            if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())) {
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operation.getDispatcherRules(), resourcePathPattern, url);
            }
            response.setDispatchCriteria(dispatchCriteria);

            results.add(new RequestResponsePair(request, response));
         }
      }
      return results;
   }
   protected static List<UnidirectionalEvent> parseUnidirectionalEventTemplatizedOutput(String content) throws Exception {
      List<UnidirectionalEvent> results = new ArrayList<>();

      return results;
   }

   private static Set<Header> buildHeaders(JsonNode headersNode) {
      Set<Header> headers = new HashSet<>();
      Iterator<Map.Entry<String, JsonNode>> headerNodes = headersNode.fields();
      while (headerNodes.hasNext()) {
         Map.Entry<String, JsonNode> headerNodeEntry = headerNodes.next();
         Header header = new Header();
         header.setName(headerNodeEntry.getKey());
         header.setValues(Set.of(headerNodeEntry.getValue().textValue()));
         headers.add(header);
      }
      return headers;
   }

   private static String getMessageContent(JsonNode headersNode, JsonNode bodyNode) throws Exception {
      String contentType = headersNode.path("content-type").asText(null);

      if (!bodyNode.isMissingNode()) {
         if (contentType != null && contentType.contains("application/json")) {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(bodyNode);
         } else if (bodyNode.asText().length() > 0 && !bodyNode.asText().equals("null")) {
            return bodyNode.asText();
         }
      }
      return null;
   }

   /** Follow the $ref if we have one. Otherwise return given node. */
   private static JsonNode followRefIfAny(JsonNode spec, JsonNode referencableNode) {
      if (referencableNode.has("$ref")) {
         String ref = referencableNode.path("$ref").asText();
         return getNodeForRef(spec, ref);
      }
      return referencableNode;
   }

   private static JsonNode getNodeForRef(JsonNode spec, String reference) {
      return spec.at(reference.substring(1));
   }
}
