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
package io.github.microcks.util.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.microcks.domain.*;
import io.github.microcks.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * @author laurent
 */
public class OpenAPIImporter implements MockRepositoryImporter {

   /**
    * A simple logger for diagnostic messages.
    */
   private static Logger log = LoggerFactory.getLogger(OpenAPIImporter.class);

   private boolean isYaml = true;
   private JsonNode spec;
   private String specContent;

   /**
    * Build a new importer.
    * @param specificationFilePath The path to local OpenAPI spec file
    * @throws IOException if project file cannot be found or read.
    */
   public OpenAPIImporter(String specificationFilePath) throws IOException {
      try {
         // Analyse first lines of file content to guess repository type.
         String line = null;
         BufferedReader reader = Files.newBufferedReader(new File(specificationFilePath).toPath(), Charset.forName("UTF-8"));
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Check is we start with json object or array definition.
            if (line.startsWith("{") || line.startsWith("[")) {
               isYaml = false;
               break;
            }
            if (line.startsWith("---")) {
               isYaml = true;
               break;
            }
         }
         reader.close();

         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(specificationFilePath));
         specContent = new String(bytes);
         // Convert them to Node using Jackson object mapper.
         ObjectMapper mapper = null;
         if (isYaml) {
            mapper = new ObjectMapper(new YAMLFactory());
         } else {
            mapper = new ObjectMapper();
         }
         spec = mapper.readTree(bytes);
      } catch (Exception e) {
         throw new IOException("OpenAPI spec file");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> result = new ArrayList<>();

      // Build a new service.
      Service service = new Service();

      service.setName(spec.path("info").path("title").asText());
      service.setVersion(spec.path("info").path("version").asText());
      service.setType(ServiceType.REST);

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
      if (isYaml) {
         name += ".yaml";
      } else {
         name += ".json";
      }

      // Build a brand new resource just with spec content.
      Resource resource = new Resource();
      resource.setName(name);
      resource.setType(ResourceType.OPEN_API_SPEC);
      resource.setContent(specContent);
      results.add(resource);

      return results;
   }

   @Override
   public Map<Request, Response> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException {
      Map<Request, Response> result = new HashMap<>();

      // Iterate on specification "paths" nodes.
      Iterator<Entry<String, JsonNode>> paths = spec.path("paths").fields();
      while (paths.hasNext()) {
         Entry<String, JsonNode> path = paths.next();
         String pathName = path.getKey();

         // Iterate on specification path, "verbs" nodes.
         Iterator<Entry<String, JsonNode>> verbs = path.getValue().fields();
         while (verbs.hasNext()) {
            Entry<String, JsonNode> verb = verbs.next();
            String verbName = verb.getKey();

            // Find the correct operation.
            if (operation.getName().equals(verbName.toUpperCase() + " " + pathName)) {
               // Find everything related to inputs for this operation examples.
               Map<String, Map<String, String>> pathParametersByExample = extractParametersByExample(path.getValue(), "path");
               Map<String, Map<String, String>> queryParametersByExample = extractParametersByExample(verb.getValue(), "query");
               Map<String, Map<String, String>> headerParametersByExample = extractParametersByExample(verb.getValue(), "header");
               Map<String, Request> requestBodiesByExample = extractRequestBodies(verb.getValue());

               // No need to go further if no examples.
               if (verb.getValue().has("responses")) {

                  Iterator<Entry<String, JsonNode>> responseCodes = verb.getValue().path("responses").fields();
                  while (responseCodes.hasNext()) {
                     Entry<String, JsonNode> responseCode = responseCodes.next();

                     // Find here potential headers for output of this operation examples.
                     Map<String, List<Header>> headersByExample = extractHeadersByExample(responseCode.getValue().path("headers"));

                     Iterator<Entry<String, JsonNode>> contents = responseCode.getValue().path("content").fields();
                     while (contents.hasNext()) {
                        Entry<String, JsonNode> content = contents.next();
                        String contentValue = content.getKey();

                        Iterator<String> exampleNames = content.getValue().path("examples").fieldNames();
                        while (exampleNames.hasNext()) {
                           String exampleName = exampleNames.next();
                           JsonNode example = content.getValue().path("examples").path(exampleName);

                           // We should have everything at hand to build response here.
                           Response response = new Response();
                           response.setName(exampleName);
                           response.setMediaType(contentValue);
                           response.setStatus(responseCode.getKey());
                           response.setContent(getExampleValue(example));
                           if (!responseCode.getKey().startsWith("2")) {
                              response.setFault(true);
                           }
                           List<Header> responseHeaders = headersByExample.get(exampleName);
                           if (responseHeaders != null) {
                              responseHeaders.stream().forEach(header -> response.addHeader(header));
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
                           Map<String, String> pathParameters = pathParametersByExample.get(exampleName);
                           if (pathParameters != null) {
                              for (Entry<String, String> paramEntry : pathParameters.entrySet()) {
                                 Parameter param = new Parameter();
                                 param.setName(paramEntry.getKey());
                                 param.setValue(paramEntry.getValue());
                                 request.addQueryParameter(param);
                              }
                           }
                           // Do we have to complete request with query parameters?
                           Map<String, String> queryParameters = queryParametersByExample.get(exampleName);
                           if (queryParameters != null) {
                              for (Entry<String, String> paramEntry : queryParameters.entrySet()) {
                                 Parameter param = new Parameter();
                                 param.setName(paramEntry.getKey());
                                 param.setValue(paramEntry.getValue());
                                 request.addQueryParameter(param);
                              }
                           }
                           // Do we have to complete request with header parameters?
                           Map<String, String> headerParameters = headerParametersByExample.get(exampleName);
                           if (headerParameters != null) {
                              for (Entry<String, String> headerEntry : headerParameters.entrySet()) {
                                 header = new Header();
                                 header.setName(headerEntry.getKey());
                                 // Values may be multiple and CSV.
                                 Set<String> headerValues = Arrays.stream(headerEntry.getValue().split(","))
                                       .map(value -> value.trim())
                                       .collect(Collectors.toSet());
                                 header.setValues(headerValues);
                                 request.addHeader(header);
                              }
                           }

                           // Finally, take care about dispatchCriteria and complete operation resourcePaths.
                           String dispatchCriteria = null;
                           String resourcePathPattern = operation.getName().split(" ")[1];

                           if (DispatchStyles.URI_PARAMS.equals(operation.getDispatcher())) {
                              Map<String, String> queryParams = queryParametersByExample.get(exampleName);
                              dispatchCriteria = DispatchCriteriaHelper
                                    .buildFromParamsMap(operation.getDispatcherRules(), queryParams);
                              // We should complete resourcePath here.
                              String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, queryParams);
                              operation.addResourcePath(resourcePath);
                           } else if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())) {
                              Map<String, String> parts = pathParametersByExample.get(exampleName);
                              dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(parts);
                              // We should complete resourcePath here.
                              String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, parts);
                              operation.addResourcePath(resourcePath);

                           } else if (DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
                              Map<String, String> parts = pathParametersByExample.get(exampleName);
                              Map<String, String> queryParams = queryParametersByExample.get(exampleName);
                              dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(parts);
                              dispatchCriteria += DispatchCriteriaHelper
                                    .buildFromParamsMap(operation.getDispatcherRules(), queryParams);
                              // We should complete resourcePath here.
                              String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, parts);
                              operation.addResourcePath(resourcePath);
                           }
                           response.setDispatchCriteria(dispatchCriteria);

                           result.put(request, response);
                        }
                     }
                  }
               }
            }
         }
      }
      return result;
   }

   /**
    * Extract the list of operations from Specification.
    */
   private List<Operation> extractOperations() throws MockRepositoryImportException {
      List<Operation> results = new ArrayList<>();

      // Iterate on specification "paths" nodes.
      Iterator<Entry<String, JsonNode>> paths = spec.path("paths").fields();
      while (paths.hasNext()) {
         Entry<String, JsonNode> path = paths.next();
         String pathName = path.getKey();

         // Iterate on specification path, "verbs" nodes.
         Iterator<Entry<String, JsonNode>> verbs = path.getValue().fields();
         while (verbs.hasNext()) {
            Entry<String, JsonNode> verb = verbs.next();
            String verbName = verb.getKey();

            // Only deal with real verbs for now.
            if (!"parameters".equals(verbName)) {
               String operationName = verbName.toUpperCase() + " " + pathName;

               Operation operation = new Operation();
               operation.setName(operationName);
               operation.setMethod(verbName.toUpperCase());

               // Deal with dispatcher stuffs.
               if (operationHasParameters(verb.getValue()) && urlHasParts(pathName)) {
                  operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(pathName)
                        + " ?? " + extractOperationParams(verb.getValue()));
                  operation.setDispatcher(DispatchStyles.URI_ELEMENTS);
               } else if (operationHasParameters(verb.getValue())) {
                  operation.setDispatcherRules(extractOperationParams(verb.getValue()));
                  operation.setDispatcher(DispatchStyles.URI_PARAMS);
               } else if (urlHasParts(pathName)) {
                  operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(pathName));
                  operation.setDispatcher(DispatchStyles.URI_PARTS);
               } else {
                  operation.addResourcePath(pathName);
               }

               results.add(operation);
            }
         }
      }
      return results;
   }

   /**
    * Extract parameters within a specification node and organize them by example. Parameter can be of type 'path',
    * 'query', 'header' or 'cookie'. Allow to filter them using parameterType. Key of returned map is example name.
    * Key of value map is param name. Value of value map is param value ;-)
    */
   private Map<String, Map<String, String>> extractParametersByExample(JsonNode node, String parameterType) {
      Map<String, Map<String, String>> results = new HashMap<>();

      Iterator<JsonNode> parameters = node.path("parameters").elements();
      while (parameters.hasNext()) {
         JsonNode parameter = parameters.next();
         String parameterName = parameter.path("name").asText();

         if (parameter.has("in") && parameter.path("in").asText().equals(parameterType)
               && parameter.has("examples")) {
            Iterator<String> exampleNames = parameter.path("examples").fieldNames();
            while (exampleNames.hasNext()) {
               String exampleName = exampleNames.next();
               JsonNode example = parameter.path("examples").path(exampleName);
               String exampleValue = getExampleValue(example);

               Map<String, String> exampleParams = results.get(exampleName);
               if (exampleParams == null) {
                  exampleParams = new HashMap<>();
                  results.put(exampleName, exampleParams);
               }
               exampleParams.put(parameterName, exampleValue);
            }
         }
      }
      return results;
   }

   /**
    * Extract request bodies within verb specification and organize them by example.
    * Key of returned map is example name. Value is basic Microcks Request object (no query params, no headers)
    */
   private Map<String, Request> extractRequestBodies(JsonNode verbNode) {
      Map<String, Request> results = new HashMap<>();

      JsonNode requestBody = verbNode.path("requestBody");
      Iterator<String> contentTypeNames = requestBody.path("content").fieldNames();
      while (contentTypeNames.hasNext()) {
         String contentTypeName = contentTypeNames.next();
         JsonNode contentType = requestBody.path("content").path(contentTypeName);

         if (contentType.has("examples")) {
            Iterator<String> exampleNames = contentType.path("examples").fieldNames();
            while (exampleNames.hasNext()) {
               String exampleName = exampleNames.next();
               JsonNode example = contentType.path("examples").path(exampleName);
               String exampleValue = getExampleValue(example);

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
    * Extract headers within a header specification node and organize them by example.
    * Key of returned map is example name. Value is a list of Microcks Header objects.
    */
   private Map<String, List<Header>> extractHeadersByExample(JsonNode headersNode) {
      Map<String, List<Header>> results = new HashMap<>();
      if (!headersNode.isMissingNode()) {
         Iterator<String> headerNames = headersNode.fieldNames();

         while (headerNames.hasNext()) {
            String headerName = headerNames.next();
            JsonNode headerNode = headersNode.path(headerName);

            if (headerNode.has("examples")) {
               Iterator<String> exampleNames = headerNode.path("examples").fieldNames();
               while (exampleNames.hasNext()) {
                  String exampleName = exampleNames.next();
                  JsonNode example = headerNode.path("examples").path(exampleName);
                  String exampleValue = getExampleValue(example);

                  // Example may be multiple CSV.
                  Set<String> values = Arrays.stream(
                        exampleValue.split(",")).map(value -> value.trim())
                        .collect(Collectors.toSet());

                  Header header = new Header();
                  header.setName(headerName);
                  header.setValues(values);

                  List<Header> headersForExample = results.get(exampleName);
                  if (headersForExample == null) {
                     headersForExample = new ArrayList<>();
                  }
                  headersForExample.add(header);
                  results.put(exampleName, headersForExample);
               }
            }
         }
      }
      return results;
   }

   /** Get the value of an example. This can be direct value field of those of followed $ref */
   private String getExampleValue(JsonNode example) {
      if (example.has("value")) {
         return example.path("value").asText();
      }
      if (example.has("$ref")) {
         // $ref: '#/components/examples/param_laurent'
         String ref = example.path("$ref").asText();
         JsonNode component = spec.at(ref.substring(1));
         return getExampleValue(component);
      }
      return null;
   }

   /** Check parameters presence into given operation node. */
   private static boolean operationHasParameters(JsonNode operation) {
      return operation.has("parameters");
   }

   /** Build a string representing operation parameters as used in dispatcher rules (param1 && param2)*/
   private static String extractOperationParams(JsonNode operation) {
      StringBuilder params = new StringBuilder();
      Iterator<JsonNode> parameters = operation.path("parameters").elements();
      while (parameters.hasNext()) {
         JsonNode parameter = parameters.next();
         if (params.length() > 0) {
            params.append(" && ");
         }
         params.append(parameter.path("name").asText());
      }
      return params.toString();
   }

   /** Check variables parts presence into given url. */
   private static boolean urlHasParts(String url) {
      return (url.indexOf("/:") != -1 || url.indexOf("/{") != -1);
   }
}
