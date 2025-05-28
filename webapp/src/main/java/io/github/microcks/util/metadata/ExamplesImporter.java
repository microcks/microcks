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

package io.github.microcks.util.metadata;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Message;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.URIBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Mock repository importer that uses a {@code ApiExamples} YAML descriptor as a source artifact.
 * @author laurent
 */
public class ExamplesImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ExamplesImporter.class);

   private static final String REQUEST_NODE = "request";
   private static final String RESPONSE_NODE = "response";
   private static final String PARAMETERS_NODE = "parameters";
   private static final String HEADERS_NODE = "headers";
   private static final String BODY_NODE = "body";
   private static final String PAYLOAD_NODE = "payload";

   private final ObjectMapper mapper;
   private final JsonNode spec;

   /**
    * Build a new importer.
    * @param specificationFilePath The path to local APIExamples spec file
    * @throws IOException if project file cannot be found or read.
    */
   public ExamplesImporter(String specificationFilePath) throws IOException {
      try {
         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(specificationFilePath));
         String specContent = new String(bytes, StandardCharsets.UTF_8);

         // Convert them to Node using Jackson object mapper.
         mapper = new ObjectMapper(new YAMLFactory());
         spec = mapper.readTree(specContent.getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
         log.error("Exception while parsing APIMetadata specification file " + specificationFilePath, e);
         throw new IOException("APIMetadata spec file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();

      JsonNode metadataNode = spec.get("metadata");
      if (metadataNode == null) {
         log.error("Missing mandatory metadata in {}", spec.asText());
         throw new MockRepositoryImportException("Mandatory metadata property is missing in APIMetadata");
      }
      service.setName(metadataNode.path("name").asText());
      service.setVersion(metadataNode.path("version").asText());

      // Then build its operations.
      service.setOperations(extractOperations());

      result.add(service);

      return result;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException {
      return Collections.emptyList();
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      List<Exchange> result = new ArrayList<>();

      // Iterate on specification "operations" nodes.
      Iterator<Map.Entry<String, JsonNode>> operations = spec.path("operations").fields();
      while (operations.hasNext()) {
         Map.Entry<String, JsonNode> operationEntry = operations.next();

         // Select the matching operation.
         if (operation.getName().equals(operationEntry.getKey())) {
            // Browse examples and extract exchanges.
            Iterator<Map.Entry<String, JsonNode>> examples = operationEntry.getValue().fields();
            while (examples.hasNext()) {
               Map.Entry<String, JsonNode> exampleEntry = examples.next();

               // Extract and add this exchange.
               result.add(extractExchange(service, operation, exampleEntry.getKey(), exampleEntry.getValue()));
            }
         }
      }
      return result;
   }

   /** Extract the list of operations from Specification. */
   private List<Operation> extractOperations() {
      List<Operation> results = new ArrayList<>();

      // Iterate on specification "operations" nodes.
      Iterator<Map.Entry<String, JsonNode>> operations = spec.path("operations").fields();
      while (operations.hasNext()) {
         Map.Entry<String, JsonNode> operation = operations.next();

         // Build a new operation.
         Operation op = new Operation();
         op.setName(operation.getKey());

         results.add(op);
      }
      return results;
   }

   /** Extract exchange information from an example node. */
   private Exchange extractExchange(Service service, Operation operation, String exampleName, JsonNode exampleNode) {
      Exchange exchange;
      if (ServiceType.EVENT.equals(service.getType())) {
         exchange = extractUnidirectionalEvent(exampleName, exampleNode);
      } else {
         exchange = extractRequestResponsePair(service, operation, exampleName, exampleNode);
      }
      return exchange;
   }

   /** Extract unidirectional event messages from an example node. */
   private UnidirectionalEvent extractUnidirectionalEvent(String exampleName, JsonNode exampleNode) {
      if (exampleNode.has("eventMessage")) {
         JsonNode eventNode = exampleNode.get("eventMessage");

         EventMessage event = new EventMessage();
         event.setName(exampleName);

         if (eventNode.has(HEADERS_NODE)) {
            completeWithHeaders(event, eventNode.get(HEADERS_NODE));
         }
         event.setContent(getSerializedValue(eventNode.get(PAYLOAD_NODE)));

         return new UnidirectionalEvent(event);
      }
      return null;
   }

   /** Extract request/response pair from an example node. */
   private RequestResponsePair extractRequestResponsePair(Service service, Operation operation, String exampleName,
         JsonNode exampleNode) {
      // Build and return something only if request and response.
      if (exampleNode.has(REQUEST_NODE) && exampleNode.has(RESPONSE_NODE)) {
         JsonNode requestNode = exampleNode.get(REQUEST_NODE);
         JsonNode responseNode = exampleNode.get(RESPONSE_NODE);

         // Initialize and complete the request.
         Request request = new Request();
         request.setName(exampleName);
         request.setContent(getSerializedValue(requestNode.get(BODY_NODE)));

         Multimap<String, String> parameters = null;
         if (requestNode.has(PARAMETERS_NODE)) {
            parameters = completeWithParameters(request, requestNode.get(PARAMETERS_NODE));
         }
         if (requestNode.has(HEADERS_NODE)) {
            completeWithHeaders(request, requestNode.get(HEADERS_NODE));
         }

         // Initialize and complete the response.
         Response response = new Response();
         response.setName(exampleName);
         // Default response status
         response.setStatus("200");

         if (responseNode.has(HEADERS_NODE)) {
            completeWithHeaders(response, responseNode.get(HEADERS_NODE));
         }
         if (responseNode.has("mediaType")) {
            String mediaType = responseNode.get("mediaType").asText();
            response.setMediaType(mediaType);
         }
         if (responseNode.has("status")) {
            String status = responseNode.get("status").asText();
            response.setStatus(status);
            if (!status.startsWith("2") || !status.startsWith("3")) {
               response.setFault(true);
            }
         }
         response.setContent(getSerializedValue(responseNode.get(BODY_NODE)));

         // Finally, take care about dispatchCriteria and complete operation resourcePaths.
         // If we previously override the dispatcher with a Fallback, we must be sure to get wrapped elements.
         DispatchCriteriaHelper.DispatcherDetails details = DispatchCriteriaHelper
               .extractDispatcherWithRules(operation);

         // Finally, take care about dispatchCriteria and complete operation resourcePaths.
         completeDispatchCriteriaAndResourcePaths(service, operation, details.rootDispatcher(),
               details.rootDispatcherRules(), parameters, requestNode, responseNode, request, response);

         return new RequestResponsePair(request, response);
      }
      return null;
   }

   /** Complete a request by extracting parameters. */
   private Multimap<String, String> completeWithParameters(Request request, JsonNode parametersNode) {
      Multimap<String, String> result = ArrayListMultimap.create();
      Iterator<Map.Entry<String, JsonNode>> parameters = parametersNode.fields();
      while (parameters.hasNext()) {
         Map.Entry<String, JsonNode> parameterNode = parameters.next();

         Parameter parameter = new Parameter();
         parameter.setName(parameterNode.getKey());
         parameter.setValue(getSerializedValue(parameterNode.getValue()));
         request.addQueryParameter(parameter);

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

   /** Complete a message by extracting headers. */
   private void completeWithHeaders(Message message, JsonNode headersNode) {
      Iterator<Map.Entry<String, JsonNode>> headers = headersNode.fields();
      while (headers.hasNext()) {
         Map.Entry<String, JsonNode> headerNode = headers.next();

         Header header = new Header();
         header.setName(headerNode.getKey());
         // Header value may be multiple CSV.
         Set<String> values = Arrays.stream(getSerializedValue(headerNode.getValue()).split(",")).map(String::trim)
               .collect(Collectors.toSet());
         header.setValues(values);

         message.addHeader(header);
      }
   }

   /** */
   private void completeDispatchCriteriaAndResourcePaths(Service service, Operation operation, String rootDispatcher,
         String rootDispatcherRules, Multimap<String, String> parameters, JsonNode requestNode, JsonNode responseNode,
         Request request, Response response) {
      String dispatchCriteria = null;
      String resourcePathPattern = operation.getName().contains(" ") ? operation.getName().split(" ")[1]
            : operation.getName();

      if (DispatchStyles.URI_PARAMS.equals(rootDispatcher)) {
         dispatchCriteria = DispatchCriteriaHelper.buildFromParamsMap(rootDispatcherRules, parameters);
         // We only need the pattern here.
         operation.addResourcePath(resourcePathPattern);
      } else if (DispatchStyles.URI_PARTS.equals(rootDispatcher)) {
         dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parameters);
         // We should complete resourcePath here.
         String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, parameters);
         operation.addResourcePath(resourcePath);
      } else if (DispatchStyles.URI_ELEMENTS.equals(rootDispatcher)) {
         // Split parameters between path and query.
         Multimap<String, String> pathParameters = Multimaps.filterEntries(parameters,
               entry -> operation.getName().contains("/{" + entry.getKey() + "}")
                     || operation.getName().contains("/:" + entry.getKey()));
         Multimap<String, String> queryParameters = Multimaps.filterEntries(parameters,
               entry -> !pathParameters.containsKey(entry.getKey()));

         dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, pathParameters);
         dispatchCriteria += DispatchCriteriaHelper.buildFromParamsMap(rootDispatcherRules, queryParameters);
         // We should complete resourcePath here.
         String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, parameters);
         operation.addResourcePath(resourcePath);
      } else if (DispatchStyles.QUERY_HEADER.equals(rootDispatcher)) {
         if (requestNode.has(HEADERS_NODE)) {
            JsonNode headersNode = requestNode.get(HEADERS_NODE);
            Map<String, String> headersMap = extractHeaders(headersNode);
            log.debug("Headers map for dispatchCriteria computation: {}", headersMap);
            dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(rootDispatcherRules, headersMap);
         } else {
            log.debug("Missing headers node in request node");
         }
      } else if (DispatchStyles.QUERY_ARGS.equals(rootDispatcher)) {
         if (ServiceType.GRAPHQL.equals(service.getType()) && requestNode.has(BODY_NODE)) {
            JsonNode variables = requestNode.get(BODY_NODE).path("variables");
            dispatchCriteria = extractQueryArgsCriteria(rootDispatcherRules, variables);
         } else if (ServiceType.GRPC.equals(service.getType())) {
            dispatchCriteria = extractQueryArgsCriteria(rootDispatcherRules, request.getContent());
         }
      } else if (responseNode.has("dispatchCriteria")) {
         dispatchCriteria = responseNode.get("dispatchCriteria").asText();
      }

      // In any case (dispatcher forced via Metadata or set to SCRIPT, we should still put a generic resourcePath
      // (maybe containing {} parts) to later force operation matching at the mock controller level.
      operation.addResourcePath(resourcePathPattern);

      response.setDispatchCriteria(dispatchCriteria);
   }

   /** Extract the QUERY_ARGS Dispatch criteria from the variable provided as JSON string representation. */
   private String extractQueryArgsCriteria(String dispatcherRules, JsonNode variables) {
      String dispatchCriteria = "";
      try {
         Map<String, String> paramsMap = mapper.convertValue(variables,
               TypeFactory.defaultInstance().constructMapType(TreeMap.class, String.class, String.class));
         dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(dispatcherRules, paramsMap);
      } catch (Exception e) {
         log.error("Exception while converting dispatch criteria from JSON body: {}", e.getMessage());
      }
      return dispatchCriteria;
   }

   /** Extract the QUERY_ARGS Dispatch criteria from the variable provided as JSON string representation. */
   private String extractQueryArgsCriteria(String dispatcherRules, String variables) {
      String dispatchCriteria = "";
      try {
         Map<String, String> paramsMap = mapper.readValue(variables,
               TypeFactory.defaultInstance().constructMapType(TreeMap.class, String.class, String.class));
         dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(dispatcherRules, paramsMap);
      } catch (Exception e) {
         log.error("Exception while extracting dispatch criteria from JSON body: {}", e.getMessage());
      }
      return dispatchCriteria;
   }

   /** Get the serialized value of a content node, or null if node is null ;-) */
   private String getSerializedValue(JsonNode contentNode) {
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
   private Map<String, String> extractHeaders(JsonNode headersNode) {
      Map<String, String> result = new HashMap<>();
      Iterator<Map.Entry<String, JsonNode>> headers = headersNode.fields();
      while (headers.hasNext()) {
         Map.Entry<String, JsonNode> headerNode = headers.next();
         result.put(headerNode.getKey(), getSerializedValue(headerNode.getValue()));
      }
      return result;
   }
}
