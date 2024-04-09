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
package io.github.microcks.util.har;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchCriteriaHelper;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of MockRepositoryImporter that deals with HAR or HTTP Archive 1.2 files. See
 * https://w3c.github.io/web-performance/specs/HAR/Overview.html and http://www.softwareishard.com/blog/har-12-spec/ for
 * explanations
 * @author laurent
 */
public class HARImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(HARImporter.class);

   /** The starter marker for the comment referencing microcks service and version identifiers. */
   public static final String MICROCKS_ID_STARTER = "microcksId:";

   /** The started marker for the comment referencing the prefix to identify API and remove from URLs. */
   public static final String API_PREFIX_STARTER = "apiPrefix:";

   protected static final String REQUEST_NODE = "request";
   protected static final String RESPONSE_NODE = "response";

   protected static final String GRAPHQL_VARIABLES_NODE = "variables";
   protected static final String GRAPHQL_QUERY_NODE = "query";

   private ObjectMapper jsonMapper;

   private JsonNode spec;

   private String apiPrefix;

   private Map<Operation, List<JsonNode>> operationToEntriesMap = new HashMap<>();

   private static final List<String> VALID_VERSIONS = List.of("1.1", "1.2");

   private static final List<String> INVALID_ENTRY_EXTENSIONS = List.of(".ico", ".html", ".css", ".js", ".png", ".jpg",
         ".ttf", ".woff2");

   private static final List<String> UNWANTED_HEADERS = List.of("host", "sec-fetch-dest", "sec-fetch-mode",
         "sec-fetch-user", "sec-fetch-site", "sec-gpc", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform",
         "upgrade-insecure-requests", "user-agent", "date", "vary");


   /**
    * Build a new importer.
    * @param specificationFilePath The path to local HAR archive file
    * @throws IOException if project file cannot be found or read.
    */
   public HARImporter(String specificationFilePath) throws IOException {
      File specificationFile = new File(specificationFilePath);
      jsonMapper = new ObjectMapper();
      spec = jsonMapper.readTree(specificationFile);
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> results = new ArrayList<>();

      // Start checking version and comment.
      String version = spec.path("log").path("version").asText();
      if (!VALID_VERSIONS.contains(version)) {
         throw new MockRepositoryImportException(
               "HAR version is not supported. Currently supporting: " + VALID_VERSIONS);
      }
      String comment = spec.path("log").path("comment").asText();
      if (comment == null || comment.length() == 0) {
         throw new MockRepositoryImportException(
               "Expecting a comment in HAR log to specify Microcks service identifier");
      }

      // We can start building something.
      Service service = new Service();
      service.setType(ServiceType.REST);

      // 1st thing: look for comments to get service and version identifiers.
      String[] commentLines = comment.split("\\r?\\n|\\r");
      for (String commentLine : commentLines) {
         if (commentLine.trim().startsWith(MICROCKS_ID_STARTER)) {
            String identifiers = commentLine.trim().substring(MICROCKS_ID_STARTER.length());

            if (identifiers.indexOf(":") != -1) {
               String[] serviceAndVersion = identifiers.split(":");
               service.setName(serviceAndVersion[0].trim());
               service.setVersion(serviceAndVersion[1].trim());
            } else {
               log.error("microcksId comment is malformed. Expecting \'microcksId: <API_name>:<API_version>\'");
               throw new MockRepositoryImportException(
                     "microcksId comment is malformed. Expecting \'microcksId: <API_name>:<API_version>\'");
            }
         } else if (commentLine.trim().startsWith(API_PREFIX_STARTER)) {
            apiPrefix = commentLine.trim().substring(API_PREFIX_STARTER.length()).trim();
            log.info("Found an API prefix to use for shortening URLs: {}", apiPrefix);
         }
      }
      if (service.getName() == null || service.getVersion() == null) {
         log.error("No microcksId: comment found into GraphQL schema to get API name and version");
         throw new MockRepositoryImportException(
               "No microcksId: comment found into GraphQL schema to get API name and version");
      }

      // Inspect requests content to determine the most probable service type.
      Map<ServiceType, Integer> requestsCounters = countRequestsByServiceType(
            spec.path("log").path("entries").elements());
      if ((requestsCounters.get(ServiceType.GRAPHQL) > requestsCounters.get(ServiceType.REST))
            && (requestsCounters.get(ServiceType.GRAPHQL) > requestsCounters.get(ServiceType.SOAP_HTTP))) {
         service.setType(ServiceType.GRAPHQL);
      } else if ((requestsCounters.get(ServiceType.SOAP_HTTP) > requestsCounters.get(ServiceType.REST))
            && (requestsCounters.get(ServiceType.SOAP_HTTP) > requestsCounters.get(ServiceType.GRAPHQL))) {
         service.setType(ServiceType.SOAP_HTTP);
      }

      // Extract service operations.
      service.setOperations(extractOperations(service.getType(), spec.path("log").path("entries").elements()));

      results.add(service);
      return results;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException {
      return new ArrayList<>();
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      Map<Request, Response> result = new HashMap<>();

      Optional<Operation> opOperation = operationToEntriesMap.keySet().stream()
            .filter(op -> op.getName().equals(operation.getName()))
            .filter(op -> op.getMethod().equals(operation.getMethod())).findFirst();

      if (opOperation.isPresent()) {
         // If we previously override the dispatcher with a Fallback, we must be sure to get wrapped elements.
         DispatchCriteriaHelper.DispatcherDetails details = DispatchCriteriaHelper
               .extractDispatcherWithRules(operation);
         String rootDispatcher = details.rootDispatcher();
         String rootDispatcherRules = details.rootDispatcherRules();

         List<JsonNode> operationEntries = operationToEntriesMap.get(opOperation.get());
         for (JsonNode entry : operationEntries) {
            JsonNode requestNode = entry.path(REQUEST_NODE);
            JsonNode responseNode = entry.path(RESPONSE_NODE);
            String requestUrl = shortenURL(requestNode.path("url").asText());
            log.debug("Extracting message definitions for entry url {}", requestUrl);

            // startedDateTIme is the only "unique" identifier for an entry...
            String name = entry.path("startedDateTime").asText();
            Request request = buildRequest(requestNode, name);

            // Build dispatchCriteria before building response.
            String dispatchCriteria = null;

            if (DispatchStyles.URI_PARAMS.equals(rootDispatcher)) {
               dispatchCriteria = DispatchCriteriaHelper.extractFromURIParams(rootDispatcherRules, requestUrl);
            } else if (DispatchStyles.URI_PARTS.equals(rootDispatcher)) {
               // We may have null dispatcher rules here if just one request
               // (as it prevents detecting patterns in URL and deducing parts)
               if (rootDispatcherRules != null) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(rootDispatcherRules,
                        removeVerbFromURL(operation.getName()), requestUrl);
               }
            } else if (DispatchStyles.URI_ELEMENTS.equals(rootDispatcher)) {
               // We may have null dispatcher rules here if just one request
               // (as it prevents detecting patterns in URL and deducing parts)
               if (rootDispatcherRules != null) {
                  dispatchCriteria = DispatchCriteriaHelper.extractFromURIPattern(rootDispatcherRules,
                        removeVerbFromURL(operation.getName()), requestUrl);
               }
               dispatchCriteria += DispatchCriteriaHelper.extractFromURIParams(rootDispatcherRules, requestUrl);
            } else if (DispatchStyles.QUERY_ARGS.equals(rootDispatcher)) {
               // This dispatcher is used for GraphQL, we have to extract variables from request body.
               dispatchCriteria = extractGraphQLCriteria(rootDispatcherRules, request.getContent());
            } else {
               // If dispatcher has been overriden (to SCRIPT for example), we should still put a generic resourcePath
               // (maybe containing : parts) to later force operation matching at the mock controller level. Only do that
               // when request url is not empty (means not the root url like POST /order).
               if (requestUrl != null && requestUrl.length() > 0) {
                  operation.addResourcePath(requestUrl);
                  log.debug("Added operation generic resource path: {}", operation.getResourcePaths());
               }
            }

            if (service.getType() == ServiceType.GRAPHQL) {
               // We also have to shorten GraphQL body as we typically just display the query in Microcks.
               //adaptGraphQLRequestContent(request);
            }

            // Finalize with response and store.
            Response response = buildResponse(responseNode, name, dispatchCriteria);
            result.put(request, response);
         }
      }

      // Adapt map to list of Exchanges.
      return result.entrySet().stream().map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
   }


   /** Try to guess and count the number of requests of each type (REST, GRAPHQL, SOAP). */
   private Map<ServiceType, Integer> countRequestsByServiceType(Iterator<JsonNode> entries) {
      Map<ServiceType, Integer> requestsCounters = new EnumMap<>(ServiceType.class);
      requestsCounters.put(ServiceType.REST, 0);
      requestsCounters.put(ServiceType.GRAPHQL, 0);
      requestsCounters.put(ServiceType.SOAP_HTTP, 0);

      List<JsonNode> candidateEntries = filterValidEntries(entries);
      for (JsonNode entry : candidateEntries) {

         JsonNode responseContent = entry.path(RESPONSE_NODE).path("content");
         if (responseContent != null) {
            String mimeType = responseContent.path("mimeType").asText();
            String responseText = getContentText(responseContent);

            ServiceType responseType = ServiceType.REST;
            if ("application/json".equals(mimeType)) {
               // Try to guess between REST and GRAPHQL...
               String requestText = entry.path(REQUEST_NODE).path("postData").path("text").asText();

               if (responseText.contains("\"data\":") && (requestText.contains(GRAPHQL_QUERY_NODE)
                     || requestText.contains("mutation") || requestText.contains("fragment"))) {
                  responseType = ServiceType.GRAPHQL;
               }
            } else if ("text/xml".equals(mimeType)) {
               // Try to guess between REST and SOAP...
               if (responseText.contains(":Envelope") && responseText.contains(":Body/>")) {
                  responseType = ServiceType.SOAP_HTTP;
               }
            }
            requestsCounters.put(responseType, requestsCounters.get(responseType) + 1);
         }
      }
      return requestsCounters;
   }

   /** Extract Service Operations from entries. */
   private List<Operation> extractOperations(ServiceType serviceType, Iterator<JsonNode> entries) {
      Map<String, Operation> discoveredOperations = new HashMap<>();

      List<JsonNode> candidateEntries = filterValidEntries(entries);

      for (JsonNode entry : candidateEntries) {
         String requestMethod = entry.path(REQUEST_NODE).path("method").asText();
         String requestUrl = entry.path(REQUEST_NODE).path("url").asText();
         String requestText = entry.path(REQUEST_NODE).path("postData").path("text").asText();

         String baseRequestUrl = shortenURL(requestUrl);
         String dispatcher = DispatchStyles.URI_PARTS;
         if (baseRequestUrl.contains("?")) {
            baseRequestUrl = baseRequestUrl.substring(0, baseRequestUrl.indexOf("?"));
            dispatcher = DispatchStyles.URI_PARAMS;
         }

         // This is OK for REST only. Has to be changed for SOAP and GRAPHQL.
         String operationName = requestMethod + " " + baseRequestUrl;
         if (serviceType == ServiceType.GRAPHQL) {
            Parser requestParser = new Parser();
            try {
               Document graphqlRequest = requestParser.parseDocument(extractGraphQLQuery(requestText));
               OperationDefinition graphqlOperation = (OperationDefinition) graphqlRequest.getDefinitions().get(0);
               requestMethod = graphqlOperation.getOperation().toString();
               operationName = ((Field) graphqlOperation.getSelectionSet().getSelections().get(0)).getName();
               if ("QUERY".equals(requestMethod)) {
                  dispatcher = DispatchStyles.QUERY_ARGS;
               }
            } catch (Exception e) {
               log.warn("Error parsing GraphQL request: {}", e.getMessage());
            }
         }

         Operation existingOperation = discoveredOperations.get(operationName);
         if (existingOperation == null) {
            // Do we have other operation on different paths?
            String[] newCandidatePaths = operationName.split("/");
            final String searchedMethod = requestMethod;
            List<Operation> similarOperations = discoveredOperations.values().stream()
                  .filter(op -> searchedMethod.equals(op.getMethod()))
                  .filter(op -> newCandidatePaths.length == op.getName().split("/").length).toList();

            Operation mostSimilarOperation = findMostSimilarOperation(operationName, similarOperations);
            if (mostSimilarOperation != null) {
               List<String> uris = List.of(removeVerbFromURL(mostSimilarOperation.getName()),
                     removeVerbFromURL(operationName));

               String urlPart = DispatchCriteriaHelper.buildTemplateURLWithPartsFromURIs(uris);
               mostSimilarOperation.setName(requestMethod + " " + urlPart);

               if (DispatchStyles.URI_PARAMS.equals(mostSimilarOperation.getDispatcher())) {
                  mostSimilarOperation.setDispatcher(DispatchStyles.URI_ELEMENTS);
               } else {
                  mostSimilarOperation.setDispatcher(DispatchStyles.URI_PARTS);
                  mostSimilarOperation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIs(uris));
                  mostSimilarOperation.addResourcePath(uris.get(0));
                  mostSimilarOperation.addResourcePath(uris.get(1));
               }
               // Add current entry to similar operation.
               operationToEntriesMap.get(mostSimilarOperation).add(entry);
            } else {
               // If finally we get here, it's time to build a new operation ;-)
               Operation operation = new Operation();
               operation.setName(operationName);
               operation.setMethod(requestMethod);
               operation.setDispatcher(dispatcher);

               if (DispatchStyles.URI_PARAMS.equals(dispatcher)) {
                  operation.setDispatcherRules(DispatchCriteriaHelper.extractParamsFromURI(requestUrl));
               } else if (DispatchStyles.URI_PARTS.equals(dispatcher)) {
                  operation.addResourcePath(baseRequestUrl);
               }

               // Store this operation and initialize its entries.
               discoveredOperations.put(operationName, operation);
               List<JsonNode> operationEntries = new ArrayList<>();
               operationEntries.add(entry);
               operationToEntriesMap.put(operation, operationEntries);
            }
         } else {
            // Add current entry to existing operation.
            operationToEntriesMap.get(existingOperation).add(entry);
         }
      }

      return discoveredOperations.values().stream().toList();
   }

   /** Filter valid entries in HAR, removing static resources and calls not having the correct prefix if specified. */
   private List<JsonNode> filterValidEntries(Iterator<JsonNode> entries) {
      return Stream.generate(() -> null).takeWhile(x -> entries.hasNext()).map(next -> entries.next()).filter(entry -> {
         String url = entry.path(REQUEST_NODE).path("url").asText();
         String extension = url.substring(url.lastIndexOf("."));
         return !INVALID_ENTRY_EXTENSIONS.contains(extension);
      }).filter(entry -> {
         if (apiPrefix != null) {
            // Filter by api prefix if provided.
            String url = entry.path(REQUEST_NODE).path("url").asText();
            return url.startsWith(apiPrefix) || removeProtocolAndHostPort(url).startsWith(apiPrefix);
         }
         return true;
      }).toList();
   }

   /** Find the most similar operation among candidates. */
   private Operation findMostSimilarOperation(String operationName, List<Operation> similarOperations) {
      Operation mostSimilarOperation = null;
      if (!similarOperations.isEmpty()) {
         int maxScore = 0;
         for (Operation similarOperation : similarOperations) {
            int score = getSimilarityScore(similarOperation.getName(), operationName);
            if (score > 70 && score > maxScore) {
               maxScore = score;
               mostSimilarOperation = similarOperation;
            }
         }
      }
      return mostSimilarOperation;
   }

   /** Compute a similarity score between a base URL and a candidate one. The greater, the better. */
   private int getSimilarityScore(String base, String candidate) {
      int similarityScore = 0;
      int commonPrefixSize = 0;
      String[] basePaths = base.split("/");
      String[] candidatePaths = candidate.split("/");
      int stepScore = 100 / basePaths.length;
      boolean stillCommon = true;

      for (int i = 0; i < basePaths.length; i++) {
         String basePath = basePaths[i];
         String candidatePath = candidatePaths[i];
         if (basePath.equals(candidatePath)) {
            if (stillCommon) {
               commonPrefixSize++;
            }
            similarityScore += stepScore;
         } else {
            stillCommon = false;
         }
      }
      return similarityScore + (basePaths.length * commonPrefixSize);
   }

   /** Build Microcks request from HAR request. */
   private Request buildRequest(JsonNode requestNode, String name) {
      Request request = new Request();
      request.setName(name);
      request.setHeaders(buildHeaders(requestNode.path("headers")));
      request.setContent(requestNode.path("postData").path("text").asText());

      JsonNode queryStringNode = requestNode.path("queryString");
      if (!queryStringNode.isMissingNode() && queryStringNode.isArray()) {
         Iterator<JsonNode> queryStrings = queryStringNode.elements();
         while (queryStrings.hasNext()) {
            JsonNode queryString = queryStrings.next();
            Parameter param = new Parameter();
            param.setName(queryString.path("name").asText());
            param.setValue(queryString.path("value").asText());
            request.addQueryParameter(param);
         }
      }
      return request;
   }

   /** Build Microcks response from HAR response. */
   private Response buildResponse(JsonNode responseNode, String name, String dispatchCriteria) {
      Response response = new Response();
      response.setName(name);
      response.setStatus(responseNode.path("status").asText("200"));
      response.setHeaders(buildHeaders(responseNode.path("headers")));
      response.setContent(getContentText(responseNode.path("content")));
      response.setDispatchCriteria(dispatchCriteria);

      if (response.getHeaders() != null) {
         for (Header header : response.getHeaders()) {
            if (header.getName().equalsIgnoreCase("Content-Type")) {
               response.setMediaType(header.getValues().toArray(new String[] {})[0]);
            }
         }
      }

      return response;
   }

   /** Build Microcks headers from HAR headers. */
   private Set<Header> buildHeaders(JsonNode headerNode) {
      Map<String, Header> headers = new HashMap<>();
      Iterator<JsonNode> items = headerNode.elements();
      while (items.hasNext()) {
         JsonNode item = items.next();
         // Filter unwanted headers that are typically sent by browsers and
         // meaningless in the context of API/Service exchanges.
         String name = item.path("name").asText();
         if (!UNWANTED_HEADERS.contains(name.toLowerCase())) {
            Header header = headers.computeIfAbsent(name, k -> {
               Header h = new Header();
               h.setName(k);
               h.setValues(new HashSet<>());
               return h;
            });
            header.getValues().add(item.path("value").asText());
         }
      }
      return headers.values().stream().collect(Collectors.toSet());
   }

   /** Extract response content as string from HAR response content. */
   private String getContentText(JsonNode contentNode) {
      String text = contentNode.path("text").asText();
      if ("base64".equals(contentNode.path("encoding").asText())) {
         text = new String(Base64.getDecoder().decode(text));
      }
      return text;
   }

   /** Shorten an entry URL, removing protocol and host and API prefix if provided. */
   private String shortenURL(String url) {
      if (apiPrefix != null) {
         if (apiPrefix.startsWith("http://") || apiPrefix.startsWith("https://")) {
            // Prefix includes protocol and port, using it as is.
            return url.substring(apiPrefix.length() + 1);
         } else {
            // Start removing protocol and host and then the prefix.
            url = removeProtocolAndHostPort(url);
            return removeApiPrefix(url, apiPrefix);
         }
      }
      return removeProtocolAndHostPort(url);
   }

   private String extractGraphQLQuery(String requestContent) {
      try {
         JsonNode requestNode = jsonMapper.readTree(requestContent);
         return requestNode.path(GRAPHQL_QUERY_NODE).asText();
      } catch (Exception e) {
         log.error("Exception while extracting dispatch criteria from GraphQL variables: {}", e.getMessage(), e);
      }
      return "";
   }

   private String extractGraphQLCriteria(String dispatcherRules, String requestContent) {
      String dispatchCriteria = "";
      try {
         JsonNode requestNode = jsonMapper.readTree(requestContent);
         if (requestNode.has(GRAPHQL_VARIABLES_NODE)) {
            JsonNode variablesNode = requestNode.path(GRAPHQL_VARIABLES_NODE);
            Map<String, String> paramsMap = jsonMapper.convertValue(variablesNode,
                  TypeFactory.defaultInstance().constructMapType(TreeMap.class, String.class, String.class));
            dispatchCriteria = DispatchCriteriaHelper.extractFromParamMap(dispatcherRules, paramsMap);
         }
      } catch (Exception e) {
         log.error("Exception while extracting dispatch criteria from GraphQL variables: {}", e.getMessage(), e);
      }
      return dispatchCriteria;
   }

   private static String removeApiPrefix(String url, String apiPrefix) {
      if (url.startsWith(apiPrefix)) {
         return url.substring(apiPrefix.length());
      }
      return url;
   }

   private static String removeVerbFromURL(String operationName) {
      return operationName.substring(operationName.indexOf(" ") + 1);
   }

   private static String removeProtocolAndHostPort(String url) {
      if (url.startsWith("https://")) {
         url = url.substring(8);
      }
      if (url.startsWith("http://")) {
         url = url.substring(7);
      }
      // Remove host and port specification if any.
      if (url.indexOf('/') != -1) {
         url = url.substring(url.indexOf('/'));
      }
      return url;
   }
}
