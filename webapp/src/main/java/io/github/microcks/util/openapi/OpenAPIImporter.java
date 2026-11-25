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
package io.github.microcks.util.openapi;

import io.github.microcks.domain.CallbackInfo;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.ParameterConstraint;
import io.github.microcks.domain.ParameterLocation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.AbstractJsonRepositoryImporter;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.ReferenceResolver;
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.metadata.MetadataExtensions;
import io.github.microcks.util.metadata.MetadataExtractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * An implementation of MockRepositoryImporter that deals with OpenAPI v3.x.x specification file ; whether encoding into
 * JSON or YAML documents.
 * @author laurent
 */
public class OpenAPIImporter extends AbstractJsonRepositoryImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenAPIImporter.class);

   private static final List<String> VALID_VERBS = Arrays.asList("get", "put", "post", "delete", "options", "head",
         "patch", "trace");

   private static final String PARAMETERS_NODE = "parameters";
   private static final String PARAMETERS_QUERY_VALUE = "query";
   private static final String RESPONSES_NODE = "responses";
   private static final String CONTENT_NODE = "content";
   private static final String HEADERS_NODE = "headers";
   private static final String EXAMPLES_NODE = "examples";
   private static final String EXAMPLE_VALUE_NODE = "value";
   private static final String EXAMPLE_EXTERNAL_VALUE_NODE = "externalValue";

   private static final String X_MICROCKS_REFS = "x-microcks-refs";

   /**
    * Build a new importer.
    * @param specificationFilePath The path to local OpenAPI spec file
    * @param referenceResolver     An optional resolver for references present into the OpenAPI file
    * @throws IOException if project file cannot be found or read.
    */
   public OpenAPIImporter(String specificationFilePath, ReferenceResolver referenceResolver) throws IOException {
      super(specificationFilePath, referenceResolver);
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();
      service.setName(rootSpecification.path("info").path("title").asText());
      service.setVersion(rootSpecification.path("info").path("version").asText());
      service.setType(ServiceType.REST);

      // Complete metadata if specified via extension.
      if (rootSpecification.path("info").has(MetadataExtensions.MICROCKS_EXTENSION)) {
         Metadata metadata = new Metadata();
         MetadataExtractor.completeMetadata(metadata,
               rootSpecification.path("info").path(MetadataExtensions.MICROCKS_EXTENSION));
         service.setMetadata(metadata);
      }

      // Before extraction operations, we need to get and build external reference if we have a resolver.
      initializeReferencedResources(service);

      // Then build its operations.
      service.setOperations(extractOperations());

      result.add(service);
      return result;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = new ArrayList<>();

      // Build a suitable name.
      String name = service.getName() + "-" + service.getVersion();
      if (Boolean.TRUE.equals(isYaml)) {
         name += ".yaml";
      } else {
         name += ".json";
      }

      // Build a brand-new resource just with spec content.
      Resource resource = new Resource();
      resource.setName(name);
      resource.setType(ResourceType.OPEN_API_SPEC);
      results.add(resource);
      // Set the content of main OpenAPI that may have been updated with normalized dependencies with initializeReferencedResources().
      resource.setContent(rootSpecificationContent);

      // Add the external resources that were imported during service discovery.
      results.addAll(externalResources);

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      Map<Request, Response> result = new HashMap<>();
      // Callbacks are optional - so don't eagerly initialize.
      List<RequestResponsePair> callbacks = null;

      // Iterate on specification "paths" nodes.
      Set<Entry<String, JsonNode>> paths = rootSpecification.path("paths").properties();
      for (Entry<String, JsonNode> path : paths) {
         String pathName = path.getKey();
         JsonNode pathValue = followRefIfAny(path.getValue());

         // Iterate on specification path, "verbs" nodes.
         Set<Entry<String, JsonNode>> verbs = pathValue.properties();
         for (Entry<String, JsonNode> verb : verbs) {
            String verbName = verb.getKey();

            // Find the correct operation.
            if (operation.getName().equals(verbName.toUpperCase() + " " + pathName.trim())) {
               List<EffectiveParameter> parameters = getEffectiveParameters(pathValue, verb.getValue());
               Map<String, Multimap<String, String>> pathParametersByExample = extractParametersByExample(parameters,
                     "path");
               Map<String, Multimap<String, String>> queryParametersByExample = extractParametersByExample(parameters,
                     PARAMETERS_QUERY_VALUE);
               Map<String, Multimap<String, String>> headerParametersByExample = extractParametersByExample(parameters,
                     "header");
               Map<String, Request> requestBodiesByExample = extractRequestBodies(verb.getValue());

               // No need to go further if no examples.
               if (verb.getValue().has(RESPONSES_NODE)) {

                  // If we previously override the dispatcher with a Fallback, we must be sure to get wrapped elements.
                  DispatchCriteriaHelper.DispatcherDetails details = DispatchCriteriaHelper
                        .extractDispatcherWithRules(operation);
                  String rootDispatcher = details.rootDispatcher();
                  String rootDispatcherRules = details.rootDispatcherRules();

                  Set<Entry<String, JsonNode>> responseCodes = verb.getValue().path(RESPONSES_NODE).properties();
                  for (Entry<String, JsonNode> responseCode : responseCodes) {
                     Set<Entry<String, JsonNode>> contents = getResponseContent(responseCode.getValue()).properties();
                     JsonNode resolvedResponseCode = followRefIfAny(responseCode.getValue());

                     if (contents.isEmpty() && resolvedResponseCode.has(X_MICROCKS_REFS)) {
                        result.putAll(getNoContentRequestResponsePair(operation, rootDispatcher, rootDispatcherRules,
                              requestBodiesByExample, pathParametersByExample, queryParametersByExample,
                              headerParametersByExample, responseCode));
                     }
                     for (Entry<String, JsonNode> content : contents) {
                        result.putAll(getContentRequestResponsePairs(operation, rootDispatcher, rootDispatcherRules,
                              requestBodiesByExample, pathParametersByExample, queryParametersByExample,
                              headerParametersByExample, responseCode, content));
                     }
                  }
               }

               if (verb.getValue().has("callbacks")) {
                  callbacks = extractCallbackExchanges(verb.getValue().get("callbacks"), operation);
               }
            }
         }
      }

      // Iterate on specification "webhooks" nodes.
      Set<Entry<String, JsonNode>> webhooks = rootSpecification.path("webhooks").properties();
      for (Entry<String, JsonNode> webhook : webhooks) {
         result.putAll(extractWebhookRequestResponses(webhook.getKey(), followRefIfAny(webhook.getValue()), operation));
      }

      // If we have callbacks, use a RequestResponsePair with callbacks.
      if (callbacks != null && !callbacks.isEmpty()) {
         final List<RequestResponsePair> finalCallbacks = callbacks;
         return result.entrySet().stream()
               .map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue(),
                     finalCallbacks.stream()
                           .filter(pair -> pair.getRequest().getName().equals(entry.getKey().getName())).toList()))
               .collect(Collectors.toList());
      }

      // Adapt map to list of simple RequestResponsePair Exchanges.
      return result.entrySet().stream().map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
   }

   /**
    * Extract the list of operations from Specification.
    */
   private List<Operation> extractOperations() {
      List<Operation> results = new ArrayList<>();

      // Iterate on specification "paths" nodes.
      Set<Entry<String, JsonNode>> paths = rootSpecification.path("paths").properties();
      for (Entry<String, JsonNode> path : paths) {
         String pathName = path.getKey();
         JsonNode pathValue = followRefIfAny(path.getValue());

         // Iterate on specification path, "verbs" nodes.
         Set<Entry<String, JsonNode>> verbs = pathValue.properties();
         for (Entry<String, JsonNode> verb : verbs) {
            String verbName = verb.getKey();

            // Only deal with real verbs for now.
            if (VALID_VERBS.contains(verbName)) {
               String operationName = verbName.toUpperCase() + " " + pathName.trim();

               Operation operation = new Operation();
               operation.setName(operationName);
               operation.setMethod(verbName.toUpperCase());

               // Complete operation properties if any.
               if (verb.getValue().has(MetadataExtensions.MICROCKS_OPERATION_EXTENSION)) {
                  MetadataExtractor.completeOperationProperties(operation,
                        verb.getValue().path(MetadataExtensions.MICROCKS_OPERATION_EXTENSION));
               }

               List<EffectiveParameter> parameters = getEffectiveParameters(pathValue, verb.getValue());
               completeOperationWithDispatcher(operation, parameters, verb.getValue(), pathName);
               completeOperationWithParameterConstraints(operation, parameters);

               results.add(operation);
            }
         }
      }

      Set<Entry<String, JsonNode>> webhooks = rootSpecification.path("webhooks").properties();
      for (Entry<String, JsonNode> webhook : webhooks) {
         String webhookName = webhook.getKey();
         JsonNode webhookValue = followRefIfAny(webhook.getValue());

         // Iterate on specification webhook, "verbs" nodes.
         Set<Entry<String, JsonNode>> verbs = webhookValue.properties();
         for (Entry<String, JsonNode> verb : verbs) {
            String verbName = verb.getKey();

            // Only deal with real verbs for now.
            if (VALID_VERBS.contains(verbName)) {
               String operationName = webhookName.trim() + " WEBHOOK";

               Operation operation = new Operation();
               operation.setName(operationName);
               operation.setMethod(verbName.toUpperCase());
               operation.setAction("webhook");

               // Complete operation properties if any.
               if (verb.getValue().has(MetadataExtensions.MICROCKS_OPERATION_EXTENSION)) {
                  MetadataExtractor.completeOperationProperties(operation,
                        verb.getValue().path(MetadataExtensions.MICROCKS_OPERATION_EXTENSION));
               }

               results.add(operation);
            }
         }
      }
      return results;
   }

   /** An operation parameter overrides the path item parameter with the same name and location. */
   private List<EffectiveParameter> getEffectiveParameters(JsonNode pathItem, JsonNode operation) {
      Map<ParameterIdentity, EffectiveParameter> parameters = new LinkedHashMap<>();
      for (JsonNode node : List.of(operation, pathItem)) {
         for (JsonNode parameterNode : node.path(PARAMETERS_NODE)) {
            JsonNode parameter = followRefIfAny(parameterNode);
            if (parameter != null) {
               ParameterIdentity identity = new ParameterIdentity(parameter.path("name").asText(),
                     parameter.path("in").asText());
               parameters.putIfAbsent(identity, new EffectiveParameter(parameter, node == pathItem));
            }
         }
      }
      return List.copyOf(parameters.values());
   }

   private record ParameterIdentity(String name, String location) {
   }

   private record EffectiveParameter(JsonNode definition, boolean inherited) {
      boolean isInheritedQueryWithExamples() {
         JsonNode examples = definition.path(EXAMPLES_NODE);
         return inherited && PARAMETERS_QUERY_VALUE.equals(definition.path("in").asText()) && examples.isObject()
               && !examples.isEmpty();
      }
   }

   private List<JsonNode> getDispatchParameters(List<EffectiveParameter> parameters, JsonNode operation,
         String pathName) {
      Set<String> responseExamples = new HashSet<>();
      if (parameters.stream().anyMatch(EffectiveParameter::isInheritedQueryWithExamples)) {
         responseExamples.addAll(getResponseExampleNames(operation));
         if (urlHasParts(pathName)) {
            responseExamples.retainAll(extractParametersByExample(parameters, "path").keySet());
         }
      }

      // Keep existing operation-level rules. Inherited parameters need a usable sample for this operation.
      return parameters.stream().filter(parameter -> !parameter.inherited() || (parameter.isInheritedQueryWithExamples()
            && extractParametersByExample(List.of(parameter), PARAMETERS_QUERY_VALUE).entrySet().stream()
                  .anyMatch(example -> responseExamples.contains(example.getKey()) && !example.getValue().isEmpty())))
            .map(EffectiveParameter::definition).toList();
   }

   private Set<String> getResponseExampleNames(JsonNode operation) {
      Set<String> exampleNames = new HashSet<>();
      for (JsonNode response : operation.path(RESPONSES_NODE)) {
         JsonNode content = getResponseContent(response);
         if (content.properties().isEmpty()) {
            JsonNode requestRefs = followRefIfAny(response).path(X_MICROCKS_REFS);
            if (requestRefs.isArray()) {
               for (JsonNode requestRef : requestRefs) {
                  if (requestRef.isTextual()) {
                     exampleNames.add(requestRef.textValue());
                  }
               }
            }
         }
         for (JsonNode mediaType : content) {
            followRefIfAny(mediaType.path(EXAMPLES_NODE)).fieldNames().forEachRemaining(exampleNames::add);
         }
      }
      return exampleNames;
   }

   /** Complete operation with dispatcher rules if not already set. */
   private void completeOperationWithDispatcher(Operation operation, List<EffectiveParameter> effectiveParameters,
         JsonNode operationNode, String pathName) {
      if (operation.getDispatcher() == null) {
         List<JsonNode> parameters = getDispatchParameters(effectiveParameters, operationNode, pathName);
         boolean hasQueryParameters = parameters.stream()
               .anyMatch(parameter -> PARAMETERS_QUERY_VALUE.equals(parameter.path("in").asText()));
         if (hasQueryParameters && urlHasParts(pathName)) {
            operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(pathName) + " ?? "
                  + extractOperationParams(parameters));
            operation.setDispatcher(DispatchStyles.URI_ELEMENTS);
         } else if (hasQueryParameters) {
            operation.setDispatcherRules(extractOperationParams(parameters));
            operation.setDispatcher(DispatchStyles.URI_PARAMS);
         } else if (urlHasParts(pathName)) {
            operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(pathName));
            operation.setDispatcher(DispatchStyles.URI_PARTS);
         } else {
            operation.addResourcePath(pathName);
         }
      } else {
         // If dispatcher has been forced via Metadata, we should still put a generic resourcePath
         // (maybe containing {} parts) to later force operation matching at the mock controller level.
         operation.addResourcePath(pathName);
      }
   }

   /** Add ParameterConstraints to operation if required parameters are found. */
   private void completeOperationWithParameterConstraints(Operation operation, List<EffectiveParameter> parameters) {
      for (EffectiveParameter effective : parameters) {
         JsonNode parameter = effective.definition();
         boolean required = parameter.path("required").asBoolean(false);
         String location = parameter.path("in").asText();

         if (required && !"path".equals(location)) {
            ParameterLocation parameterLocation = ParameterLocation.valueOf(location);
            if (parameterLocation == ParameterLocation.query && effective.inherited()
                  && "deepObject".equals(parameter.path("style").asText())) {
               // Deep objects use bracketed property names, so a bare-name constraint would reject valid requests.
               continue;
            }
            JsonNode schema = parameterLocation == ParameterLocation.query ? followRefIfAny(parameter.path("schema"))
                  : null;
            if (isExplodedFormObject(parameter, schema)) {
               // An exploded form object sends its properties as individual query keys.
               for (JsonNode property : schema.path("required")) {
                  addRequiredParameterConstraint(operation, parameterLocation, property.asText());
               }
            } else {
               addRequiredParameterConstraint(operation, parameterLocation, parameter.path("name").asText());
            }
         }
      }
   }

   private boolean isExplodedFormObject(JsonNode parameter, JsonNode schema) {
      return schema != null && "object".equals(schema.path("type").asText())
            && "form".equals(parameter.path("style").asText("form")) && parameter.path("explode").asBoolean(true);
   }

   private void addRequiredParameterConstraint(Operation operation, ParameterLocation location, String name) {
      ParameterConstraint constraint = new ParameterConstraint();
      constraint.setRequired(true);
      constraint.setName(name);
      constraint.setIn(location);
      operation.addParameterConstraint(constraint);
   }

   /**
    * Extract examples for parameters in the given location. The outer map is keyed by example name and the inner
    * multimap is keyed by parameter name.
    */
   private Map<String, Multimap<String, String>> extractParametersByExample(List<EffectiveParameter> parameters,
         String parameterType) {
      Map<String, Multimap<String, String>> results = new HashMap<>();

      for (EffectiveParameter effective : parameters) {
         JsonNode parameter = effective.definition();
         String parameterName = parameter.path("name").asText();

         if (parameter.has("in") && parameter.path("in").asText().equals(parameterType)
               && parameter.has(EXAMPLES_NODE)) {
            Iterator<String> exampleNames = parameter.path(EXAMPLES_NODE).fieldNames();
            while (exampleNames.hasNext()) {
               String exampleName = exampleNames.next();
               JsonNode example = parameter.path(EXAMPLES_NODE).path(exampleName);
               JsonNode exampleValue = getExampleValue(example);

               if (exampleValue == null) {
                  log.warn("Couldn't find example value for example node: name: {}, data: {}", exampleName, example);
                  continue;
               }

               if (effective.inherited() && PARAMETERS_QUERY_VALUE.equals(parameterType)
                     && !supportsInheritedQueryExample(parameter, exampleValue)) {
                  continue;
               }

               Multimap<String, String> exampleParams = results.computeIfAbsent(exampleName,
                     k -> ArrayListMultimap.create());

               if (PARAMETERS_QUERY_VALUE.equals(parameterType) && exampleValue.isArray()) {
                  // Array of query params.
                  for (JsonNode current : (ArrayNode) exampleValue) {
                     exampleParams.put(parameterName, getValueString(current));
                  }
               } else if (PARAMETERS_QUERY_VALUE.equals(parameterType) && exampleValue.isObject()) {
                  final var fields = ((ObjectNode) exampleValue).properties();
                  for (Entry<String, JsonNode> current : fields) {
                     exampleParams.put(current.getKey(), getValueString(current.getValue()));
                  }

               } else {
                  exampleParams.put(parameterName, getValueString(exampleValue));
               }
            }
         }
      }
      return results;
   }

   private boolean supportsInheritedQueryExample(JsonNode parameter, JsonNode example) {
      if (!"form".equals(parameter.path("style").asText("form"))) {
         return false;
      }
      if (example.isValueNode()) {
         return isNonemptyQueryValue(example);
      }
      if (!parameter.path("explode").asBoolean(true)) {
         return false;
      }
      if (example.isObject()) {
         JsonNode schema = followRefIfAny(parameter.path("schema"));
         if (!isExplodedFormObject(parameter, schema)) {
            return false;
         }

         // Automatic dispatch only includes keys declared in the schema's properties.
         JsonNode properties = schema.path("properties");
         Iterator<String> exampleKeys = example.fieldNames();
         while (exampleKeys.hasNext()) {
            if (!properties.has(exampleKeys.next())) {
               return false;
            }
         }
      }

      // Exploded form can reuse the existing repeated-key array and flat object handling.
      // Nested arrays and objects have no representation in these query examples.
      for (JsonNode value : example) {
         if (!isNonemptyQueryValue(value)) {
            return false;
         }
      }
      return example.isContainerNode();
   }

   private boolean isNonemptyQueryValue(JsonNode value) {
      // URI dispatch treats empty query values as absent.
      return value.isValueNode() && !value.isNull() && !value.asText().isEmpty();
   }

   /**
    * Extract request bodies within verb specification and organize them by example. Key of returned map is example
    * name. Value is basic Microcks Request object (no query params, no headers)
    */
   private Map<String, Request> extractRequestBodies(JsonNode verbNode) {
      Map<String, Request> results = new HashMap<>();

      JsonNode requestBody = verbNode.path("requestBody");
      Iterator<String> contentTypeNames = requestBody.path(CONTENT_NODE).fieldNames();
      while (contentTypeNames.hasNext()) {
         String contentTypeName = contentTypeNames.next();
         JsonNode contentType = requestBody.path(CONTENT_NODE).path(contentTypeName);

         if (contentType.has(EXAMPLES_NODE)) {
            Iterator<String> exampleNames = contentType.path(EXAMPLES_NODE).fieldNames();
            while (exampleNames.hasNext()) {
               String exampleName = exampleNames.next();
               JsonNode example = contentType.path(EXAMPLES_NODE).path(exampleName);
               String exampleValue = getSerializedExampleValue(example);

               // Build and store a request object.
               Request request = new Request();
               request.setName(exampleName);
               request.setContent(exampleValue);

               // We should add a Content-type header here for request body.
               Header header = new Header();
               header.setName("Content-Type");
               HashSet<String> values = new HashSet<>();
               values.add(contentTypeName);
               header.setValues(values);
               request.addHeader(header);

               results.put(exampleName, request);
            }
         }
      }
      return results;
   }

   /**
    * Extract callback exchanges within callbacks specification node.
    */
   private List<RequestResponsePair> extractCallbackExchanges(JsonNode callbacksNode, Operation operation) {
      List<RequestResponsePair> results = new ArrayList<>();

      for (Entry<String, JsonNode> callbackNode : callbacksNode.properties()) {
         String callbackName = callbackNode.getKey();

         for (Entry<String, JsonNode> callbackPath : callbackNode.getValue().properties()) {
            String callbackPathExpression = callbackPath.getKey();

            for (Entry<String, JsonNode> callbackVerb : callbackPath.getValue().properties()) {
               // Complete the operation with the callback information.
               CallbackInfo callbackInfo = new CallbackInfo(callbackPathExpression, callbackVerb.getKey());
               if (callbackVerb.getValue().has("x-microcks-callback")) {
                  JsonNode microcksCBExt = callbackVerb.getValue().get("x-microcks-callback");
                  if (microcksCBExt.has("order")) {
                     callbackInfo.setOrder(microcksCBExt.get("order").asInt());
                  }
               }

               operation.addCallbackInfo(callbackName, callbackInfo);

               // Initialize new structure for storing results of this callback verb.
               Map<Request, Response> requestResponseMap = new HashMap<>();
               Map<String, Request> requestBodiesByExample = extractRequestBodies(callbackVerb.getValue());

               // Update the request with the name of the callback as the exchange reference.
               for (Request request : requestBodiesByExample.values()) {
                  request.setCallbackName(callbackName);
               }

               if (callbackVerb.getValue().has(RESPONSES_NODE)) {
                  for (Entry<String, JsonNode> responseCode : callbackVerb.getValue().path(RESPONSES_NODE)
                        .properties()) {

                     Set<Entry<String, JsonNode>> contentNodes = getResponseContent(responseCode.getValue())
                           .properties();
                     JsonNode resolvedResponseCode = followRefIfAny(responseCode.getValue());
                     if (contentNodes.isEmpty() && resolvedResponseCode.has(X_MICROCKS_REFS)) {
                        requestResponseMap.putAll(getNoContentCallbackRequestResponsePair(callbackName,
                              requestBodiesByExample, responseCode));
                     }
                     for (Entry<String, JsonNode> contentNode : contentNodes) {
                        requestResponseMap.putAll(getContentCallbackRequestResponsePairs(callbackName,
                              requestBodiesByExample, responseCode, contentNode));
                     }
                  }
               }

               if (!requestResponseMap.isEmpty()) {
                  results.addAll(requestResponseMap.entrySet().stream()
                        .map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue())).toList());
               }
            }
         }
      }
      return results;
   }

   /**
    * Extract webhook request/response pairs with a webhook specification node.
    */
   private Map<Request, Response> extractWebhookRequestResponses(String webhookName, JsonNode webhookNode,
         Operation operation) {
      Map<Request, Response> results = new HashMap<>();

      // Iterate on specification path, "verbs" nodes.
      Set<Entry<String, JsonNode>> verbs = webhookNode.properties();
      for (Entry<String, JsonNode> verb : verbs) {

         // Find the correct operation.
         if (operation.getName().equals(webhookName.trim() + " WEBHOOK")) {
            Map<String, Request> requestBodiesByExample = extractRequestBodies(verb.getValue());

            // No need to go further if no examples.
            if (verb.getValue().has(RESPONSES_NODE)) {
               Set<Entry<String, JsonNode>> responseCodes = verb.getValue().path(RESPONSES_NODE).properties();
               for (Entry<String, JsonNode> responseCode : responseCodes) {
                  Set<Entry<String, JsonNode>> contents = getResponseContent(responseCode.getValue()).properties();
                  JsonNode resolvedResponseCode = followRefIfAny(responseCode.getValue());

                  if (contents.isEmpty() && resolvedResponseCode.has(X_MICROCKS_REFS)) {
                     results.putAll(getNoContentRequestResponsePair(operation, null, null, requestBodiesByExample,
                           Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), responseCode));
                  }
                  for (Entry<String, JsonNode> content : contents) {
                     results.putAll(getContentRequestResponsePairs(operation, null, null, requestBodiesByExample,
                           Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), responseCode,
                           content));
                  }
               }
            }
         }
      }

      return results;
   }

   /**
    * Extract headers within a header specification node and organize them by example. Key of returned map is example
    * name. Value is a list of Microcks Header objects.
    */
   private Map<String, List<Header>> extractHeadersByExample(JsonNode responseNode) {
      Map<String, List<Header>> results = new HashMap<>();

      responseNode = followRefIfAny(responseNode);
      if (responseNode.has(HEADERS_NODE)) {
         JsonNode headersNode = responseNode.path(HEADERS_NODE);
         Iterator<String> headerNames = headersNode.fieldNames();

         while (headerNames.hasNext()) {
            String headerName = headerNames.next();
            JsonNode headerNode = headersNode.path(headerName);

            if (headerNode.has(EXAMPLES_NODE)) {
               Iterator<String> exampleNames = headerNode.path(EXAMPLES_NODE).fieldNames();
               while (exampleNames.hasNext()) {
                  String exampleName = exampleNames.next();
                  JsonNode example = headerNode.path(EXAMPLES_NODE).path(exampleName);
                  String exampleValue = getSerializedExampleValue(example);

                  List<Header> headersForExample = results.computeIfAbsent(exampleName, k -> new ArrayList<>());

                  // Example may be multiple CSV.
                  Set<String> values = Arrays.stream(exampleValue.split(",")).map(String::trim)
                        .collect(Collectors.toSet());

                  Header header = new Header();
                  header.setName(headerName);
                  header.setValues(values);

                  headersForExample.add(header);
                  results.put(exampleName, headersForExample);
               }
            }
         }
      }
      return results;
   }

   /**
    * Get the request/response pairs for a response content.
    */
   private Map<Request, Response> getContentRequestResponsePairs(Operation operation, String rootDispatcher,
         String rootDispatcherRules, Map<String, Request> requestBodiesByExample,
         Map<String, Multimap<String, String>> pathParametersByExample,
         Map<String, Multimap<String, String>> queryParametersByExample,
         Map<String, Multimap<String, String>> headerParametersByExample, Entry<String, JsonNode> responseCode,
         Entry<String, JsonNode> content) {

      Map<Request, Response> results = new HashMap<>();

      String contentValue = content.getKey();
      // Find here potential headers for output of this operation examples.
      Map<String, List<Header>> headersByExample = extractHeadersByExample(responseCode.getValue());

      JsonNode examplesNode = followRefIfAny(content.getValue().path(EXAMPLES_NODE));

      Iterator<String> exampleNames = examplesNode.fieldNames();
      while (exampleNames.hasNext()) {
         String exampleName = exampleNames.next();
         JsonNode example = examplesNode.path(exampleName);

         // We should have everything at hand to build response here.
         Response response = new Response();
         response.setName(exampleName);
         response.setMediaType(contentValue);
         response.setStatus(responseCode.getKey());
         response.setContent(getSerializedExampleValue(example));
         if (!responseCode.getKey().startsWith("2")) {
            response.setFault(true);
         }
         List<Header> responseHeaders = headersByExample.get(exampleName);
         if (responseHeaders != null) {
            responseHeaders.stream().forEach(response::addHeader);
         }

         // Do we have a request for this example?
         Request request = requestBodiesByExample.get(exampleName);
         if (request == null) {
            request = new Request();
            request.setName(exampleName);
         }

         // Complete request accept-type with response content-type.
         Header header = new Header();
         header.setName("Accept");
         HashSet<String> values = new HashSet<>();
         values.add(contentValue);
         header.setValues(values);
         request.addHeader(header);

         // Do we have to complete request with path parameters?
         Multimap<String, String> pathParameters = pathParametersByExample.get(exampleName);
         if (pathParameters != null) {
            completeRequestWithPathParameters(request, pathParameters);
         } else if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())
               || DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
            // We must have at least one path parameters but none!
            // Do not register this request / response pair.
            continue;
         }

         // Complete request with query parameters if any.
         completeRequestWithQueryParameters(request, queryParametersByExample.get(exampleName));
         // Complete request with header parameters if any.
         completeRequestWithHeaderParameters(request, headerParametersByExample.get(exampleName));

         // Finally, take care about dispatchCriteria and complete operation resourcePaths.
         completeDispatchCriteriaAndResourcePaths(operation, rootDispatcher, rootDispatcherRules,
               pathParametersByExample, queryParametersByExample, headerParametersByExample, exampleName, response);

         results.put(request, response);
      }

      return results;
   }

   /**
    * Get the request/response pairs for a response content with a callback..
    */
   private Map<Request, Response> getContentCallbackRequestResponsePairs(String callbackName,
         Map<String, Request> requestBodiesByExample, Entry<String, JsonNode> responseCode,
         Entry<String, JsonNode> content) {

      Map<Request, Response> results = new HashMap<>();

      JsonNode examplesNode = followRefIfAny(content.getValue().path(EXAMPLES_NODE));

      Iterator<String> exampleNames = examplesNode.fieldNames();
      while (exampleNames.hasNext()) {
         String exampleName = exampleNames.next();

         // Do we have a request or path or query or header parameters?
         Request request = requestBodiesByExample.get(exampleName);
         if (request != null) {
            JsonNode example = examplesNode.path(exampleName);

            // We should have everything at hand to build response here.
            Response response = new Response();
            response.setName(exampleName);
            response.setMediaType(content.getKey());
            response.setStatus(responseCode.getKey());
            response.setCallbackName(callbackName);
            response.setContent(getSerializedExampleValue(example));
            if (!responseCode.getKey().startsWith("2")) {
               response.setFault(true);
            }
            results.put(request, response);
         }
      }
      return results;
   }

   /**
    * Get the request/response pairs for a response without content. A response without content has a x-microcks-refs
    * property to get bounds to requests.
    */
   private Map<Request, Response> getNoContentRequestResponsePair(Operation operation, String rootDispatcher,
         String rootDispatcherRules, Map<String, Request> requestBodiesByExample,
         Map<String, Multimap<String, String>> pathParametersByExample,
         Map<String, Multimap<String, String>> queryParametersByExample,
         Map<String, Multimap<String, String>> headerParametersByExample, Entry<String, JsonNode> responseCode) {

      Map<Request, Response> results = new HashMap<>();
      JsonNode resolvedResponse = followRefIfAny(responseCode.getValue());
      JsonNode requestRefs = resolvedResponse.path(X_MICROCKS_REFS);

      if (requestRefs.isArray()) {
         // Find here potential headers for output of this operation examples.
         Map<String, List<Header>> headersByExample = extractHeadersByExample(responseCode.getValue());

         Iterator<JsonNode> requestRefsIterator = requestRefs.elements();
         while (requestRefsIterator.hasNext()) {
            String exampleName = requestRefsIterator.next().textValue();

            // Do we have a request or path or query or header parameters?
            Request request = requestBodiesByExample.get(exampleName);
            Multimap<String, String> pathParameters = pathParametersByExample.get(exampleName);
            Multimap<String, String> queryParameters = queryParametersByExample.get(exampleName);
            Multimap<String, String> headerParameters = headerParametersByExample.get(exampleName);

            if (request != null || pathParameters != null || queryParameters != null || headerParameters != null) {
               if (request == null) {
                  request = new Request();
                  request.setName(exampleName);
               }

               if (pathParameters != null) {
                  completeRequestWithPathParameters(request, pathParameters);
               } else if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())
                     || DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
                  // We must have at least one path parameters but none!
                  // Do not register this request / response pair.
                  continue;
               }

               // We should have everything at hand to build response here.
               Response response = new Response();
               response.setName(exampleName);
               response.setStatus(responseCode.getKey());
               if (!responseCode.getKey().startsWith("2")) {
                  response.setFault(true);
               }
               List<Header> responseHeaders = headersByExample.get(exampleName);
               if (responseHeaders != null) {
                  responseHeaders.stream().forEach(response::addHeader);
               }

               // Complete request with query parameters if any.
               completeRequestWithQueryParameters(request, queryParametersByExample.get(exampleName));
               // Complete request with header parameters if any.
               completeRequestWithHeaderParameters(request, headerParametersByExample.get(exampleName));

               // Finally, take care about dispatchCriteria and complete operation resourcePaths.
               completeDispatchCriteriaAndResourcePaths(operation, rootDispatcher, rootDispatcherRules,
                     pathParametersByExample, queryParametersByExample, headerParametersByExample, exampleName,
                     response);

               results.put(request, response);
            }
         }
      }
      return results;
   }

   private void completeRequestWithPathParameters(Request request, Multimap<String, String> pathParameters) {
      for (Entry<String, String> paramEntry : pathParameters.entries()) {
         Parameter param = new Parameter();
         param.setName(paramEntry.getKey());
         param.setValue(paramEntry.getValue());
         request.addQueryParameter(param);
      }
   }

   private void completeRequestWithQueryParameters(Request request, Multimap<String, String> queryParameters) {
      if (queryParameters != null) {
         for (Entry<String, String> paramEntry : queryParameters.entries()) {
            Parameter param = new Parameter();
            param.setName(paramEntry.getKey());
            param.setValue(paramEntry.getValue());
            request.addQueryParameter(param);
         }
      }
   }

   private void completeRequestWithHeaderParameters(Request request, Multimap<String, String> headerParameters) {
      if (headerParameters != null) {
         for (Entry<String, String> headerEntry : headerParameters.entries()) {
            Header header = new Header();
            header.setName(headerEntry.getKey());
            // Values may be multiple and CSV.
            Set<String> headerValues = Arrays.stream(headerEntry.getValue().split(",")).map(String::trim)
                  .collect(Collectors.toSet());
            header.setValues(headerValues);
            request.addHeader(header);
         }
      }
   }

   private void completeDispatchCriteriaAndResourcePaths(Operation operation, String rootDispatcher,
         String rootDispatcherRules, Map<String, Multimap<String, String>> pathParametersByExample,
         Map<String, Multimap<String, String>> queryParametersByExample,
         Map<String, Multimap<String, String>> headerParametersByExample, String exampleName, Response response) {
      String dispatchCriteria = null;
      String resourcePathPattern = operation.getName().split(" ")[1];

      if (DispatchStyles.URI_PARAMS.equals(rootDispatcher)) {
         Multimap<String, String> queryParams = queryParametersByExample.get(exampleName);
         dispatchCriteria = DispatchCriteriaHelper.buildFromParamsMap(rootDispatcherRules, queryParams);
         // We only need the pattern here.
         operation.addResourcePath(resourcePathPattern);
      } else if (DispatchStyles.URI_PARTS.equals(rootDispatcher)) {
         Multimap<String, String> parts = pathParametersByExample.get(exampleName);
         dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parts);
         // We should complete resourcePath here.
         String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, parts);
         operation.addResourcePath(resourcePath);
      } else if (DispatchStyles.URI_ELEMENTS.equals(rootDispatcher)) {
         Multimap<String, String> parts = pathParametersByExample.get(exampleName);
         Multimap<String, String> queryParams = queryParametersByExample.get(exampleName);
         dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parts);
         dispatchCriteria += DispatchCriteriaHelper.buildFromParamsMap(rootDispatcherRules, queryParams);
         // We should complete resourcePath here.
         String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, parts);
         operation.addResourcePath(resourcePath);
      } else if (DispatchStyles.QUERY_HEADER.equals(rootDispatcher)) {
         Multimap<String, String> headerParams = headerParametersByExample.get(exampleName);
         dispatchCriteria = DispatchCriteriaHelper.buildFromParamsMap(rootDispatcherRules, headerParams);
         // We only need the pattern here.
         operation.addResourcePath(resourcePathPattern);
      }
      response.setDispatchCriteria(dispatchCriteria);
   }

   /**
    * Get the request/response pairs for a callback response without content. A response without content has a
    * x-microcks-refs property to get bounds to requests.
    */
   private Map<Request, Response> getNoContentCallbackRequestResponsePair(String callbackName,
         Map<String, Request> requestBodiesByExample, Entry<String, JsonNode> responseCode) {

      Map<Request, Response> results = new HashMap<>();
      JsonNode resolvedResponse = followRefIfAny(responseCode.getValue());
      JsonNode requestRefs = resolvedResponse.path(X_MICROCKS_REFS);

      if (requestRefs.isArray()) {
         Iterator<JsonNode> requestRefsIterator = requestRefs.elements();
         while (requestRefsIterator.hasNext()) {
            String exampleName = requestRefsIterator.next().textValue();

            // Do we have a request or path or query or header parameters?
            Request request = requestBodiesByExample.get(exampleName);
            if (request != null) {
               // We should have everything at hand to build response here.
               Response response = new Response();
               response.setName(exampleName);
               response.setStatus(responseCode.getKey());
               response.setCallbackName(callbackName);
               if (!responseCode.getKey().startsWith("2")) {
                  response.setFault(true);
               }

               results.put(request, response);
            }
         }
      }
      return results;
   }

   /** Get the value of an example. This can be direct value field or those of followed $ref */
   private JsonNode getExampleValue(JsonNode example) {
      if (example.has(EXAMPLE_VALUE_NODE)) {
         return followRefIfAny(example.path(EXAMPLE_VALUE_NODE));
      }
      if (example.has(EXAMPLE_EXTERNAL_VALUE_NODE)) {
         // if the externalValue node does not start with "data:" it is a remote or relative URL so we must getNodeForRef for it else just return the node
         String externalValue = example.path(EXAMPLE_EXTERNAL_VALUE_NODE).asText();
         if (externalValue.startsWith("data:")) {
            return example.path(EXAMPLE_EXTERNAL_VALUE_NODE);
         } else {
            return getNodeForRef(externalValue);
         }
      }
      if (example.has("$ref")) {
         JsonNode component = followRefIfAny(example);
         return getExampleValue(component);
      }
      return null;
   }

   /** Get the serialized value of an example. This can be direct value field or those of followed $ref */
   private String getSerializedExampleValue(JsonNode example) {
      JsonNode exampleValue = getExampleValue(example);
      return exampleValue != null ? getValueString(exampleValue) : null;
   }

   /** Get the content of a response. This can be direct content field or those of followed $ref */
   private JsonNode getResponseContent(JsonNode response) {
      if (response.has("$ref")) {
         JsonNode component = followRefIfAny(response);
         return getResponseContent(component);
      }
      return response.path(CONTENT_NODE);
   }

   /** Build a string representing operation parameters as used in dispatcher rules (param1 && param2) */
   private String extractOperationParams(List<JsonNode> parameters) {
      StringBuilder params = new StringBuilder();
      for (JsonNode parameter : parameters) {
         String parameterIn = parameter.path("in").asText();
         String parameterType = followRefIfAny(parameter.path("schema")).path("type").asText();
         if (!"path".equals(parameterIn)) {
            if (!params.isEmpty()) {
               params.append(" && ");
            }
            if (parameterType.equals("object")) {
               params.append(StreamSupport.stream(Spliterators.spliteratorUnknownSize(
                     followRefIfAny(parameter.path("schema")).path("properties").fieldNames(), Spliterator.ORDERED),
                     false).collect(Collectors.joining(" && ")));
            } else {
               params.append(parameter.path("name").asText());
            }
         }
      }
      return params.toString();
   }

   /** Check variables parts presence into given url. */
   private static boolean urlHasParts(String url) {
      return (url.indexOf("/:") != -1 || url.indexOf("/{") != -1);
   }
}
