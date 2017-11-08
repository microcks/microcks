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
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Implement of MockRepositoryImporter that uses a Postman collection for building
 * domain objects. Only v2 collection format is supported.
 */
public class PostmanCollectionImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(PostmanCollectionImporter.class);

   /** Postman collection property that references service version property. */
   public static final String SERVICE_VERSION_PROPERTY = "version";

   private JsonNode collection;
   // Flag telling if V2 format is used.
   private boolean isV2Collection = false;


   /**
    * Build a new importer.
    * @param collectionFilePath The path to local SoapUI project file
    * @throws IOException if project file cannot be found or read.
    */
   public PostmanCollectionImporter(String collectionFilePath) throws IOException {
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

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();

      // Collection V2 as an info node.
      if (collection.has("info")) {
         isV2Collection = true;
         fillServiceDefinition(service);
      } else {
         throw new MockRepositoryImportException("Only Postman v2 Collection are supported.");
      }

      // Then build its operations.
      service.setOperations(extractOperations());

      result.add(service);
      return result;
   }

   private void fillServiceDefinition(Service service) {
      service.setName(collection.path("info").path("name").asText());
      service.setType(ServiceType.REST);

      String version = null;
      String description = collection.path("info").path("description").asText();
      if (description != null && description.indexOf(SERVICE_VERSION_PROPERTY + "=") != -1) {
         description = description.substring(description.indexOf(SERVICE_VERSION_PROPERTY + "="));
         description = description.substring(0, description.indexOf(' '));
         if (description.split("=").length > 1) {
            version = description.split("=")[1];
         }
      }
      if (version == null){
         // TODO Throw a typed exception here...
      }
      service.setVersion(version);
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = new ArrayList<>();
      // Non-sense on Postman collection. Just return empty result.
      return results;
   }

   @Override
   public Map<Request, Response> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException {

      if (isV2Collection) {
         return getMessageDefinitionsV2(service, operation);
      } else {
         throw new MockRepositoryImportException("Only Postman v2 Collection are supported.");
      }
   }

   private Map<Request, Response> getMessageDefinitionsV2(Service service, Operation operation) {
      Map<Request, Response> result = new HashMap<Request, Response>();

      Iterator<JsonNode> items = collection.path("item").elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         result.putAll(getMessageDefinitionsV2("", item, operation));
      }
      return result;
   }

   private Map<Request, Response> getMessageDefinitionsV2(String operationNameRadix, JsonNode itemNode, Operation operation) {
      Map<Request, Response> result = new HashMap<Request, Response>();
      String itemNodeName = itemNode.path("name").asText();

      if (!itemNode.has("request")) {
         // Item is simply a folder that may contain some other folders recursively.
         Iterator<JsonNode> items = itemNode.path("item").elements();
         while (items.hasNext()) {
            JsonNode item = items.next();
            result.putAll(getMessageDefinitionsV2(operationNameRadix + "/" + itemNodeName, item, operation));
         }
      } else {
         // Item is here an operation description.
         String operationName = buildOperationName(itemNode, operationNameRadix);

         // Select item based onto operation name.
         if (operationName.equals(operation.getName())) {
            Iterator<JsonNode> responses = itemNode.path("response").elements();
            while (responses.hasNext()) {
               JsonNode responseNode = responses.next();
               JsonNode requestNode = responseNode.path("originalRequest");

               // Build dispatchCriteria and complete operation resourcePaths.
               String dispatchCriteria = null;
               String requestUrl = requestNode.path("url").path("raw").asText();

               if (DispatchStyles.URI_PARAMS.equals(operation.getDispatcher())) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(), requestUrl);
               } else if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())) {
                  Map<String, String> parts = buildRequestParts(requestNode);
                  dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(parts);
                  // We should complete resourcePath here.
                  String resourcePath = extractResourcePath(requestUrl, operationNameRadix);
                  operation.addResourcePath(buildResourcePath(parts, resourcePath));
               } else if (DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
                  Map<String, String> parts = buildRequestParts(requestNode);
                  dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(parts);
                  dispatchCriteria += DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(), requestUrl);
                  // We should complete resourcePath here.
                  String resourcePath = extractResourcePath(requestUrl, operationNameRadix);
                  operation.addResourcePath(buildResourcePath(parts, resourcePath));
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
         }
         if (requestNode.path("url").has("variable")) {
            JsonNode variablesNode = requestNode.path("url").path("variable");
            for (JsonNode variableNode : variablesNode) {
               Parameter param = new Parameter();
               param.setName(variableNode.path("key").asText());
               param.setValue(variableNode.path("value").asText());
               request.addQueryParameter(param);
            }
         }
         if (requestNode.path("url").has("query")) {
            JsonNode queryNode = requestNode.path("url").path("query");
            for (JsonNode variableNode : queryNode) {
               Parameter param = new Parameter();
               param.setName(variableNode.path("key").asText());
               param.setValue(variableNode.path("value").asText());
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
      if (requestNode.has("url") && requestNode.path("url").has("variable")) {
         Iterator<JsonNode> variables = requestNode.path("url").path("variable").elements();
         while (variables.hasNext()) {
            JsonNode variable = variables.next();
            parts.put(variable.path("key").asText(), variable.path("value").asText());
         }
      }
      return parts;
   }

   private Response buildResponse(JsonNode responseNode, String dispatchCriteria) {
      Response response = new Response();
      response.setName(responseNode.path("name").asText());

      if (isV2Collection) {
         response.setStatus(responseNode.path("code").asText());
         response.setHeaders(buildHeaders(responseNode.path("header")));
         response.setContent(responseNode.path("body").asText());
      } else {
         response.setStatus(responseNode.path("responseCode").path("code").asText());
         response.setHeaders(buildHeaders(responseNode.path("headers")));
         response.setContent(responseNode.path("text").asText());
      }
      if (response.getHeaders() != null) {
         for (Header header : response.getHeaders()) {
            if (header.getName().equalsIgnoreCase("Content-Type")) {
               response.setMediaType(header.getValues().toArray(new String[]{})[0]);
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
      // For V1 Collection, if no Content-Type header but response expressed as a language,
      // assume it is its content-type.
      if (isV2Collection && response.getMediaType() == null) {
         if ("json".equals(responseNode.path("_postman_previewlanguage").asText())) {
            response.setMediaType("application/json");
         }
      }
      response.setDispatchCriteria(dispatchCriteria);
      return response;
   }

   private Set<Header> buildHeaders(JsonNode headerNode) {
      if (headerNode == null || headerNode.size() == 0) {
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
         values.add(item.path("value").asText());
         header.setValues(values);
         headers.add(header);
      }
      return headers;
   }

   /**
    * Extract the list of operations from Collection.
    */
   private List<Operation> extractOperations() throws MockRepositoryImportException {

      if (isV2Collection) {
         return extractOperationsV2();
      }
      throw new MockRepositoryImportException("Only Postman v2 Collection are supported.");
   }

   private List<Operation> extractOperationsV2() {
      // Items corresponding to same operations may be defined multiple times in Postman
      // with different names and resource path. We have to track them to complete them in second step.
      Map<String, Operation> collectedOperations = new HashMap<String, Operation>();

      Iterator<JsonNode> items = collection.path("item").elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         extractOperationV2("", item, collectedOperations);
      }

      return new ArrayList<>(collectedOperations.values());
   }

   private void extractOperationV2(String operationNameRadix, JsonNode itemNode, Map<String, Operation> collectedOperations) {
      String itemNodeName = itemNode.path("name").asText();

      // Item may be a folder or an operation description.
      if (!itemNode.has("request")) {
         // Item is simply a folder that may contain some other folders recursively.
         Iterator<JsonNode> items = itemNode.path("item").elements();
         while (items.hasNext()) {
            JsonNode item = items.next();
            extractOperationV2(operationNameRadix + "/" + itemNodeName, item, collectedOperations);
         }
      } else {
         // Item is here an operation description.
         String operationName = buildOperationName(itemNode, operationNameRadix);
         Operation operation = collectedOperations.get(operationName);
         String url = itemNode.path("request").path("url").asText("");
         if ("".equals(url)) {
            url = itemNode.path("request").path("url").path("raw").asText("");
         }

         // Collection may have been used for testing so it may contain a valid URL with prefix that will bother us.
         // Ex: http://localhost:8080/prefix1/prefix2/order/123456 or http://petstore.swagger.io/v2/pet/1. Trim it.
         if (url.indexOf(operationNameRadix + "/") != -1) {
            url = url.substring(url.indexOf(operationNameRadix + "/"));
         }

         if (operation == null) {
            // Build a new operation.
            operation = new Operation();
            operation.setName(operationName);

            // Complete with REST specific fields.
            operation.setMethod(itemNode.path("request").path("method").asText());

            // Deal with dispatcher stuffs.
            if (urlHasParameters(url) && urlHasParts(url)) {
               operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(url)
                     + " ?? " + DispatchCriteriaHelper.extractParamsFromURI(url));
               operation.setDispatcher(DispatchStyles.URI_ELEMENTS);
            } else if (urlHasParameters(url)) {
               operation.setDispatcherRules(DispatchCriteriaHelper.extractParamsFromURI(url));
               operation.setDispatcher(DispatchStyles.URI_PARAMS);
               operation.addResourcePath(extractResourcePath(url, operationNameRadix));
            } else if (urlHasParts(url)) {
               operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(url));
               operation.setDispatcher(DispatchStyles.URI_PARTS);
            } else {
               operation.addResourcePath(extractResourcePath(url, operationNameRadix));
            }
         }

         // Do not deal with resource path now as it will be done when extracting messages.
         collectedOperations.put(operationName, operation);
      }
   }

   /**
    * Build a coherent operation name from the JsonNode of collection representing operation (ie. having a
    * request item) and an operationNameRadix (ie. a subcontext or nested subcontext folder where operation
    * is stored).
    */
   public static String buildOperationName(JsonNode operationNode, String operationNameRadix) {
      String url = operationNode.path("request").path("url").asText("");
      if ("".equals(url)) {
         url = operationNode.path("request").path("url").path("raw").asText();
      }
      String nameSuffix = url.substring(url.lastIndexOf(operationNameRadix) + operationNameRadix.length());
      if (nameSuffix.indexOf('?') != -1) {
         nameSuffix = nameSuffix.substring(0, nameSuffix.indexOf('?'));
      }
      operationNameRadix += nameSuffix;

      return operationNode.path("request").path("method").asText() + " " + operationNameRadix;
   }

   /**
    * Extract a resource path from a complete url and an optional operationName radix (context or subcontext).
    * https://petstore-api-2445581593402.apicast.io:443/v2/pet/findByStatus?user_key=998bac0775b1d5f588e0a6ca7c11b852&status=available
    *    => /v2/pet/findByStatus if no operationNameRadix
    *    => /pet/findByStatus if operationNameRadix=/pet
    */
   private String extractResourcePath(String url, String operationNameRadix) {
      String result = url;
      if (result.startsWith("https://")) {
         result = result.substring("https://".length());
      }
      if (result.startsWith("http://")) {
         result = result.substring("http://".length());
      }
      // Remove host and port specification.
      result = result.substring(result.indexOf('/'));
      // Remove trailing parameters.
      if (result.indexOf('?') != -1) {
         result = result.substring(0, result.indexOf('?'));
      }
      // Remove prefix of radix if specified.
      if (operationNameRadix != null && result.indexOf(operationNameRadix) != -1) {
         result = result.substring(result.indexOf(operationNameRadix));
      }
      return result;
   }

   /**
    * Build a resource path a Map for parts and a patter url
    * <(id:123)> && /order/:id => /order/123
    */
   private String buildResourcePath(Map<String, String> parts, String patternUrl) {
      StringBuilder result = new StringBuilder();
      for (String token : patternUrl.split("/")) {
         if (token.startsWith(":") && token.length() > 1) {
            result.append("/").append(parts.get(token.substring(1)));
         } else if (token.length() > 0) {
            result.append("/").append(token);
         }
      }
      return result.toString();
   }

   /** Check parameters presence into given url. */
   private static boolean urlHasParameters(String url) {
      return url.indexOf('?') != -1 && url.indexOf('=') != -1;
   }

   /** Check variables parts presence into given url. */
   private static boolean urlHasParts(String url) {
      return url.indexOf("/:") != -1;
   }
}
