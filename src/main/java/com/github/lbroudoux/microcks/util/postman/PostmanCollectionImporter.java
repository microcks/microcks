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
package com.github.lbroudoux.microcks.util.postman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.lbroudoux.microcks.domain.*;
import com.github.lbroudoux.microcks.util.DispatchCriteriaHelper;
import com.github.lbroudoux.microcks.util.DispatchStyles;
import com.github.lbroudoux.microcks.util.MockRepositoryImportException;
import com.github.lbroudoux.microcks.util.MockRepositoryImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Implement of MockRepositoryImporter that uses a Postman collection for building
 * domain objects.
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
   public List<Service> getServiceDefinitions() {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();

      // Collection V2 as an info node.
      if (collection.has("info")) {
         isV2Collection = true;
         fillServiceDefinitionV2(service);
      } else {
         fillServiceDefinitionV1(service);
      }

      // Then build its operations.
      service.setOperations(extractOperations());

      result.add(service);
      return result;
   }

   private void fillServiceDefinitionV1(Service service) {
      service.setName(collection.path("name").asText());
      service.setType(ServiceType.REST);

      String version = null;
      String description = collection.path("description").asText();
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

   private void fillServiceDefinitionV2(Service service) {
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
         return getMessageDefinitionsV1(service, operation);
      }
   }

   private Map<Request, Response> getMessageDefinitionsV1(Service service, Operation operation) {
      Map<Request, Response> result = new HashMap<Request, Response>();

      Iterator<JsonNode> requests = collection.path("requests").elements();
      while (requests.hasNext()) {
         JsonNode requestNode = requests.next();
         String method = requestNode.path("method").asText();
         String url = requestNode.path("url").asText();
         String resourcePath = extractResourcePath(url);

         // Select item based onto operation Http verb (GET, POST, PUT, etc ...)
         if (operation.getMethod().equals(method)) {
            // ... then check is we have a matching resource path.
            if (operation.getResourcePaths().contains(resourcePath)) {
               JsonNode responseNode = requestNode.path("responses").get(0);

               String dispatchCriteria = null;

               if (DispatchStyles.URI_PARAMS.equals(operation.getDispatcher())) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(), url);
               } else if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operation.getName(), resourcePath);
               } else if (DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operation.getName(), resourcePath);
                  dispatchCriteria += DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(), url);
               }

               Request request = buildRequest(requestNode, requestNode.path("name").asText());
               Response response = buildResponse(responseNode, dispatchCriteria);
               result.put(request, response);
            }
         }
      }
      return result;
   }

   private Map<Request, Response> getMessageDefinitionsV2(Service service, Operation operation) {
      Map<Request, Response> result = new HashMap<Request, Response>();

      Iterator<JsonNode> items = collection.path("item").elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         String method = item.path("request").path("method").asText();
         String url = item.path("request").path("url").asText();
         String resourcePath = extractResourcePath(url);

         // Select item based onto operation Http verb (GET, POST, PUT, etc ...)
         if (operation.getMethod().equals(method)) {
            // ... then check is we have a matching resource path.
            if (operation.getResourcePaths().contains(resourcePath)) {
               JsonNode requestNode = item.path("request");
               JsonNode responseNode = item.path("response").get(0);

               String dispatchCriteria = null;

               if (DispatchStyles.URI_PARAMS.equals(operation.getDispatcher())) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(), url);
               } else if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operation.getName(), resourcePath);
               } else if (DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(operation.getName(), resourcePath);
                  dispatchCriteria += DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(), url);
               }

               Request request = buildRequest(requestNode, item.path("name").asText());
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
      } else {
         request.setHeaders(buildHeaders(requestNode.path("headers")));
      }
      return request;
   }

   private Response buildResponse(JsonNode responseNode, String dispatchCriteria) {
      Response response = new Response();
      response.setName(responseNode.path("name").asText());

      if (isV2Collection) {
         response.setStatus(responseNode.path("code").asText());
         response.setHeaders(buildHeaders(responseNode.path("header")));
      } else {
         response.setStatus(responseNode.path("responseCode").path("code").asText());
         response.setHeaders(buildHeaders(responseNode.path("headers")));
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
      response.setContent(responseNode.path("body").asText());
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
   private List<Operation> extractOperations() {

      if (isV2Collection) {
         return extractOperationsV2();
      } else {
         return extractOperationsV1();
      }
   }

   private List<Operation> extractOperationsV1() {
      // Items corresponding to same operations may be defined multiple times in Postman
      // with different names and resource path. We have to track them to complete them in second step.
      Map<String, Operation> collectedOperations = new HashMap<String, Operation>();

      Iterator<JsonNode> requests = collection.path("requests").elements();
      while (requests.hasNext()) {
         JsonNode request = requests.next();
         String requestName = request.path("name").asText();
         // findByStatus (status=available) => findByStatus
         String operationName = requestName.substring(0, requestName.indexOf(' '));

         Operation operation = collectedOperations.get(operationName);
         String url = request.path("url").asText();

         if (operation == null) {
            // Build a new operation.
            operation = new Operation();
            operation.setName(operationName);

            // Complete with REST specific fields.
            operation.setMethod(request.path("method").asText());

            // Deal with dispatcher stuffs.
            if (urlHasParameters(url)) {
               operation.setDispatcherRules(DispatchCriteriaHelper.extractParamsFromURI(url));
               operation.setDispatcher(DispatchStyles.URI_PARAMS);
            }
         }

         // Extract resource path from complete URL found in collection.
         String resourcePath = extractResourcePath(url);
         operation.addResourcePath(resourcePath);
         collectedOperations.put(operationName, operation);
      }

      // Check operations: if we got multiple resource paths for an Operation, we should
      // extracts the variable parts of the path and update dispatch criteria accordingly.
      // We can also update the operation name so that it reflects the resource path
      sanitizeCollectedOperations(collectedOperations);

      return new ArrayList<>(collectedOperations.values());
   }

   private List<Operation> extractOperationsV2() {
      // Items corresponding to same operations may be defined multiple times in Postman
      // with different names and resource path. We have to track them to complete them in second step.
      Map<String, Operation> collectedOperations = new HashMap<String, Operation>();

      Iterator<JsonNode> items = collection.path("item").elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         String itemName = item.path("name").asText();
         // findByStatus (status=available) => findByStatus
         String operationName = itemName.substring(0, itemName.indexOf(' '));

         Operation operation = collectedOperations.get(operationName);
         String url = item.path("request").path("url").asText();

         if (operation == null) {
            // Build a new operation.
            operation = new Operation();
            operation.setName(operationName);

            // Complete with REST specific fields.
            operation.setMethod(item.path("request").path("method").asText());

            // Deal with dispatcher stuffs.
            if (urlHasParameters(url)) {
               operation.setDispatcherRules(DispatchCriteriaHelper.extractParamsFromURI(url));
               operation.setDispatcher(DispatchStyles.URI_PARAMS);
            }
         }

         // Extract resource path from complete URL found in collection.
         String resourcePath = extractResourcePath(url);
         operation.addResourcePath(resourcePath);
         collectedOperations.put(operationName, operation);
      }

      // Check operations: if we got multiple resource paths for an Operation, we should
      // extracts the variable parts of the path and update dispatch criteria accordingly.
      // We can also update the operation name so that it reflects the resource path
      sanitizeCollectedOperations(collectedOperations);

      return new ArrayList<>(collectedOperations.values());
   }

   private void sanitizeCollectedOperations(Map<String, Operation> collectedOperations) {
      for (Operation operation : collectedOperations.values()) {
         if (operation.getResourcePaths().size() > 1) {
            String partsCriteria = DispatchCriteriaHelper.extractPartsFromURIs(operation.getResourcePaths());

            if (DispatchStyles.URI_PARAMS.equals(operation.getDispatcher())) {
               operation.setDispatcher(DispatchStyles.URI_ELEMENTS);
               operation.setDispatcherRules(partsCriteria + " ?? " + operation.getDispatcherRules());
            } else {
               operation.setDispatcher(DispatchStyles.URI_PARTS);
               operation.setDispatcherRules(partsCriteria);
            }

            // Replace operation name by templatized url.
            int numOfParts = partsCriteria.split("&&").length;
            String templatizedName = DispatchCriteriaHelper.extractCommonPrefix(operation.getResourcePaths());
            String commonSuffix = DispatchCriteriaHelper.extractCommonSuffix(operation.getResourcePaths());
            for (int i=1; i<numOfParts+1; i++) {
               templatizedName += "/{part" + i + "}";
            }
            if (commonSuffix != null) {
               templatizedName += commonSuffix;
            }
            operation.setName(templatizedName);
         } else {
            operation.setName(operation.getResourcePaths().get(0));
         }
      }
   }

   /**
    * Extract a resource path from a complete url.
    * https://petstore-api-2445581593402.apicast.io:443/v2/pet/findByStatus?user_key=998bac0775b1d5f588e0a6ca7c11b852&status=available => /v2/pet/findByStatus
    */
   private String extractResourcePath(String url) {
      String result = url;
      if (result.startsWith("https://") || result.startsWith("http://")) {
         result = result.substring("https://".length());
      }
      // Remove host and port specification.
      result = result.substring(result.indexOf('/'));
      // Remove trailing parameters.
      if (result.indexOf('?') != -1) {
         result = result.substring(0, result.indexOf('?'));
      }
      return result;
   }

   /** Check parameters presence into given url. */
   private static boolean urlHasParameters(String url) {
      return url.indexOf('?') != -1 && url.indexOf('=') != -1;
   }
}
