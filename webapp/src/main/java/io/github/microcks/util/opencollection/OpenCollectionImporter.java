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
package io.github.microcks.util.opencollection;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.ParameterLocation;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.stream.Collectors;

/**
 * Implementation of MockRepositoryImporter that uses an OpenCollection format for building domain objects. Supports
 * OpenCollection v1.x format.
 * @author krisrr3
 */
public class OpenCollectionImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenCollectionImporter.class);

   /** OpenCollection property that references service version property. */
   public static final String SERVICE_VERSION_PROPERTY = "version";
   /** Error message for unsupported versions. */
   public static final String COLLECTION_VERSION_ERROR_MESSAGE = "Only OpenCollection v1.x is supported.";

   private static final String INFO_NODE = "info";
   private static final String OPENCOLLECTION_NODE = "opencollection";
   private static final String ITEMS_NODE = "items";
   private static final String REQUEST_NODE = "request";
   private static final String RESPONSE_NODE = "response";
   private static final String PARAMS_NODE = "params";
   private static final String HEADERS_NODE = "headers";
   private static final String BODY_NODE = "body";
   private static final String URL_NODE = "url";
   private static final String METHOD_NODE = "method";
   private static final String STATUS_NODE = "status";
   private static final String TYPE_NODE = "type";
   private static final String NAME_NODE = "name";
   private static final String VALUE_NODE = "value";
   private static final String DATA_NODE = "data";

   private ObjectMapper mapper;
   private JsonNode collection;
   private String collectionContent;

   /**
    * Build a new importer.
    * @param collectionFilePath The path to local OpenCollection file
    * @throws IOException if project file cannot be found or read.
    */
   public OpenCollectionImporter(String collectionFilePath) throws IOException {
      try {
         // Read Json bytes.
         byte[] jsonBytes = Files.readAllBytes(Paths.get(collectionFilePath));
         collectionContent = new String(jsonBytes, StandardCharsets.UTF_8);
         // Convert them to Node using Jackson object mapper.
         mapper = new ObjectMapper();
         collection = mapper.readTree(jsonBytes);
      } catch (Exception e) {
         log.error("Exception while parsing OpenCollection file " + collectionFilePath, e);
         throw new IOException("OpenCollection file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Validate OpenCollection version.
      if (!collection.has(OPENCOLLECTION_NODE)) {
         throw new MockRepositoryImportException("Missing 'opencollection' version field");
      }

      String version = collection.path(OPENCOLLECTION_NODE).asText();
      if (!OpenCollectionUtil.isValidOpenCollectionVersion(version)) {
         throw new MockRepositoryImportException(COLLECTION_VERSION_ERROR_MESSAGE + " Found: " + version);
      }

      // Build a new service.
      Service service = new Service();

      if (collection.has(INFO_NODE)) {
         fillServiceDefinition(service);
      } else {
         throw new MockRepositoryImportException("Missing 'info' node in OpenCollection");
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
      service.setName(infoNode.path(NAME_NODE).asText());
      service.setType(ServiceType.REST);

      String version = null;
      if (infoNode.has(SERVICE_VERSION_PROPERTY)) {
         version = infoNode.path(SERVICE_VERSION_PROPERTY).asText();
      }

      if (version == null || version.isEmpty()) {
         log.error("Version property is missing in OpenCollection info section");
         throw new MockRepositoryImportException("Version property is missing in OpenCollection info");
      }
      service.setVersion(version);
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) {
      List<Resource> results = new ArrayList<>();

      // Build a suitable name.
      String name = service.getName() + "-" + service.getVersion() + ".json";

      Resource resource = new Resource();
      resource.setName(name);
      resource.setType(ResourceType.OPEN_COLLECTION);
      resource.setContent(collectionContent);
      results.add(resource);

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      Map<Request, Response> result = new HashMap<>();

      Iterator<JsonNode> items = collection.path(ITEMS_NODE).elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         result.putAll(getMessageDefinitions("", item, operation));
      }

      // Adapt map to list of Exchanges.
      return result.entrySet().stream().map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
   }

   private Map<Request, Response> getMessageDefinitions(String folderName, JsonNode itemNode, Operation operation) {
      log.debug("Extracting message definitions in folder {}", folderName);
      Map<Request, Response> result = new HashMap<>();
      String itemNodeName = itemNode.path(NAME_NODE).asText();
      String itemType = itemNode.path(TYPE_NODE).asText();

      if ("folder".equals(itemType)) {
         // Item is a folder that may contain other items recursively.
         Iterator<JsonNode> items = itemNode.path(ITEMS_NODE).elements();
         while (items.hasNext()) {
            JsonNode item = items.next();
            result.putAll(getMessageDefinitions(folderName + "/" + itemNodeName, item, operation));
         }
      } else if ("request".equals(itemType) && itemNode.has(REQUEST_NODE)) {
         // Item is a request description.
         String operationName = buildOperationName(itemNode, folderName);

         // Select item based on operation name.
         if (OpenCollectionUtil.areOperationsEquivalent(operation.getName(), operationName)) {
            // If we previously override the dispatcher with a Fallback, we must get wrapped elements.
            DispatchCriteriaHelper.DispatcherDetails details = DispatchCriteriaHelper
                  .extractDispatcherWithRules(operation);
            String rootDispatcher = details.rootDispatcher();
            String rootDispatcherRules = details.rootDispatcherRules();

            JsonNode requestNode = itemNode.path(REQUEST_NODE);
            Iterator<JsonNode> responses = itemNode.path(RESPONSE_NODE).elements();

            while (responses.hasNext()) {
               JsonNode responseNode = responses.next();

               // Build request and response pair.
               String requestUrl = requestNode.path(URL_NODE).asText();
               String dispatchCriteria = null;

               if (DispatchStyles.URI_PARAMS.equals(rootDispatcher)) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIParams(operation.getDispatcherRules(),
                        requestUrl);
                  operation.addResourcePath(extractResourcePath(requestUrl, null));
               } else if (DispatchStyles.URI_PARTS.equals(rootDispatcher)) {
                  Map<String, String> parts = buildRequestParts(requestNode);
                  dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parts);
                  String resourcePath = extractResourcePath(requestUrl, null);
                  operation.addResourcePath(URIBuilder.buildURIFromPattern(resourcePath, parts));
               }

               Request request = buildRequest(requestNode, operationName);
               Response response = buildResponse(responseNode, dispatchCriteria);

               result.put(request, response);
            }
         }
      }

      return result;
   }

   private List<Operation> extractOperations() throws MockRepositoryImportException {
      Map<String, Operation> operations = new HashMap<>();

      Iterator<JsonNode> items = collection.path(ITEMS_NODE).elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         extractOperations("", item, operations);
      }

      return new ArrayList<>(operations.values());
   }

   private void extractOperations(String folderName, JsonNode itemNode, Map<String, Operation> operations) {
      String itemNodeName = itemNode.path(NAME_NODE).asText();
      String itemType = itemNode.path(TYPE_NODE).asText();

      if ("folder".equals(itemType)) {
         // Recurse into folder items.
         Iterator<JsonNode> items = itemNode.path(ITEMS_NODE).elements();
         while (items.hasNext()) {
            JsonNode item = items.next();
            extractOperations(folderName + "/" + itemNodeName, item, operations);
         }
      } else if ("request".equals(itemType) && itemNode.has(REQUEST_NODE)) {
         // Extract operation from request.
         String operationName = buildOperationName(itemNode, folderName);

         if (!operations.containsKey(operationName)) {
            Operation operation = new Operation();
            operation.setName(operationName);
            operation.setMethod(extractMethod(itemNode));
            operations.put(operationName, operation);
         }
      }
   }

   private String buildOperationName(JsonNode itemNode, String folderName) {
      JsonNode requestNode = itemNode.path(REQUEST_NODE);
      String method = requestNode.path(METHOD_NODE).asText("GET").toUpperCase();
      String url = requestNode.path(URL_NODE).asText();

      // Extract path from URL (remove query parameters and base URL).
      String path = extractResourcePath(url, null);

      return method + " " + path;
   }

   private String extractMethod(JsonNode itemNode) {
      JsonNode requestNode = itemNode.path(REQUEST_NODE);
      return requestNode.path(METHOD_NODE).asText("GET").toUpperCase();
   }

   private String extractResourcePath(String url, String baseUrl) {
      // Remove query parameters.
      if (url.contains("?")) {
         url = url.substring(0, url.indexOf("?"));
      }

      // Remove protocol and host if present.
      if (url.startsWith("http://") || url.startsWith("https://")) {
         int pathStart = url.indexOf("/", url.indexOf("://") + 3);
         if (pathStart > 0) {
            url = url.substring(pathStart);
         }
      }

      // Remove base URL if provided.
      if (baseUrl != null && !baseUrl.isEmpty() && url.startsWith(baseUrl)) {
         url = url.substring(baseUrl.length());
      }

      // Ensure path starts with /.
      if (!url.startsWith("/")) {
         url = "/" + url;
      }

      return url;
   }

   private Map<String, String> buildRequestParts(JsonNode requestNode) {
      Map<String, String> parts = new HashMap<>();

      if (requestNode.has(PARAMS_NODE)) {
         Iterator<JsonNode> params = requestNode.path(PARAMS_NODE).elements();
         while (params.hasNext()) {
            JsonNode param = params.next();
            String type = param.path(TYPE_NODE).asText();
            if ("path".equals(type)) {
               parts.put(param.path(NAME_NODE).asText(), param.path(VALUE_NODE).asText());
            }
         }
      }

      return parts;
   }

   private Request buildRequest(JsonNode requestNode, String operationName) {
      Request request = new Request();
      request.setName(operationName);

      // Extract headers.
      if (requestNode.has(HEADERS_NODE)) {
         Set<Header> headers = new HashSet<>();
         Iterator<JsonNode> headerNodes = requestNode.path(HEADERS_NODE).elements();
         while (headerNodes.hasNext()) {
            JsonNode headerNode = headerNodes.next();
            Header header = new Header();
            header.setName(headerNode.path(NAME_NODE).asText());
            header.setValues(Set.of(headerNode.path(VALUE_NODE).asText()));
            headers.add(header);
         }
         request.setHeaders(headers);
      }

      // Extract query parameters.
      if (requestNode.has(PARAMS_NODE)) {
         List<Parameter> parameters = new ArrayList<>();
         Iterator<JsonNode> params = requestNode.path(PARAMS_NODE).elements();
         while (params.hasNext()) {
            JsonNode param = params.next();
            String type = param.path(TYPE_NODE).asText();
            if ("query".equals(type)) {
               Parameter parameter = new Parameter();
               parameter.setName(param.path(NAME_NODE).asText());
               parameter.setValue(param.path(VALUE_NODE).asText());
               parameters.add(parameter);
            }
         }
         request.setQueryParameters(parameters);
      }

      // Extract body.
      if (requestNode.has(BODY_NODE)) {
         JsonNode bodyNode = requestNode.path(BODY_NODE);
         if (bodyNode.has(DATA_NODE)) {
            request.setContent(bodyNode.path(DATA_NODE).asText());
         }
      }

      return request;
   }

   private Response buildResponse(JsonNode responseNode, String dispatchCriteria) {
      Response response = new Response();
      response.setName(responseNode.path(NAME_NODE).asText());
      response.setDispatchCriteria(dispatchCriteria);

      // Extract status.
      if (responseNode.has(STATUS_NODE)) {
         response.setStatus(String.valueOf(responseNode.path(STATUS_NODE).asInt()));
      }

      // Extract headers.
      if (responseNode.has(HEADERS_NODE)) {
         Set<Header> headers = new HashSet<>();
         Iterator<JsonNode> headerNodes = responseNode.path(HEADERS_NODE).elements();
         while (headerNodes.hasNext()) {
            JsonNode headerNode = headerNodes.next();
            Header header = new Header();
            header.setName(headerNode.path(NAME_NODE).asText());
            header.setValues(Set.of(headerNode.path(VALUE_NODE).asText()));
            headers.add(header);
         }
         response.setHeaders(headers);
      }

      // Extract body.
      if (responseNode.has(BODY_NODE)) {
         JsonNode bodyNode = responseNode.path(BODY_NODE);
         if (bodyNode.has(DATA_NODE)) {
            response.setContent(bodyNode.path(DATA_NODE).asText());
         }
      }

      return response;
   }
}
