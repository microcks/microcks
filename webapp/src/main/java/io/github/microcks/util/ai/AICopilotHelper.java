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
import io.github.microcks.util.ObjectMapperFactory;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamWriteConstraints;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

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
   private static final Logger log = LoggerFactory.getLogger(AICopilotHelper.class);

   protected static final ObjectMapper JSON_MAPPER = new ObjectMapper();
   protected static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

   protected static final String REALISTIC_AND_VALID_EXAMPLES_PROMPT = """
         Generated examples must be realistic illustrations on how to use the API and must be valid regarding the schema definition.
         """;

   protected static final String OPENAPI_OPERATION_PROMPT_TEMPLATE = """
         Given the OpenAPI specification below, generate %2$d full examples (request and response) for operation '%1$s' strictly (no sub-path).
         """
         + REALISTIC_AND_VALID_EXAMPLES_PROMPT;

   protected static final String GRAPHQL_OPERATION_PROMPT_TEMPLATE = """
         Given the GraphQL schema below, generate %2$d full examples (request and response) for operation '%1$s' only.
         """ + REALISTIC_AND_VALID_EXAMPLES_PROMPT;

   protected static final String ASYNCAPI_OPERATION_PROMPT_TEMPLATE = """
         Given the AsyncAPI specification below, generate %2$d full examples for operation '%1$s' only.
         """ + REALISTIC_AND_VALID_EXAMPLES_PROMPT;

   protected static final String GRPC_OPERATION_PROMPT_TEMPLATE = """
         Given the gRPC protobuf schema below, generate %3$d full examples (request and response) for operation '%2$s' of service '%1$s'.
         """
         + REALISTIC_AND_VALID_EXAMPLES_PROMPT;

   protected static final String YAML_FORMATTING_PROMPT = """
         Use only the provided YAML format to output the list of examples (no other text or markdown):
         """;
   protected static final String REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE = """
         - example: <meaningful example %1$d name>
           request:
             url: <request url>
             headers:
               accept: application/json
             parameters:
               <name 1>: <value 1>
             body: <request body>
           response:
             code: <response code>
             headers:
               content-type: application/json
             body: <response body>
         """;
   protected static final String UNIDIRECTIONAL_EVENT_EXAMPLE_YAML_FORMATTING_TEMPLATE = """
         - example: <meaningful example %1$d name>
           message:
             headers:
               <header_name>: <value 1>
             payload: <message payload>
         """;

   protected static final String GRPC_REQUEST_RESPONSE_EXAMPLE_YAML_FORMATTING_TEMPLATE = """
         - example: <meaningful example %1$d name>
           request:
             body: <request body in JSON>
           response:
             body: <response body in JSON>
         """;

   private static final String REQUEST_NODE = "request";
   private static final String RESPONSE_NODE = "response";
   private static final String PARAMETERS_NODE = "parameters";
   private static final String QUERY_NODE = "query";
   private static final String HEADERS_NODE = "headers";
   private static final String VARIABLES_NODE = "variables";
   private static final String BODY_NODE = "body";

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
         int index = 1;
         while (examples.hasNext()) {
            JsonNode example = examples.next();
            String name = example.path("example").asText("Sample " + index++);

            // Deal with parsing request.
            JsonNode requestNode = example.path(REQUEST_NODE);
            Request request = buildRequest(requestNode, name);

            // Deal with parsing response.
            JsonNode responseNode = example.path(RESPONSE_NODE);
            Response response = buildResponse(responseNode, name);

            // Finally, take care about dispatchCriteria and complete operation resourcePaths.
            // If we previously override the dispatcher with a Fallback, we must be sure to get wrapped elements.
            DispatchCriteriaHelper.DispatcherDetails details = DispatchCriteriaHelper
                  .extractDispatcherWithRules(operation);

            // Finally, take care about dispatchCriteria if dispatching rules are not null.
            if (details.rootDispatcherRules() != null) {
               completeDispatchCriteriaAndResourcePaths(service, operation, details.rootDispatcher(),
                     details.rootDispatcherRules(), requestNode, request, response);
            }

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

   private static Request buildRequest(JsonNode requestNode, String name) throws Exception {
      Request request = new Request();
      request.setName(name);
      JsonNode requestHeadersNode = requestNode.path(HEADERS_NODE);
      request.setHeaders(buildHeaders(requestHeadersNode));
      request.setContent(getRequestContent(requestHeadersNode, requestNode.path(BODY_NODE)));
      return request;
   }

   private static Multimap<String, String> buildRequestParameters(Request request, JsonNode parametersNode) {
      Multimap<String, String> result = ArrayListMultimap.create();
      Iterator<Map.Entry<String, JsonNode>> parameters = parametersNode.fields();
      while (parameters.hasNext()) {
         Map.Entry<String, JsonNode> parameterNode = parameters.next();

         // Depending on node type, extract different representations to MultiMap result.
         if (parameterNode.getValue().isArray()) {
            for (JsonNode current : parameterNode.getValue()) {
               result.put(parameterNode.getKey(), getSerializedValue(current));
            }
         } else if (parameterNode.getValue().isObject()) {
            final var fieldsIterator = parameterNode.getValue().fields();
            while (fieldsIterator.hasNext()) {
               var current = fieldsIterator.next();
               result.put(current.getKey(), getSerializedValue(current.getValue()));
            }
         } else {
            result.put(parameterNode.getKey(), getSerializedValue(parameterNode.getValue()));
         }
      }
      return result;
   }

   private static Response buildResponse(JsonNode responseNode, String name) throws Exception {
      Response response = new Response();
      response.setName(name);
      JsonNode responseHeadersNode = responseNode.path(HEADERS_NODE);
      response.setHeaders(buildHeaders(responseHeadersNode));
      response.setContent(getResponseContent(responseHeadersNode, responseNode.path(BODY_NODE)));
      response.setMediaType(responseHeadersNode.path("content-type").asText(null));
      response.setStatus(responseNode.path("code").asText("200"));
      response.setFault(response.getStatus().startsWith("4") || response.getStatus().startsWith("5"));
      return response;
   }

   private static void completeDispatchCriteriaAndResourcePaths(Service service, Operation operation,
         String rootDispatcher, String rootDispatcherRules, JsonNode requestNode, Request request, Response response)
         throws Exception {

      String dispatchCriteria = null;
      String resourcePathPattern = operation.getName().contains(" ") ? operation.getName().split(" ")[1]
            : operation.getName();

      // Extract parameters if LLM provided some.
      Multimap<String, String> parameters = null;
      if (requestNode.has(PARAMETERS_NODE)) {
         parameters = buildRequestParameters(request, requestNode.get(PARAMETERS_NODE));
      } else {
         parameters = ArrayListMultimap.create();
      }
      // Extract parameters from URL if LLM provided some.
      if (requestNode.has("url")) {
         String url = requestNode.get("url").asText();
         Map<String, String> pathParameters = DispatchCriteriaHelper.extractMapFromURIPattern(rootDispatcherRules,
               resourcePathPattern, url);
         Multimap<String, String> queryParameters = DispatchCriteriaHelper.extractMapFromURIParams(rootDispatcherRules,
               url);

         // Complete parameter map with both content.
         for (Map.Entry<String, String> entry : pathParameters.entrySet()) {
            if (!parameters.containsKey(entry.getKey())) {
               parameters.put(entry.getKey(), entry.getValue());
            }
         }
         for (Map.Entry<String, String> entry : queryParameters.entries()) {
            if (!parameters.containsKey(entry.getKey())) {
               parameters.put(entry.getKey(), entry.getValue());
            }
         }
      }

      // Now that we should have all parameters, we can complete the
      for (Map.Entry<String, String> entry : parameters.entries()) {
         Parameter parameter = new Parameter();
         parameter.setName(entry.getKey());
         parameter.setValue(entry.getValue());
         request.addQueryParameter(parameter);
      }

      // Compute a dispatch criteria based on dispatcher and dispatcher rules but
      // don't update the operation resource path as the request/response has not been reviewed by user yet.
      if (DispatchStyles.URI_PARAMS.equals(rootDispatcher)) {
         dispatchCriteria = DispatchCriteriaHelper.buildFromParamsMap(rootDispatcherRules, parameters);
      } else if (DispatchStyles.URI_PARTS.equals(rootDispatcher)) {
         dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parameters);
      } else if (DispatchStyles.URI_ELEMENTS.equals(rootDispatcher)) {
         dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parameters);
         dispatchCriteria += DispatchCriteriaHelper.buildFromParamsMap(rootDispatcherRules, parameters);
      } else if (DispatchStyles.QUERY_HEADER.equals(rootDispatcher)) {
         if (requestNode.has(HEADERS_NODE)) {
            JsonNode headersNode = requestNode.get(HEADERS_NODE);
            Map<String, String> headersMap = extractHeaders(headersNode);
            dispatchCriteria = DispatchCriteriaHelper.buildFromParamsMap(rootDispatcherRules, parameters);
         }
      } else if (DispatchStyles.QUERY_ARGS.equals(rootDispatcher)) {
         // This dispatcher is used for GraphQL or gRPC
         if (ServiceType.GRAPHQL.equals(service.getType())) {
            Map<String, String> variables = getGraphQLVariables(request.getContent());
            dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(operation.getDispatcherRules(), variables);
         } else if (ServiceType.GRPC.equals(service.getType())) {
            Map<String, String> bodyAsMap = JSON_MAPPER.readValue(request.getContent(),
                  TypeFactory.defaultInstance().constructMapType(TreeMap.class, String.class, String.class));
            dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(operation.getDispatcherRules(), bodyAsMap);
         }
      }
      response.setDispatchCriteria(dispatchCriteria);
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

   /** Get the serialized value of a content node, or null if node is null ;-) */
   private static String getSerializedValue(JsonNode contentNode) {
      if (contentNode != null) {
         // Get string representation if array or object.
         if (contentNode.getNodeType() == JsonNodeType.ARRAY || contentNode.getNodeType() == JsonNodeType.OBJECT) {
            return contentNode.toString();
         }
         // Else get raw representation.
         return contentNode.asText();
      }
      return null;
   }

   /** Extract headers from a node to be processed for dispatch criteria computing. */
   private static Map<String, String> extractHeaders(JsonNode headersNode) {
      Map<String, String> result = new HashMap<>();
      Iterator<Map.Entry<String, JsonNode>> headers = headersNode.fields();
      while (headers.hasNext()) {
         Map.Entry<String, JsonNode> headerNode = headers.next();
         result.put(headerNode.getKey(), getSerializedValue(headerNode.getValue()));
      }
      return result;
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
      return Collections.emptyMap();
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
      // Parse and remove unrelated operations from the spec.
      JsonNode specNode = parseAndFilterSpec(specification, operationName);

      Map<String, JsonNode> resolvedReferences = new HashMap<>();
      try {
         // 1.5MB is a good threshold to avoid too many references resolution.
         if (specification.length() < 1500000) {
            resolveReferenceAndRemoveTokensInNode(specNode, specNode, new HashMap<>(), new HashSet<>(), true);
         } else {
            resolveReferenceAndRemoveTokensInNode(specNode, specNode, resolvedReferences, new HashSet<>(), false);
         }
         return filterAndSerializeSpec(specification, specNode,
               resolvedReferences.keySet().stream().filter(ref -> ref.startsWith("#/components/schemas"))
                     .map(ref -> ref.substring("#/components/schemas".length() + 1)).toList());
      } catch (Throwable t) {
         log.error(
               "Exception while resolving references in spec. This is likely due to circular or too many references in the spec: {}",
               t.getMessage());
         log.error("Trying with the raw spec...");
         specNode = parseAndFilterSpec(specification, operationName);
         return filterAndSerializeSpec(specification, specNode, List.of());
      }
   }

   /** */
   protected static JsonNode parseAndFilterSpec(String specification, String operationName) throws Exception {
      // Parse the specification to a JsonNode.
      boolean isJson = specification.trim().startsWith("{");
      JsonNode specNode;
      if (isJson) {
         specNode = JSON_MAPPER.readTree(specification);
      } else {
         specNode = ObjectMapperFactory.getYamlObjectMapper().readTree(specification);
      }

      // Filter the operation to avoid resolving references on the whole spec.
      List<String> specTokenNames = getTokenNames(specNode);
      if (specTokenNames.contains("openapi")) {
         filterOpenAPISpecOnOperation(specNode, operationName);
      } else if (specTokenNames.contains("asyncapi")) {
         filterAsyncAPISpecOnOperation(specNode, operationName);
      }
      return specNode;
   }

   /** */
   protected static String filterAndSerializeSpec(String originalSpec, JsonNode specNode, List<String> schemasToKeep)
         throws IOException {
      // Remove schemas if not in the list of components/schemas to keep.
      if (!schemasToKeep.isEmpty() && specNode.has("components")) {
         ObjectNode componentsNode = (ObjectNode) specNode.get("components");
         if (componentsNode.has("schemas")) {
            ObjectNode schemasNode = (ObjectNode) componentsNode.get("schemas");
            removeTokensInNode(schemasNode, schemasToKeep);
         }
      } else {
         ((ObjectNode) specNode).remove("components");
      }

      // Filter the remaining elements in spec.
      List<String> specTokenNames = getTokenNames(specNode);
      if (specTokenNames.contains("openapi")) {
         filterOpenAPISpec(specNode);
      } else if (specTokenNames.contains("asyncapi")) {
         filterAsyncAPISpec(specNode);
      }

      // Serialize the spec back to a string.
      boolean isJson = originalSpec.trim().startsWith("{");
      if (isJson) {
         return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(specNode);
      }
      return ObjectMapperFactory.getYamlObjectMapper().writeValueAsString(specNode);
   }

   protected static void resolveReferenceAndRemoveTokensInNode(JsonNode specNode, JsonNode node,
         Map<String, JsonNode> resolvedReferences, Set<String> resolvingReferences) {
      resolveReferenceAndRemoveTokensInNode(specNode, node, resolvedReferences, resolvingReferences, true);
   }

   protected static void resolveReferenceAndRemoveTokensInNode(JsonNode specNode, JsonNode node,
         Map<String, JsonNode> resolvedReferences, Set<String> resolvingReferences, boolean perBranchResolution) {
      // Are we currently resolving a reference?
      String resolvingRef = null;
      boolean resolveRef = false;
      boolean skipChildren = false;

      if (node.getNodeType() == JsonNodeType.OBJECT) {
         if (node.has("$ref")) {
            resolvingRef = node.path("$ref").asText();

            // The reference may have already been resolved globally.
            JsonNode target = resolvedReferences.get(resolvingRef);
            if (target != null) {
               // Dereference the $ref and merge the target into the node.
               ((ObjectNode) node).setAll((ObjectNode) target);
               ((ObjectNode) node).remove("$ref");
               // Mark to skip children as we have already processed the target.
               skipChildren = true;
            } else {
               // Check the reference is not currently tried to be resolved.
               // In this case, we are in a circular reference situation, just let the $ref in place if it is not already resolved.
               if (!resolvingReferences.contains(resolvingRef)) {
                  target = getNodeForRef(specNode, resolvingRef);
                  if (target != null) {
                     // Dereference the $ref and merge the target into the node.
                     ((ObjectNode) node).setAll((ObjectNode) target);
                     ((ObjectNode) node).remove("$ref");
                     // Add the resolving reference to the set of currently resolving references.
                     resolvingReferences.add(resolvingRef);
                     resolveRef = true;
                  } else {
                     log.warn("Reference '{}' could not be resolved in the spec.", resolvingRef);
                  }
               }
            }
         }

         // Remove common fields that are not needed in the context of AI copilot.
         removeTokensInNode((ObjectNode) node);

         Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
         while (fields.hasNext() && !skipChildren) {
            Map.Entry<String, JsonNode> field = fields.next();
            // Do not browse the root specNode components.
            // Do not recurse on un-resolved $ref field as it has been already processed.
            if ((specNode == node && !"components".equals(field.getKey()))
                  || (specNode != node && !"$ref".equals(field.getKey()))) {
               resolveReferenceAndRemoveTokensInNode(specNode, field.getValue(), resolvedReferences,
                     resolvingReferences, perBranchResolution);
            }
         }
      } else if (node.getNodeType() == JsonNodeType.ARRAY) {
         // If node is an array, we need to iterate over its elements.
         Iterator<JsonNode> elements = node.elements();
         while (elements.hasNext()) {
            resolveReferenceAndRemoveTokensInNode(specNode, elements.next(), resolvedReferences, resolvingReferences,
                  perBranchResolution);
         }
      }

      // Track the resolved reference if we were currently resolving one.
      if (resolvingRef != null && resolveRef) {
         // If we are in per-branch resolution mode, we can pop the resolving reference from the set of resolved references.
         if (perBranchResolution) {
            resolvingReferences.remove(resolvingRef);
         }
         // In any case, save the resolved reference in the map of resolved references.
         resolvedReferences.put(resolvingRef, node);
      }
   }

   protected static void removeTokensInNode(ObjectNode node) {
      // Remove common tokens that are not needed in the context of AI copilot.
      node.remove("examples");
      node.remove("example");
      node.remove("x-microcks");
      node.remove("x-microcks-operation");
   }

   protected static void filterOpenAPISpecOnOperation(JsonNode specNode, String operationName) throws Exception {
      String[] operationPathName = operationName.split(" ");
      String verb = operationPathName[0].toLowerCase();
      String path = operationPathName[1];

      JsonNode pathsSpec = specNode.get("paths");
      JsonNode pathSpec = pathsSpec.get(path);

      removeTokensInNode(pathsSpec, List.of(path));
      removeTokensInNode(pathSpec, List.of(verb));
      removeSecurityTokenInNode(pathSpec, verb);
   }

   protected static void filterOpenAPISpec(JsonNode specNode) {
      List<String> keysToKeepInRoot = List.of("openapi", "paths", "info", "components");
      removeTokensInNode(specNode, keysToKeepInRoot);
   }

   protected static void filterAsyncAPISpecOnOperation(JsonNode specNode, String channelName) {
      String[] operationPathName = channelName.split(" ");
      String channel = operationPathName[1];

      JsonNode channelsSpec = ((ObjectNode) specNode).get("channels");
      removeTokensInNode(channelsSpec, List.of(channel));
   }

   protected static void filterAsyncAPISpec(JsonNode specNode) {
      List<String> keysToKeepInRoot = List.of("asyncapi", "channels", "info", "components");
      removeTokensInNode(specNode, keysToKeepInRoot);
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
