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
package io.github.microcks.util.ai;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This helper class holds general utility constants and methods for:
 * <ul>
 * <li>interacting with LLM (prompt template, formatting specifications</li>
 * <li>parsing the output of LLM interaction (converting prompt templates to Microcks domain model)</li>
 * </ul>
 * It is intended to be use by {@code AICopilot} implementations so that they can focus on configuration, connection and
 * prompt refinement concerns.
 * @author laurent
 */
public class AICopilotHelper {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(AICopilotHelper.class);

   protected static final ObjectMapper JSON_MAPPER = new ObjectMapper();
   protected static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

   protected static final String OPENAPI_OPERATION_PROMPT_TEMPLATE = """
         Given the OpenAPI specification below, generate %2$d full examples (request and response) for operation '%1$s' strictly (no sub-path).
         """;

   protected static final String GRAPHQL_OPERATION_PROMPT_TEMPLATE = """
         Given the GraphQL schema below, generate %2$d full examples (request and response) for operation '%1$s' only.
         """;

   protected static final String ASYNCAPI_OPERATION_PROMPT_TEMPLATE = """
         Given the AsyncAPI specification below, generate %2$d full examples for operation '%1$s' only.
         """;

   protected static final String GRPC_OPERATION_PROMPT_TEMPLATE = """
         Given the gRPC protobuf schema below, generate %3$d full examples (request and response) for operation '%2$s' of service '%1$s'.
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

   protected static final String UNIDIRECTIONAL_EVENT_EXAMPLE_YAML_FORMATTING_TEMPLATE = """
         - example: %1$d
           message:
             headers:
               <header_name>: <value 1>
             payload: <message payload>
         """;

   protected static final String GRPC_REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE = """
         - example: %1$d
           request:
             body: <request body in JSON>
           response:
             body: <response body in JSON>
         """;

   private static final String QUERY_NODE = "query";
   private static final String HEADERS_NODE = "headers";
   private static final String VARIABLES_NODE = "variables";

   private AICopilotHelper() {
      // Hides the default implicit one as it's a utility class.
   }

   /**
    * Generate an OpenAPI prompt introduction, asking for generation of {@code numberOfSamples} samples for operation.
    */
   protected static String getOpenAPIOperationPromptIntro(String operationName, int numberOfSamples) {
      return String.format(OPENAPI_OPERATION_PROMPT_TEMPLATE, operationName, numberOfSamples);
   }

   /**
    * Generate a GraphQL prompt introduction, asking for generation of {@code numberOfSamples} samples for operation.
    */
   protected static String getGraphQLOperationPromptIntro(String operationName, int numberOfSamples) {
      return String.format(GRAPHQL_OPERATION_PROMPT_TEMPLATE, operationName, numberOfSamples);
   }

   /**
    * Generate an AsyncAPI prompt introduction, asking for generation of {@code numberOfSamples} samples for operation.
    */
   protected static String getAsyncAPIOperationPromptIntro(String operationName, int numberOfSamples) {
      return String.format(ASYNCAPI_OPERATION_PROMPT_TEMPLATE, operationName, numberOfSamples);
   }

   /** Generate a GRPC prompt introduction, asking for generation of {@code numberOfSamples} samples for operation. */
   protected static String getGrpcOperationPromptIntro(String serviceName, String operationName, int numberOfSamples) {
      return String.format(GRPC_OPERATION_PROMPT_TEMPLATE, serviceName, operationName, numberOfSamples);
   }

   protected static String getRequestResponseExampleYamlFormattingDirective(int numberOfSamples) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < numberOfSamples; i++) {
         builder.append(String.format(REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE, i + 1));
      }
      return builder.toString();
   }

   protected static String getUnidirectionalEventExampleYamlFormattingDirective(int numberOfSamples) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < numberOfSamples; i++) {
         builder.append(String.format(UNIDIRECTIONAL_EVENT_EXAMPLE_YAML_FORMATTING_TEMPLATE, i + 1));
      }
      return builder.toString();
   }

   protected static String getGrpcRequestResponseExampleYamlFormattingDirective(int numberOfSamples) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < numberOfSamples; i++) {
         builder.append(String.format(GRPC_REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE, i + 1));
      }
      return builder.toString();
   }

   /**
    * Transform the output respecting the {@code REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE} into Microcks domain
    * exchanges.
    */
   protected static List<RequestResponsePair> parseRequestResponseTemplateOutput(Service service, Operation operation,
         String content) throws Exception {
      List<RequestResponsePair> results = new ArrayList<>();

      JsonNode root = YAML_MAPPER.readTree(sanitizeYamlContent(content));
      if (root.getNodeType() == JsonNodeType.ARRAY) {
         Iterator<JsonNode> examples = root.elements();
         while (examples.hasNext()) {
            JsonNode example = examples.next();

            // Deal with parsing request.
            JsonNode requestNode = example.path("request");
            Request request = new Request();
            JsonNode requestHeadersNode = requestNode.path(HEADERS_NODE);
            request.setHeaders(buildHeaders(requestHeadersNode));
            request.setContent(getRequestContent(requestHeadersNode, requestNode.path("body")));

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
            JsonNode responseHeadersNode = responseNode.path(HEADERS_NODE);
            response.setHeaders(buildHeaders(responseHeadersNode));
            response.setContent(getResponseContent(responseHeadersNode, responseNode.path("body")));
            response.setMediaType(responseHeadersNode.path("content-type").asText(null));
            response.setStatus(responseNode.path("code").asText("200"));
            response.setFault(response.getStatus().startsWith("4") || response.getStatus().startsWith("5"));

            //
            String dispatchCriteria = null;

            if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())) {
               String resourcePathPattern = operation.getName().split(" ")[1];
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operation.getDispatcherRules(),
                     resourcePathPattern, url);
            } else if (DispatchStyles.URI_PARAMS.equals(operation.getDispatcher())) {
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(), url);
            } else if (DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
               String resourcePathPattern = operation.getName().split(" ")[1];
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operation.getDispatcherRules(),
                     resourcePathPattern, url);
               dispatchCriteria += DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(), url);
            } else if (DispatchStyles.QUERY_ARGS.equals(operation.getDispatcher())) {
               // This dispatcher is used for GraphQL
               Map<String, String> variables = getGraphQLVariables(request.getContent());
               dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(operation.getDispatcherRules(), variables);
            }
            response.setDispatchCriteria(dispatchCriteria);

            if (service.getType() == ServiceType.GRAPHQL) {
               adaptGraphQLRequestContent(request);
            }

            results.add(new RequestResponsePair(request, response));
         }
      }
      return results;
   }

   /**
    * Transform the output respecting the {@code UNIDIRECTIONAL_EVENT_EXAMPLE_YAML_FORMATTING_TEMPLATE} into Microcks
    * domain exchanges.
    */
   protected static List<UnidirectionalEvent> parseUnidirectionalEventTemplateOutput(String content) throws Exception {
      List<UnidirectionalEvent> results = new ArrayList<>();

      JsonNode root = YAML_MAPPER.readTree(sanitizeYamlContent(content));
      if (root.getNodeType() == JsonNodeType.ARRAY) {
         Iterator<JsonNode> examples = root.elements();
         while (examples.hasNext()) {
            JsonNode example = examples.next();

            // Deal with parsing message.
            JsonNode message = example.path("message");
            EventMessage event = new EventMessage();
            JsonNode headersNode = message.path(HEADERS_NODE);
            event.setHeaders(buildHeaders(headersNode));
            event.setMediaType("application/json");
            event.setContent(getMessageContent("application/json", message.path("payload")));

            results.add(new UnidirectionalEvent(event));
         }
      }
      return results;
   }

   /** Sanitize the pseudo Yaml sometimes returned into plain valid Yaml. */
   private static String sanitizeYamlContent(String pseudoYaml) {
      pseudoYaml = pseudoYaml.trim();
      if (!pseudoYaml.startsWith("-")) {
         boolean inYaml = false; // Are we currently in Yaml content?
         boolean nextIsYaml = false; // May the next line be Yaml content?
         boolean addPadding = false; // Do we have to add padding?
         StringBuilder yaml = new StringBuilder();
         String[] lines = pseudoYaml.split("\\r?\\n|\\r");
         for (String line : lines) {
            if (line.startsWith("-")) {
               inYaml = true;
            }
            if (line.trim().length() == 0) {
               inYaml = false;
            }
            if (nextIsYaml && !line.startsWith("-")) {
               inYaml = true;
               nextIsYaml = false;
               addPadding = true;
               yaml.append("- ").append(line).append("\n");
               continue;
            }
            if (line.startsWith("```")) {
               // Starting or ending markdown block.
               if (inYaml) {
                  inYaml = false;
                  nextIsYaml = false;
                  addPadding = false;
               } else {
                  nextIsYaml = true;
               }
            }
            if (inYaml) {
               yaml.append(addPadding ? "  " : "").append(line).append("\n");
               // We don't know what next is going to be...
               nextIsYaml = false;
            }
         }
         return yaml.toString();
      }
      return pseudoYaml;
   }

   private static Set<Header> buildHeaders(JsonNode headersNode) {
      Set<Header> headers = new HashSet<>();
      Iterator<Map.Entry<String, JsonNode>> headerNodes = headersNode.fields();
      while (headerNodes.hasNext()) {
         Map.Entry<String, JsonNode> headerNodeEntry = headerNodes.next();
         Header header = new Header();
         header.setName(headerNodeEntry.getKey());
         header.setValues(Set.of(headerNodeEntry.getValue().asText()));
         headers.add(header);
      }
      return headers;
   }

   private static String getRequestContent(JsonNode headersNode, JsonNode bodyNode) throws Exception {
      String contentType = headersNode.path("accept").asText(null);
      return getMessageContent(contentType, bodyNode);
   }

   private static String getResponseContent(JsonNode headersNode, JsonNode bodyNode) throws Exception {
      String contentType = headersNode.path("content-type").asText(null);
      return getMessageContent(contentType, bodyNode);
   }

   private static String getMessageContent(String contentType, JsonNode bodyNode) throws Exception {
      if (!bodyNode.isMissingNode()) {

         if (!bodyNode.isTextual() && !bodyNode.isEmpty()
               && (contentType == null || contentType.contains("application/json"))) {
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(bodyNode);
         } else if (bodyNode.isTextual()) {
            return bodyNode.asText();
         }
      }
      return null;
   }

   private static Map<String, String> getGraphQLVariables(String requestContent) throws Exception {
      JsonNode graphQL = JSON_MAPPER.readTree(requestContent);

      if (graphQL.has(VARIABLES_NODE)) {
         JsonNode variablesNode = graphQL.path(VARIABLES_NODE);
         Map<String, String> results = new HashMap<>();
         Set<Map.Entry<String, JsonNode>> elements = variablesNode.properties();
         for (Map.Entry<String, JsonNode> element : elements) {
            results.put(element.getKey(), element.getValue().asText());
         }
         return results;
      } else {
         log.warn("GraphQL request do not contain variables...");
      }
      return new HashMap<>();
   }

   private static void adaptGraphQLRequestContent(Request request) throws Exception {
      JsonNode graphQL = JSON_MAPPER.readTree(request.getContent());
      if (graphQL.has(QUERY_NODE)) {
         // GraphQL query may have \n we'd like to escape for better display.
         String query = graphQL.path(QUERY_NODE).asText();
         if (query.contains("\n")) {
            query = query.replace("\n", "\\n");
            ((ObjectNode) graphQL).put(QUERY_NODE, query);
            request.setContent(JSON_MAPPER.writeValueAsString(graphQL));
         }
      }
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

   /**
    * Some AI will reuse already present examples and x-microcks-operation in the spec. This method cleans a
    * specification from its examples.
    */
   protected static String removeTokensFromSpec(String specification, String operationName) throws Exception {
      JsonNode specNode;
      boolean isJson = specification.trim().startsWith("{");

      if (isJson) {
         specNode = JSON_MAPPER.readTree(specification);
      } else {
         specNode = YAML_MAPPER.readTree(specification);
      }

      // Resolve schemas and Remove examples recursively from the root.
      resolveReferenceAndRemoveTokensInNode(specNode, specNode);

      // Filter the spec
      List<String> specTokenNames = getTokenNames(specNode);
      if (specTokenNames.contains("openapi")) {
         filterOpenAPISpec(specNode, operationName);
      }
      if (specTokenNames.contains("asyncapi")) {
         filterAsyncAPISpec(specNode, operationName);
      }

      if (isJson) {
         return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(specNode);
      }

      return YAML_MAPPER.writeValueAsString(specNode);
   }

   protected static void resolveReferenceAndRemoveTokensInNode(JsonNode specNode, JsonNode node) {
      JsonNode target = followRefIfAny(specNode, node);
      if (target.getNodeType() == JsonNodeType.OBJECT) {
         if (node.has("$ref")) {
            ((ObjectNode) node).setAll((ObjectNode) target);
            ((ObjectNode) node).remove("$ref");
         }
         if (target.has("examples")) {
            ((ObjectNode) node).remove("examples");
         }
         if (target.has("example")) {
            ((ObjectNode) node).remove("example");
         }
         if (target.has("x-microcks-operation")) {
            ((ObjectNode) node).remove("x-microcks-operation");
         }
         Iterator<Map.Entry<String, JsonNode>> fields = target.fields();
         while (fields.hasNext()) {
            resolveReferenceAndRemoveTokensInNode(specNode, fields.next().getValue());
         }
      }
      if (target.getNodeType() == JsonNodeType.ARRAY) {
         Iterator<JsonNode> elements = target.elements();
         while (elements.hasNext()) {
            resolveReferenceAndRemoveTokensInNode(specNode, elements.next());
         }
      }
   }

   protected static void filterOpenAPISpec(JsonNode specNode, String operationName) {
      String[] operationPathName = operationName.split(" ");
      String verb = operationPathName[0].toLowerCase();
      String path = operationPathName[1];

      JsonNode pathsSpec = ((ObjectNode) specNode).get("paths");
      JsonNode pathSpec = ((ObjectNode) pathsSpec).get(path);

      List<String> keysToKeepInRoot = List.of("openapi", "paths", "info");
      List<String> keysToKeepInPaths = List.of(path);
      List<String> keysToKeepInPath = List.of(verb);

      removeTokensInNode(specNode, keysToKeepInRoot);
      removeTokensInNode(pathsSpec, keysToKeepInPaths);
      removeTokensInNode(pathSpec, keysToKeepInPath);
      removeSecurityTokenInNode(pathSpec, verb);
   }

   protected static void filterAsyncAPISpec(JsonNode specNode, String channelName) {
      String[] operationPathName = channelName.split(" ");
      String channel = operationPathName[1];

      JsonNode channelsSpec = ((ObjectNode) specNode).get("channels");

      List<String> keysToKeepInRoot = List.of("asyncapi", "channels", "info");
      List<String> keysToKeepInChannels = List.of(channel);

      removeTokensInNode(specNode, keysToKeepInRoot);
      removeTokensInNode(channelsSpec, keysToKeepInChannels);
   }

   protected static void removeSecurityTokenInNode(JsonNode specNode, String tokenName) {
      JsonNode verbSpec = ((ObjectNode) specNode).get(tokenName);
      if (verbSpec.has("security")) {
         ((ObjectNode) verbSpec).remove("security");
      }
   }

   protected static List<String> getTokenNames(JsonNode specNode) {
      List<String> fieldNames = new ArrayList<>();
      Iterator<String> specNodeFieldNames = specNode.fieldNames();
      while (specNodeFieldNames.hasNext()) {
         fieldNames.add(specNodeFieldNames.next());
      }
      return fieldNames;
   }

   protected static void removeTokensInNode(JsonNode specNode, List<String> keysToKeep) {
      List<String> fieldNames = getTokenNames(specNode);
      for (String fieldName : fieldNames) {
         if (!keysToKeep.contains(fieldName)) {
            ((ObjectNode) specNode).remove(fieldName);
         }
      }
   }
}
