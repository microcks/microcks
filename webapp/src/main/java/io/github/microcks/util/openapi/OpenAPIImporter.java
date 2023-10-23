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

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Metadata;
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
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.ObjectMapperFactory;
import io.github.microcks.util.ReferenceResolver;
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.metadata.MetadataExtensions;
import io.github.microcks.util.metadata.MetadataExtractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An implementation of MockRepositoryImporter that deals with OpenAPI v3.x.x specification
 * file ; whether encoding into JSON or YAML documents.
 * @author laurent
 */
public class OpenAPIImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(OpenAPIImporter.class);

   private boolean isYaml = true;
   private JsonNode spec;
   private String specContent;
   private ReferenceResolver referenceResolver;

   private static final List<String> VALID_VERBS = Arrays.asList("get", "put", "post", "delete", "options", "head", "patch", "trace");

   private static final String CONTENT_NODE = "content";
   private static final String EXAMPLES_NODE = "examples";
   private static final String EXAMPLE_VALUE_NODE = "value";

   /**
    * Build a new importer.
    * @param specificationFilePath The path to local OpenAPI spec file
    * @param referenceResolver An optional resolver for references present into the OpenAPI file
    * @throws IOException if project file cannot be found or read.
    */
   public OpenAPIImporter(String specificationFilePath, ReferenceResolver referenceResolver) throws IOException {
      this.referenceResolver = referenceResolver;
      BufferedReader reader = null;
      try {
         // Analyse first lines of file content to guess repository type.
         String line = null;
         reader = Files.newBufferedReader(new File(specificationFilePath).toPath(), StandardCharsets.UTF_8);
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Check is we start with json object or array definition.
            if (line.startsWith("{") || line.startsWith("[")) {
               isYaml = false;
               break;
            }
            else if (line.startsWith("---") || line.startsWith("-") || line.startsWith("openapi: ")) {
               isYaml = true;
               break;
            }
         }

         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(specificationFilePath));
         specContent = new String(bytes, StandardCharsets.UTF_8);
         // Convert them to Node using Jackson object mapper.
         ObjectMapper mapper = isYaml ? ObjectMapperFactory.getYamlObjectMapper() : ObjectMapperFactory.getJsonObjectMapper();
         spec = mapper.readTree(bytes);
      } catch (Exception e) {
         log.error("Exception while parsing OpenAPI specification file " + specificationFilePath, e);
         throw new IOException("OpenAPI spec file parsing error");
      } finally {
         if (reader != null) {
            reader.close();
         }
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

      // Complete metadata if specified via extension.
      if (spec.path("info").has(MetadataExtensions.MICROCKS_EXTENSION)) {
         Metadata metadata = new Metadata();
         MetadataExtractor.completeMetadata(metadata, spec.path("info").path(MetadataExtensions.MICROCKS_EXTENSION));
         service.setMetadata(metadata);
      }

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
      results.add(resource);

      // Browse all document references for external JSON schemas
      // only if we have a resolver available.
      if (referenceResolver != null) {

         Set<String> references = new HashSet<>();
         findAllExternalRefs(spec, references);

         for (String ref : references) {
            try {
               // Extract content using resolver.
               String content = referenceResolver.getHttpReferenceContent(ref, "UTF-8");
               String resourceName = ref.substring(ref.lastIndexOf('/') + 1);

               // Build a new resource from content. Use the escaped operation path.
               Resource schemaResource = new Resource();
               schemaResource.setName(IdBuilder.buildResourceFullName(service, resourceName));
               schemaResource.setPath(ref);
               schemaResource.setContent(content);
               schemaResource.setType(ResourceType.JSON_SCHEMA);

               if (!ref.startsWith("http")) {
                  // If a relative resource, replace with new name.
                  specContent = specContent.replace(ref, URLEncoder.encode(schemaResource.getName(), "UTF-8"));
               }

               results.add(schemaResource);
            } catch (IOException ioe) {
               log.error("IOException while trying to resolve reference " + ref, ioe);
               log.info("Ignoring the reference {} cause it could not be resolved", ref);
            }
         }
         // Finally try to clean up resolved references and associated resources (files)
         referenceResolver.cleanResolvedReferences();
      }
      // Set the content of main OpenAPI that may have been updated with dereferenced dependencies.
      resource.setContent(specContent);

      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException {
      Map<Request, Response> result = new HashMap<>();

      // Iterate on specification "paths" nodes.
      Iterator<Entry<String, JsonNode>> paths = spec.path("paths").fields();
      while (paths.hasNext()) {
         Entry<String, JsonNode> path = paths.next();
         String pathName = path.getKey();

         // Find examples fragments defined at the path level.
         Map<String, Map<String, String>> pathPathParametersByExample = extractParametersByExample(path.getValue(), "path");

         // Iterate on specification path, "verbs" nodes.
         Iterator<Entry<String, JsonNode>> verbs = path.getValue().fields();
         while (verbs.hasNext()) {
            Entry<String, JsonNode> verb = verbs.next();
            String verbName = verb.getKey();

            // Find the correct operation.
            if (operation.getName().equals(verbName.toUpperCase() + " " + pathName.trim())) {
               // Find examples fragments defined at the verb level.
               Map<String, Map<String, String>> pathParametersByExample = extractParametersByExample(verb.getValue(), "path");
               pathParametersByExample.putAll(pathPathParametersByExample);
               Map<String, Map<String, String>> queryParametersByExample = extractParametersByExample(verb.getValue(), "query");
               Map<String, Map<String, String>> headerParametersByExample = extractParametersByExample(verb.getValue(), "header");
               Map<String, Request> requestBodiesByExample = extractRequestBodies(verb.getValue());

               // No need to go further if no examples.
               if (verb.getValue().has("responses")) {

                  // If we previously override the dispatcher with a Fallback, we must be sure to get wrapped elements.
                  String rootDispatcher = operation.getDispatcher();
                  String rootDispatcherRules = operation.getDispatcherRules();

                  if (DispatchStyles.FALLBACK.equals(operation.getDispatcher())) {
                     FallbackSpecification fallbackSpec = null;
                     try {
                        fallbackSpec = FallbackSpecification.buildFromJsonString(operation.getDispatcherRules());
                        rootDispatcher = fallbackSpec.getDispatcher();
                        rootDispatcherRules = fallbackSpec.getDispatcherRules();
                     } catch (JsonMappingException e) {
                        log.warn("Operation '{}' has a malformed Fallback dispatcher rules", operation.getName());
                     }
                  }

                  Iterator<Entry<String, JsonNode>> responseCodes = verb.getValue().path("responses").fields();
                  while (responseCodes.hasNext()) {
                     Entry<String, JsonNode> responseCode = responseCodes.next();
                     // Find here potential headers for output of this operation examples.
                     Map<String, List<Header>> headersByExample = extractHeadersByExample(responseCode.getValue());

                     Iterator<Entry<String, JsonNode>> contents = getResponseContent(responseCode.getValue()).fields();
                     while (contents.hasNext()) {
                        Entry<String, JsonNode> content = contents.next();
                        String contentValue = content.getKey();

                        Iterator<String> exampleNames = content.getValue().path(EXAMPLES_NODE).fieldNames();
                        while (exampleNames.hasNext()) {
                           String exampleName = exampleNames.next();
                           JsonNode example = content.getValue().path(EXAMPLES_NODE).path(exampleName);

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
                           Map<String, String> pathParameters = pathParametersByExample.get(exampleName);
                           if (pathParameters != null) {
                              for (Entry<String, String> paramEntry : pathParameters.entrySet()) {
                                 Parameter param = new Parameter();
                                 param.setName(paramEntry.getKey());
                                 param.setValue(paramEntry.getValue());
                                 request.addQueryParameter(param);
                              }
                           } else if (DispatchStyles.URI_PARTS.equals(operation.getDispatcher())
                                 || DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
                              // We've must have at least one path parameters but none...
                              // Do not register this request / response pair.
                              break;
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
                                       .map(String::trim)
                                       .collect(Collectors.toSet());
                                 header.setValues(headerValues);
                                 request.addHeader(header);
                              }
                           }

                           // Finally, take care about dispatchCriteria and complete operation resourcePaths.
                           String dispatchCriteria = null;
                           String resourcePathPattern = operation.getName().split(" ")[1];

                           if (DispatchStyles.URI_PARAMS.equals(rootDispatcher)) {
                              Map<String, String> queryParams = queryParametersByExample.get(exampleName);
                              dispatchCriteria = DispatchCriteriaHelper
                                    .buildFromParamsMap(rootDispatcherRules, queryParams);
                              // We only need the pattern here.
                              operation.addResourcePath(resourcePathPattern);
                           } else if (DispatchStyles.URI_PARTS.equals(rootDispatcher)) {
                              Map<String, String> parts = pathParametersByExample.get(exampleName);
                              dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parts);
                              // We should complete resourcePath here.
                              String resourcePath = URIBuilder.buildURIFromPattern(resourcePathPattern, parts);
                              operation.addResourcePath(resourcePath);
                           } else if (DispatchStyles.URI_ELEMENTS.equals(rootDispatcher)) {
                              Map<String, String> parts = pathParametersByExample.get(exampleName);
                              Map<String, String> queryParams = queryParametersByExample.get(exampleName);
                              dispatchCriteria = DispatchCriteriaHelper.buildFromPartsMap(rootDispatcherRules, parts);
                              dispatchCriteria += DispatchCriteriaHelper
                                    .buildFromParamsMap(rootDispatcherRules, queryParams);
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

      // Adapt map to list of Exchanges.
      return result.entrySet().stream()
            .map(entry -> new RequestResponsePair(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
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

               // Deal with dispatcher stuffs if needed.
               if (operation.getDispatcher() == null) {
                  if (operationHasParameters(verb.getValue(), "query") && urlHasParts(pathName)) {
                     operation.setDispatcherRules(DispatchCriteriaHelper.extractPartsFromURIPattern(pathName)
                           + " ?? " + extractOperationParams(verb.getValue()));
                     operation.setDispatcher(DispatchStyles.URI_ELEMENTS);
                  } else if (operationHasParameters(verb.getValue(), "query")) {
                     operation.setDispatcherRules(extractOperationParams(verb.getValue()));
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

               results.add(operation);
            }
         }
      }
      return results;
   }

   /**
    * Browser Json node to extract references and store them into externalRefs.
    */
   private void findAllExternalRefs(JsonNode node, Set<String> externalRefs) {
      // If node as a $ref child, it's a stop condition.
      if (node.has("$ref")) {
         String ref = node.path("$ref").asText();
         if (!ref.startsWith("#")) {
            externalRefs.add(ref);
         }
      } else {
         // Iterate on all other children.
         Iterator<JsonNode> children = node.elements();
         while (children.hasNext()) {
            findAllExternalRefs(children.next(), externalRefs);
         }
      }
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
         JsonNode parameter = followRefIfAny(parameters.next());
         String parameterName = parameter.path("name").asText();

         if (parameter.has("in") && parameter.path("in").asText().equals(parameterType)
               && parameter.has(EXAMPLES_NODE)) {
            Iterator<String> exampleNames = parameter.path(EXAMPLES_NODE).fieldNames();
            while (exampleNames.hasNext()) {
               String exampleName = exampleNames.next();
               JsonNode example = parameter.path(EXAMPLES_NODE).path(exampleName);
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
      Iterator<String> contentTypeNames = requestBody.path(CONTENT_NODE).fieldNames();
      while (contentTypeNames.hasNext()) {
         String contentTypeName = contentTypeNames.next();
         JsonNode contentType = requestBody.path(CONTENT_NODE).path(contentTypeName);

         if (contentType.has(EXAMPLES_NODE)) {
            Iterator<String> exampleNames = contentType.path(EXAMPLES_NODE).fieldNames();
            while (exampleNames.hasNext()) {
               String exampleName = exampleNames.next();
               JsonNode example = contentType.path(EXAMPLES_NODE).path(exampleName);
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
   private Map<String, List<Header>> extractHeadersByExample(JsonNode responseNode) {
      Map<String, List<Header>> results = new HashMap<>();

      if (responseNode.has("headers")) {
         JsonNode headersNode = responseNode.path("headers");
         Iterator<String> headerNames = headersNode.fieldNames();

         while (headerNames.hasNext()) {
            String headerName = headerNames.next();
            JsonNode headerNode = headersNode.path(headerName);

            if (headerNode.has(EXAMPLES_NODE)) {
               Iterator<String> exampleNames = headerNode.path(EXAMPLES_NODE).fieldNames();
               while (exampleNames.hasNext()) {
                  String exampleName = exampleNames.next();
                  JsonNode example = headerNode.path(EXAMPLES_NODE).path(exampleName);
                  String exampleValue = getExampleValue(example);

                  // Example may be multiple CSV.
                  Set<String> values = Arrays.stream(exampleValue.split(","))
                        .map(String::trim)
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
      if (responseNode.has("$ref")) {
         JsonNode component = followRefIfAny(responseNode);
         return extractHeadersByExample(component);
      }
      return results;
   }

   /** Get the value of an example. This can be direct value field or those of followed $ref */
   private String getExampleValue(JsonNode example) {
      if (example.has(EXAMPLE_VALUE_NODE)) {
         if (example.path(EXAMPLE_VALUE_NODE).getNodeType() == JsonNodeType.ARRAY ||
               example.path(EXAMPLE_VALUE_NODE).getNodeType() == JsonNodeType.OBJECT ) {
            return example.path(EXAMPLE_VALUE_NODE).toString();
         }
         return example.path(EXAMPLE_VALUE_NODE).asText();
      }
      if (example.has("$ref")) {
         JsonNode component = followRefIfAny(example);
         return getExampleValue(component);
      }
      return null;
   }

   /** Get the content of a response. This can be direct content field or those of followed $ref */
   private JsonNode getResponseContent(JsonNode response) {
      if (response.has("$ref")) {
         JsonNode component = followRefIfAny(response);
         return getResponseContent(component);
      }
      return response.path(CONTENT_NODE);
   }

   /** Build a string representing operation parameters as used in dispatcher rules (param1 && param2)*/
   private String extractOperationParams(JsonNode operation) {
      StringBuilder params = new StringBuilder();
      Iterator<JsonNode> parameters = operation.path("parameters").elements();
      while (parameters.hasNext()) {
         JsonNode parameter = followRefIfAny(parameters.next());

         String parameterIn = parameter.path("in").asText();
         if (!"path".equals(parameterIn)) {
            if (params.length() > 0) {
               params.append(" && ");
            }
            params.append(parameter.path("name").asText());
         }
      }
      return params.toString();
   }

   /** Check parameters presence into given operation node. */
   private boolean operationHasParameters(JsonNode operation, String parameterType) {
      if (!operation.has("parameters")) {
         return false;
      }
      Iterator<JsonNode> parameters = operation.path("parameters").elements();
      while (parameters.hasNext()) {
         JsonNode parameter = followRefIfAny(parameters.next());

         String parameterIn = parameter.path("in").asText();
         if (parameterIn.equals(parameterType)) {
            return true;
         }
      }
      return false;
   }

   /** Follow the $ref if we have one. Otherwise return given node. */
   private JsonNode followRefIfAny(JsonNode referencableNode) {
      if (referencableNode.has("$ref")) {
         String ref = referencableNode.path("$ref").asText();
         return getNodeForRef(ref);
      }
      return referencableNode;
   }

   /** */
   private JsonNode getNodeForRef(String reference) {
      return spec.at(reference.substring(1));
   }

   /** Check variables parts presence into given url. */
   private static boolean urlHasParts(String url) {
      return (url.indexOf("/:") != -1 || url.indexOf("/{") != -1);
   }
}
