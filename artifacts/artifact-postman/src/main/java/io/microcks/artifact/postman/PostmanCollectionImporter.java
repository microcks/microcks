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

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.URIBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Implement of MockRepositoryImporter that uses a Postman collection for building domain objects. Only v2 collection
 * format is supported.
 * @author laurent
 */
public class PostmanCollectionImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(PostmanCollectionImporter.class);

   /** Postman collection property that references service version property. */
   public static final String SERVICE_VERSION_PROPERTY = "version";
   /** We only support Postman Collection v2. Here's the error message otherwise. */
   public static final String COLLECTION_VERSION_ERROR_MESSAGE = "Only Postman v2 Collection are supported.";

   private static final String INFO_NODE = "info";
   private static final String REQUEST_NODE = "request";
   private static final String RESPONSE_NODE = "response";
   private static final String QUERY_NODE = "query";
   private static final String VARIABLE_NODE = "variable";
   private static final String GRAPHQL_NODE = "graphql";
   private static final String VARIABLES_NODE = "variables";
   private static final String VALUE_NODE = "value";

   private ObjectMapper mapper;
   private JsonNode collection;
   private String collectionContent;
   // Flag telling if V2 format is used.
   private boolean isV2Collection = false;

   /** Default constructor for package protected extensions. */
   protected PostmanCollectionImporter() {
   }

   /**
    * Build a new importer.
    * @param collectionFilePath The path to local Postman collection file
    * @throws IOException if project file cannot be found or read.
    */
   public PostmanCollectionImporter(String collectionFilePath) throws IOException {
      try {
         // Read Json bytes.
         byte[] jsonBytes = Files.readAllBytes(Paths.get(collectionFilePath));
         collectionContent = new String(jsonBytes, StandardCharsets.UTF_8);
         // Convert them to Node using Jackson object mapper.
         mapper = new ObjectMapper();
         collection = mapper.readTree(jsonBytes);
      } catch (Exception e) {
         log.error("Exception while parsing Postman collection file " + collectionFilePath, e);
         throw new IOException("Postman collection file parsing error");
      }
   }

   protected void setCollection(JsonNode collection) {
      this.collection = collection;
   }

   protected void setCollectionContent(String collectionContent) {
      this.collectionContent = collectionContent;
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();

      // Collection V2 as an info node.
      if (collection.has(INFO_NODE)) {
         isV2Collection = true;
         fillServiceDefinition(service);
      } else {
         throw new MockRepositoryImportException(COLLECTION_VERSION_ERROR_MESSAGE);
      }

      // Then build its operations.
      try {
         service.setOperations(extractOperations());
      } catch (Throwable t) {
         log.error("Runtime exception while extracting Operations for {}", service.getName());
         throw new MockRepositoryImportException("Runtime exception for " + service.getName() + ": " + t.getMessage());
      }

      result.add(service);
      return result;
   }

   private void fillServiceDefinition(Service service) throws MockRepositoryImportException {
      JsonNode infoNode = collection.path(INFO_NODE);
      service.setName(infoNode.path("name").asText());
      service.setType(ServiceType.REST);

      String version = null;

      // On v2.1 collection format, we may have a version attribute under info.
      // See https://schema.getpostman.com/json/collection/v2.1.0/docs/index.html
      if (infoNode.has(SERVICE_VERSION_PROPERTY)) {
         version = extractStructuredVersion(infoNode.path(SERVICE_VERSION_PROPERTY));
      } else {
         String description = infoNode.path("description").asText();
         if (description != null && description.contains(SERVICE_VERSION_PROPERTY + "=")) {
            description = description.substring(description.indexOf(SERVICE_VERSION_PROPERTY + "="));
            if (description.indexOf(' ') > -1) {
               description = description.substring(0, description.indexOf(' '));
            }
            if (description.split("=").length > 1) {
               version = description.split("=")[1];
            }
         }
      }

      if (version == null) {
         log.error(
               "Version property is missing in Collection. Use either 'version' for v2.1 Collection or 'version=x.y - something' description syntax.");
         throw new MockRepositoryImportException("Version property is missing in Collection description");
      }
      service.setVersion(version);
   }

   private String extractStructuredVersion(JsonNode versionNode) {
      String version = null;
      if (versionNode.has("identifier")) {
         version = versionNode.path("identifier").asText();
      } else if (versionNode.has("major") && versionNode.has("minor") && versionNode.has("patch")) {
         version = versionNode.path("major").asText();
         version += "." + versionNode.path("minor").asText();
         version += "." + versionNode.path("patch").asText();
      } else {
         version = versionNode.asText();
      }
      return version;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = new ArrayList<>();

      // Build a suitable name.
      String name = service.getName() + "-" + service.getVersion() + ".json";

      Resource resource = new Resource();
      resource.setName(name);
      resource.setType(ResourceType.POSTMAN_COLLECTION);
      resource.setContent(collectionContent);
      results.add(resource);

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {

      if (isV2Collection) {
         return getMessageDefinitionsV2(service, operation);
      } else {
         throw new MockRepositoryImportException(COLLECTION_VERSION_ERROR_MESSAGE);
      }
   }

   private List<Exchange> getMessageDefinitionsV2(Service service, Operation operation) {
      Map<Request, Response> result = new HashMap<>();

      Iterator<JsonNode> items = collection.path("item").elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         result.putAll(getMessageDefinitionsV2("", item, operation));
      }

      // Adapt map to list of Exchanges.
      return result.entrySet().stream().map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
   }

   private Map<Request, Response> getMessageDefinitionsV2(String folderName, JsonNode itemNode, Operation operation) {
      log.debug("Extracting message definitions in folder {}", folderName);
      Map<Request, Response> result = new HashMap<>();
      String itemNodeName = itemNode.path("name").asText();

      if (!itemNode.has(REQUEST_NODE)) {
         // Item is simply a folder that may contain some other folders recursively.
         Iterator<JsonNode> items = itemNode.path("item").elements();
         while (items.hasNext()) {
            JsonNode item = items.next();
            result.putAll(getMessageDefinitionsV2(folderName + "/" + itemNodeName, item, operation));
         }
      } else {
         // Item is here an operation description.
         String operationName = buildOperationName(itemNode, folderName);

         // Select item based onto operation name.
         if (PostmanUtil.areOperationsEquivalent(operation.getName(), operationName)) {
            // If we previously override the dispatcher with a Fallback, we must be sure to get wrapped elements.
            DispatchCriteriaHelper.DispatcherDetails details = DispatchCriteriaHelper
                  .extractDispatcherWithRules(operation);
            String rootDispatcher = details.rootDispatcher();
            String rootDispatcherRules = details.rootDispatcherRules();

            Iterator<JsonNode> responses = itemNode.path(RESPONSE_NODE).elements();
            while (responses.hasNext()) {
               JsonNode responseNode = responses.next();
               JsonNode requestNode = responseNode.path("originalRequest");

               // Build dispatchCriteria and complete operation resourcePaths.
               String dispatchCriteria = null;
               String requestUrl = requestNode.path("url").path("raw").asText();

               if (DispatchStyles.URI_PARAMS.equals(rootDispatcher)) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(),
                        requestUrl);
                  // We only need the pattern here.
                  operation.addResourcePath(extractResourcePath(requestUrl, null));
               } else if (DispatchStyles.URI_PARTS.equals(rootDispatcher)) {
                  Map<String, String> parts = buildRequestParts(requestNode);
                  dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parts);
                  // We should complete resourcePath here.
                  String resourcePath = extractResourcePath(requestUrl, null);
                  operation.addResourcePath(URIBuilder.buildURIFromPattern(resourcePath, parts));
               } else if (DispatchStyles.URI_ELEMENTS.equals(rootDispatcher)) {
                  Map<String, String> parts = buildRequestParts(requestNode);
                  dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parts);
                  dispatchCriteria += DispatchCriteriaHelper.extractFromURIParams(rootDispatcherRules, requestUrl);
                  // We should complete resourcePath here.
                  String resourcePath = extractResourcePath(requestUrl, null);
                  operation.addResourcePath(URIBuilder.buildURIFromPattern(resourcePath, parts));
               } else if (DispatchStyles.QUERY_HEADER.equals(rootDispatcher)) {
                  Map<String, String> paramsMap = buildRequestHeaders(requestNode);
                  dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(rootDispatcherRules, paramsMap);
                  // We only need the pattern here.
                  operation.addResourcePath(extractResourcePath(requestUrl, null));
               } else if (DispatchStyles.QUERY_ARGS.equals(rootDispatcher)) {
                  // This dispatcher is used for GraphQL
                  if (requestNode.path("body").has(GRAPHQL_NODE)) {
                     // We must transform JSON representing variables into a Map<String, String>
                     // before building a dispatchCriteria matching the rules.
                     String variables = requestNode.path("body").path(GRAPHQL_NODE).path(VARIABLES_NODE).asText();
                     dispatchCriteria = extractQueryArgsCriteria(rootDispatcherRules, variables);
                  }
                  // or for gRPC.
                  if (requestNode.path("body").has("raw")) {
                     String variables = requestNode.path("body").path("raw").asText();
                     dispatchCriteria = extractQueryArgsCriteria(rootDispatcherRules, variables);
                  }

               } else {
                  // If dispatcher has been overriden (to SCRIPT for example), we should still put a generic resourcePath
                  // (maybe containing : parts) to later force operation matching at the mock controller level. Only do that
                  // when request url is not empty (means not the root url like POST /order).
                  if (requestUrl != null && !requestUrl.isEmpty()) {
                     operation.addResourcePath(extractResourcePath(requestUrl, null));
                     log.debug("Added operation generic resource path: {}", operation.getResourcePaths());
                  }
               }

               Request request = buildRequest(requestNode, responseNode.path("name").asText());
               Response response = buildResponse(responseNode, dispatchCriteria);
               result.put(request, response);
            }
         }
      }
      return result;
   }

   private Request buildRequest(JsonNode requestNode, String name) {
      Request request = new Request();
      request.setName(name);

      if (isV2Collection) {
         request.setHeaders(buildHeaders(requestNode.path("header")));
         if (requestNode.has("body") && requestNode.path("body").has("raw")) {
            request.setContent(requestNode.path("body").path("raw").asText());
         } else if (requestNode.has("body") && requestNode.path("body").has(GRAPHQL_NODE)) {
            // We got to rebuild a specific HTTP request for GraphQL.
            String query = requestNode.path("body").path(GRAPHQL_NODE).path(QUERY_NODE).asText();
            String variables = requestNode.path("body").path(GRAPHQL_NODE).path(VARIABLES_NODE).asText();
            // Initialize with query field.
            StringBuilder requestContent = new StringBuilder("{\"query\": \"").append(query.replace("\n", "\\n"))
                  .append("\"");

            try {
               // See if we have to add variables.
               JsonNode variablesNode = mapper.readTree(variables);
               if (variablesNode.isObject() && !variablesNode.isEmpty()) {
                  requestContent.append(", \"variables\": ").append(variables);
               }
            } catch (JsonProcessingException jpe) {
               log.warn("Json processing excpetion while parsing GraphQL variables, ignoring them: {}",
                     jpe.getMessage());
            }
            requestContent.append("}");
            request.setContent(requestContent.toString());
         }
         if (requestNode.path("url").has(VARIABLE_NODE)) {
            JsonNode variablesNode = requestNode.path("url").path(VARIABLE_NODE);
            for (JsonNode variableNode : variablesNode) {
               Parameter param = new Parameter();
               param.setName(variableNode.path("key").asText());
               param.setValue(variableNode.path(VALUE_NODE).asText());
               request.addQueryParameter(param);
            }
         }
         if (requestNode.path("url").has(QUERY_NODE)) {
            JsonNode queryNode = requestNode.path("url").path(QUERY_NODE);
            for (JsonNode variableNode : queryNode) {
               Parameter param = new Parameter();
               param.setName(variableNode.path("key").asText());
               param.setValue(variableNode.path(VALUE_NODE).asText());
               request.addQueryParameter(param);
            }
         }
      } else {
         request.setHeaders(buildHeaders(requestNode.path("headers")));
      }
      return request;
   }

   private Map<String, String> buildRequestParts(JsonNode requestNode) {
      Map<String, String> parts = new HashMap<>();
      if (requestNode.has("url") && requestNode.path("url").has(VARIABLE_NODE)) {
         Iterator<JsonNode> variables = requestNode.path("url").path(VARIABLE_NODE).elements();
         while (variables.hasNext()) {
            JsonNode variable = variables.next();
            parts.put(variable.path("key").asText(), variable.path(VALUE_NODE).asText());
         }
      }
      return parts;
   }

   private Map<String, String> buildRequestHeaders(JsonNode requestNode) {
      Map<String, String> headers = new HashMap<>();
      if (requestNode.has("header")) {
         Iterator<JsonNode> headerNodes = requestNode.path("header").elements();
         while (headerNodes.hasNext()) {
            JsonNode headerNode = headerNodes.next();
            headers.put(headerNode.path("key").asText(), headerNode.path(VALUE_NODE).asText());
         }
      }
      return headers;
   }

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

   private Response buildResponse(JsonNode responseNode, String dispatchCriteria) {
      Response response = new Response();
      response.setName(responseNode.path("name").asText());

      if (isV2Collection) {
         response.setStatus(responseNode.path("code").asText("200"));
         response.setHeaders(buildHeaders(responseNode.path("header")));
         response.setContent(responseNode.path("body").asText(""));
      } else {
         response.setStatus(responseNode.path("responseCode").path("code").asText());
         response.setHeaders(buildHeaders(responseNode.path("headers")));
         response.setContent(responseNode.path("text").asText());
      }
      if (response.getHeaders() != null) {
         for (Header header : response.getHeaders()) {
            if (header.getName().equalsIgnoreCase("Content-Type")) {
               response.setMediaType(header.getValues().toArray(new String[] {})[0]);
            }
         }
      }
      // For V1 Collection, if no Content-Type header but response expressed as a language,
      // assume it is its content-type.
      if (!isV2Collection && response.getMediaType() == null) {
         if ("json".equals(responseNode.path("language").asText())) {
            response.setMediaType("application/json");
         }
      }
      // For V2 Collection, if no Content-Type header but response expressed as a language,
      // assume it is its content-type.
      if (isV2Collection && response.getMediaType() == null) {
         if ("json".equals(responseNode.path("_postman_previewlanguage").asText())) {
            response.setMediaType("application/json");
         } else if ("xml".equals(responseNode.path("_postman_previewlanguage").asText())) {
            response.setMediaType("text/xml");
         }
      }
      response.setDispatchCriteria(dispatchCriteria);
      return response;
   }

   private Set<Header> buildHeaders(JsonNode headerNode) {
      if (headerNode == null || headerNode.isEmpty()) {
         return null;
      }

      // Prepare and map the set of headers.
      Set<Header> headers = new HashSet<>();
      Iterator<JsonNode> items = headerNode.elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         Header header = new Header();
         header.setName(item.path("key").asText());
         Set<String> values = new HashSet<>();
         values.add(item.path(VALUE_NODE).asText());
         header.setValues(values);
         headers.add(header);
      }
      return headers;
   }

   /** Extract the list of operations from Collection. */
   private List<Operation> extractOperations() throws MockRepositoryImportException {
      if (isV2Collection) {
         return extractOperationsV2();
      }
      throw new MockRepositoryImportException(COLLECTION_VERSION_ERROR_MESSAGE);
   }

   private List<Operation> extractOperationsV2() {
      // Items corresponding to same operations may be defined multiple times in Postman
      // with different names and resource path. We have to track them to complete them in second step.
      Map<String, Operation> collectedOperations = new HashMap<>();

      Iterator<JsonNode> items = collection.path("item").elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         extractOperationV2("", item, collectedOperations);
      }

      return new ArrayList<>(collectedOperations.values());
   }

   private void extractOperationV2(String folderName, JsonNode itemNode, Map<String, Operation> collectedOperations) {
      log.debug("Extracting operation in folder {}", folderName);
      String itemNodeName = itemNode.path("name").asText();

      // Item may be a folder or an operation description.
      if (!itemNode.has(REQUEST_NODE)) {
         // Item is simply a folder that may contain some other folders recursively.
         Iterator<JsonNode> items = itemNode.path("item").elements();
         while (items.hasNext()) {
            JsonNode item = items.next();
            extractOperationV2(folderName + "/" + itemNodeName, item, collectedOperations);
         }
      } else {
         // Item is here an operation description.
         String operationName = buildOperationName(itemNode, folderName);
         Operation operation = collectedOperations.get(operationName);
         String url = itemNode.path(REQUEST_NODE).path("url").asText("");
         if ("".equals(url)) {
            url = itemNode.path(REQUEST_NODE).path("url").path("raw").asText("");
         }

         // Collection may have been used for testing so it may contain a valid URL with prefix that will bother us.
         // Ex: http://localhost:8080/prefix1/prefix2/order/123456 or http://petstore.swagger.io/v2/pet/1. Trim it.
         if (url.contains(folderName + "/")) {
            url = removeProtocolAndHostPort(url);
         }

         if (operation == null) {
            // Build a new operation.
            operation = new Operation();
            operation.setName(operationName);

            // Complete with REST specific fields.
            operation.setMethod(itemNode.path(REQUEST_NODE).path("method").asText());

            // Deal with dispatcher stuffs.
            if (urlHasParameters(url) && urlHasParts(url)) {
               operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(url) + " ?? "
                     + DispatchCriteriaHelper.extractParamsFromURI(url));
               operation.setDispatcher(DispatchStyles.URI_ELEMENTS);
            } else if (urlHasParameters(url)) {
               operation.setDispatcherRules(DispatchCriteriaHelper.extractParamsFromURI(url));
               operation.setDispatcher(DispatchStyles.URI_PARAMS);
               operation.addResourcePath(extractResourcePath(url, null));
            } else if (urlHasParts(url)) {
               operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(url));
               operation.setDispatcher(DispatchStyles.URI_PARTS);
            } else {
               operation.addResourcePath(extractResourcePath(url, null));
               log.debug("Added operation generic resource path: {}", operation.getResourcePaths());
            }
         }

         // Do not deal with resource path now as it will be done when extracting messages.
         collectedOperations.put(operationName, operation);
      }
   }

   /**
    * Build a coherent operation name from the JsonNode of collection representing operation (ie. having a request item)
    * and an operationNameRadix (ie. a subcontext or nested subcontext folder where operation is stored).
    * @param operationNode JSON node for operation
    * @param folderName    String representing radix of operation name
    * @return Operation name
    */
   public static String buildOperationName(JsonNode operationNode, String folderName) {
      String url = operationNode.path(REQUEST_NODE).path("url").asText("");
      if ("".equals(url)) {
         url = operationNode.path(REQUEST_NODE).path("url").path("raw").asText();
      }

      // New way of computing operation name.
      if (url.indexOf('?') != -1) {
         // Remove query parameters.
         url = url.substring(0, url.indexOf('?'));
      }
      // Remove protocol pragma and host/port stuffs.
      url = removeProtocolAndHostPort(url);
      return operationNode.path(REQUEST_NODE).path("method").asText() + " " + url;
   }

   /**
    * Extract a resource path from a complete url and an optional operationName radix (context or subcontext).
    * https://petstore-api-2445581593402.apicast.io:443/v2/pet/findByStatus?user_key=998bac0775b1d5f588e0a6ca7c11b852&status=available
    * => /v2/pet/findByStatus if no operationNameRadix => /pet/findByStatus if operationNameRadix=/pet
    */
   private String extractResourcePath(String url, String operationNameRadix) {
      // Remove protocol, host and port specification.
      String result = removeProtocolAndHostPort(url);
      // Remove trailing parameters.
      if (result.indexOf('?') != -1) {
         result = result.substring(0, result.indexOf('?'));
      }
      // Remove prefix of radix if specified.
      if (operationNameRadix != null && result.contains(operationNameRadix)) {
         result = result.substring(result.indexOf(operationNameRadix));
      }
      // Remove trailing / if present.
      if (result.endsWith("/")) {
         result = result.substring(0, result.length() - 1);
      }
      return result;
   }

   /** Check parameters presence into given url. */
   private static boolean urlHasParameters(String url) {
      return url.indexOf('?') != -1 && url.indexOf('=') != -1;
   }

   /** Check variables parts presence into given url. */
   private static boolean urlHasParts(String url) {
      return url.indexOf("/:") != -1;
   }

   private static String removeProtocolAndHostPort(String url) {
      if (url.startsWith("https://")) {
         url = url.substring("https://".length());
      }
      if (url.startsWith("http://")) {
         url = url.substring("http://".length());
      }
      // Remove host and port specification if any.
      if (url.indexOf('/') != -1) {
         url = url.substring(url.indexOf('/'));
      }
      return url;
   }
}
