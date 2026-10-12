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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.util.ObjectMapperFactory;
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.ai.McpSchema;
import io.github.microcks.util.ai.McpToolConverter;
import io.github.microcks.web.BasicHttpServletRequest;
import io.github.microcks.web.MockControllerCommons;
import io.github.microcks.web.MockInvocationContext;
import io.github.microcks.web.ResponseResult;
import io.github.microcks.web.RestInvocationProcessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.microcks.util.JsonSchemaValidator.JSON_SCHEMA_ADD_PROPERTIES_ELEMENT;
import static io.github.microcks.util.JsonSchemaValidator.JSON_SCHEMA_PROPERTIES_ELEMENT;
import static io.github.microcks.util.JsonSchemaValidator.JSON_SCHEMA_REQUIRED_ELEMENT;

/**
 * Implementation of McpToolConverter for OpenAPI services.
 * @author laurent
 */
public class OpenAPIMcpToolConverter extends McpToolConverter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenAPIMcpToolConverter.class);

   private static final String OPEN_API_PATHS_ELEMENT = "/paths/";

   private final RestInvocationProcessor invocationProcessor;
   private final ObjectMapper mapper;
   private final ResourceRepository resourceRepository;

   private JsonNode schemaNode;
   private final Map<String, JsonNode> externalResourcesContent = new HashMap<>();
   private ObjectMapper yamlMapper;

   /**
    * Build a new instance of OpenAPIMcpToolConverter.
    * @param service             The service to which this converter is attached
    * @param resource            The resource used for OpenAPI service conversion
    * @param invocationProcessor The invocation processor to use for processing the call
    * @param mapper              The ObjectMapper to use for JSON serialization
    */
   public OpenAPIMcpToolConverter(Service service, Resource resource, ResourceRepository resourceRepository,
         RestInvocationProcessor invocationProcessor, ObjectMapper mapper) {
      super(service, resource);
      this.resourceRepository = resourceRepository;
      this.invocationProcessor = invocationProcessor;
      this.mapper = mapper;
   }

   @Override
   public String getToolName(Operation operation) {
      // Anthropic Claude tools must respect ^[a-zA-Z0-9_-]{1,64}$ regular expression that doesn't match with our URI.
      return operation.getName().replace(" ", "_").replace("/", "_").replace("{", "").replace("}", "")
            .replace("__", "_").toLowerCase();
   }

   @Override
   public String getToolDescription(Operation operation) {
      try {
         if (schemaNode == null) {
            schemaNode = OpenAPISchemaValidator.getJsonNodeForSchema(resource.getContent());
         }

         // Extract JsonNode corresponding to operation.
         String verb = operation.getName().split(" ")[0].toLowerCase();
         String path = operation.getName().split(" ")[1].trim().replace("/", "~1");

         String operationPointer = OPEN_API_PATHS_ELEMENT + path + "/" + verb;
         JsonNode operationNode = schemaNode.at(operationPointer);
         if (operationNode.has("description")) {
            return operationNode.path("description").asText();
         }
         if (operationNode.has("summary")) {
            return operationNode.path("summary").asText();
         }
      } catch (Exception e) {
         log.error("Exception while trying to get tool description", e);
      }
      return null;
   }

   @Override
   public McpSchema.JsonSchema getInputSchema(Operation operation) {
      ObjectNode inputSchemaNode = mapper.createObjectNode();
      ObjectNode schemaPropertiesNode = mapper.createObjectNode();
      ArrayNode requiredPropertiesNode = mapper.createArrayNode();

      // Initialize input schema with empty object.
      inputSchemaNode.put("type", "object");
      inputSchemaNode.set(JSON_SCHEMA_PROPERTIES_ELEMENT, schemaPropertiesNode);
      inputSchemaNode.set(JSON_SCHEMA_REQUIRED_ELEMENT, requiredPropertiesNode);
      inputSchemaNode.put(JSON_SCHEMA_ADD_PROPERTIES_ELEMENT, false);
      try {
         if (schemaNode == null) {
            schemaNode = OpenAPISchemaValidator.getJsonNodeForSchema(resource.getContent());
         }

         // Extract JsonNode corresponding to request body.
         String verb = operation.getName().split(" ")[0].toLowerCase();
         String path = operation.getName().split(" ")[1].trim().replace("/", "~1");

         String schemaPointer = OPEN_API_PATHS_ELEMENT + path + "/" + verb
               + "/requestBody/content/application~1json/schema";
         JsonNode requestSchemaNode = schemaNode.at(schemaPointer);
         if (!requestSchemaNode.isMissingNode()) {
            requestSchemaNode = followRefIfAny(requestSchemaNode);

            // May be null if not resolved.
            if (requestSchemaNode != null) {
               visitNodeWithProperties(requestSchemaNode, schemaPropertiesNode, requiredPropertiesNode);
            }
         }

         // Add parameters (both path-level and operation-level) to input schema.
         Map<String, JsonNode> parameters = getParametersForOperation(path, verb);
         for (Map.Entry<String, JsonNode> entry : parameters.entrySet()) {
            String paramName = entry.getKey();
            JsonNode parameter = entry.getValue();

            // Create a property node for the parameter.
            ObjectNode propertyNode = buildParameterPropertyNode(parameter);
            schemaPropertiesNode.set(paramName, propertyNode);

            boolean isRequired = parameter.path(JSON_SCHEMA_REQUIRED_ELEMENT).asBoolean(false)
                  || "path".equalsIgnoreCase(parameter.path("in").asText());
            if (isRequired && !arrayNodeContains(requiredPropertiesNode, paramName)) {
               requiredPropertiesNode.add(paramName);
            }
         }
      } catch (Exception e) {
         log.error("Exception while trying to get input schema", e);
      }
      return mapper.convertValue(inputSchemaNode, McpSchema.JsonSchema.class);
   }

   @Override
   public Response getCallResponse(Operation operation, McpSchema.CallToolRequest request,
         Map<String, List<String>> headers) {
      String queryString = "";
      String verb = operation.getName().split(" ")[0];
      String resourcePath = operation.getName().split(" ")[1].trim();
      Map<String, String> pathParams = new HashMap<>();
      Map<String, String> queryParams = new HashMap<>();

      // Copy arguments to a mutable map to avoid mutating input or failing on unmodifiable map.
      Map<String, Object> remainingArguments = request != null && request.arguments() != null
            ? new HashMap<>(request.arguments())
            : new HashMap<>();

      // Unwrap the request parameters and headers and remove them from request.
      try {
         if (schemaNode == null) {
            schemaNode = OpenAPISchemaValidator.getJsonNodeForSchema(resource.getContent());
         }

         // Extract JsonNode corresponding to request parameters (both path-level and operation-level).
         String path = resourcePath.replace("/", "~1");
         Map<String, JsonNode> parameters = getParametersForOperation(path, verb.toLowerCase());

         for (JsonNode parameter : parameters.values()) {
            String paramName = parameter.path("name").asText();
            String paramIn = parameter.path("in").asText(); // Check if it's "path" or "query" or "header"

            if (remainingArguments.containsKey(paramName)) {
               Object argVal = remainingArguments.remove(paramName);
               if (argVal != null) {
                  String paramValue = argVal.toString();
                  if ("path".equals(paramIn)) {
                     pathParams.put(paramName, paramValue);
                  } else if ("query".equals(paramIn)) {
                     queryParams.put(paramName, paramValue);
                  } else if ("header".equals(paramIn)) {
                     headers.put(paramName, List.of(paramValue));
                  }
               }
            }
         }
      } catch (Exception e) {
         log.error("Exception while extracting URI parameters from arguments", e);
      }

      // Re-build the resource path with parameters if needed.
      if (operation.getName().contains("{")) {
         resourcePath = URIBuilder.buildURIFromPattern(resourcePath, pathParams);
      }
      // Re-build the query string with parameters if needed.
      if (!queryParams.isEmpty()) {
         queryString = URIBuilder.buildURIFromPattern("", queryParams);
         if (queryString.startsWith("?")) {
            queryString = queryString.substring(1);
         }
      }

      // Create a mock request to pass to the invocation processor.
      MockInvocationContext ic = new MockInvocationContext(service, operation, resourcePath);

      try {
         // Serialize remaining arguments as the request body.
         String body = mapper.writeValueAsString(remainingArguments);

         // Execute the invocation processor after having cleaned the headers to propagate.
         headers = sanitizeHttpHeaders(headers);
         ResponseResult result = invocationProcessor.processInvocation(ic, System.currentTimeMillis(), null, body,
               headers,
               new BasicHttpServletRequest(
                     "http://localhost:8080/rest/"
                           + MockControllerCommons.composeServiceAndVersion(service.getName(), service.getVersion()),
                     verb, resourcePath, queryString, queryParams, headers));

         // Build a Microcks Response from the result.
         Response response = new Response();
         response.setStatus(result.status().toString());
         response.setHeaders(null);
         response.setContent(extractResponseContent(result));

         if (result.status().isError()) {
            response.setFault(true);
         }
         return response;
      } catch (Exception e) {
         log.error("Exception while processing the MCP call invocation", e);
      }
      return null;
   }

   /** Follow the $ref if we have one. Otherwise, return given node. */
   protected JsonNode followRefIfAny(JsonNode referencableNode) {
      if (referencableNode.has("$ref")) {
         String ref = referencableNode.path("$ref").asText();
         return getNodeForRef(ref);
      }
      return referencableNode;
   }

   /** Get the JsonNode for reference within the specification. */
   private JsonNode getNodeForRef(String reference) {
      if (reference.startsWith("#/")) {
         return schemaNode.at(reference.substring(1));
      }
      return getNodeForExternalRef(reference);
   }

   /** Hold the parts of an external reference after parsing. */
   private record ExternalRefParts(String resourceName, String pointerInFile) {
   }

   /** Get the JsonNode for reference that is located in external resource. */
   private JsonNode getNodeForExternalRef(String externalReference) {
      ExternalRefParts refParts = parseExternalReference(externalReference);

      if (resourceRepository != null) {
         Resource attachedResource = findMatchingResource(refParts.resourceName());
         if (attachedResource != null) {
            JsonNode resourceNode = parseAndCacheResource(attachedResource, externalReference);
            if (resourceNode != null) {
               if (refParts.pointerInFile() != null) {
                  return resourceNode.at(refParts.pointerInFile().substring(1));
               }
               return resourceNode;
            }
         }
      }

      log.warn("Found no resource for reference '{}'", externalReference);
      return null;
   }

   /**
    * Parse an external reference into the resource name and optional JSON pointer. For example,
    * "models.yaml#/components/schemas/TestModel" becomes ExternalRefParts["models.yaml",
    * "#/components/schemas/TestModel"].
    */
   private ExternalRefParts parseExternalReference(String externalReference) {
      if (externalReference.contains("#/")) {
         String resourceName = externalReference.substring(0, externalReference.indexOf("#/"));
         String pointerInFile = externalReference.substring(externalReference.indexOf("#/"));
         return new ExternalRefParts(resourceName, pointerInFile);
      }
      return new ExternalRefParts(externalReference, null);
   }

   /**
    * Find a matching resource from the repository for the given resource name. Skips the main resource and matches by
    * path or encoded name.
    */
   private Resource findMatchingResource(String resourceName) {
      List<Resource> attachedResources = resourceRepository.findByServiceIdAndType(service.getId(),
            ResourceType.OPEN_API_SPEC);
      for (Resource attachedResource : attachedResources) {
         // Skip the main resource.
         if (attachedResource.getId() != null && attachedResource.getId().equals(resource.getId())) {
            continue;
         }
         // Path direct equality is for absolute ref ("http://raw.githubusercontent.com/...").
         // Path equality with resource name is for relative refs that have been re-normalized
         // ("Service name+Service version+...").
         if (resourceName.equals(attachedResource.getPath())
               || resourceName.equals(URLEncoder.encode(attachedResource.getName(), StandardCharsets.UTF_8))) {
            return attachedResource;
         }
      }
      return null;
   }

   /**
    * Parse and cache the content of a resource (JSON or YAML detection). Uses the resource ID as the cache key for
    * stable lookup.
    */
   private JsonNode parseAndCacheResource(Resource resource, String externalReference) {
      return externalResourcesContent.computeIfAbsent(resource.getId(), k -> {
         try {
            // We have to guess if content is JSON or YAML.
            if (resource.getContent().startsWith("{") || resource.getContent().startsWith("[")) {
               return mapper.readTree(resource.getContent());
            } else {
               // yamlMapper is lazily initialized.
               if (yamlMapper == null) {
                  yamlMapper = ObjectMapperFactory.getYamlObjectMapper();
               }
               return yamlMapper.readTree(resource.getContent());
            }
         } catch (JsonProcessingException e) {
            log.error("Get a JSON processing exception on {}", externalReference, e);
            return null;
         }
      });
   }

   /** Visit a node and extract its properties. */
   private void visitNodeWithProperties(JsonNode node, ObjectNode propertiesNode, ArrayNode requiredPropertiesNode) {
      JsonNode requiredNode = node.path(JSON_SCHEMA_REQUIRED_ELEMENT);
      Iterator<Map.Entry<String, JsonNode>> propertiesList = node.path(JSON_SCHEMA_PROPERTIES_ELEMENT).fields();

      while (propertiesList.hasNext()) {
         Map.Entry<String, JsonNode> property = propertiesList.next();
         String propertyName = property.getKey();
         JsonNode propertyValue = followRefIfAny(property.getValue());

         if (propertyValue.has(JSON_SCHEMA_PROPERTIES_ELEMENT)) {
            // Initialize a new subschema node we must visit to resolve all possible references.
            ObjectNode subschemaNode = mapper.createObjectNode();
            ObjectNode subpropertiesNode = mapper.createObjectNode();
            ArrayNode requiredSubpropertiesNode = mapper.createArrayNode();

            subschemaNode.put("type", "object");
            subschemaNode.set(JSON_SCHEMA_PROPERTIES_ELEMENT, subpropertiesNode);
            subschemaNode.set(JSON_SCHEMA_REQUIRED_ELEMENT, requiredSubpropertiesNode);
            subschemaNode.put(JSON_SCHEMA_ADD_PROPERTIES_ELEMENT, false);
            propertiesNode.set(propertyName, subschemaNode);

            visitNodeWithProperties(propertyValue, subpropertiesNode, requiredSubpropertiesNode);
         } else {
            propertiesNode.set(propertyName, dereferencedNode(propertyValue));
            if (!requiredNode.isMissingNode() && requiredNode.isArray()
                  && arrayNodeContains((ArrayNode) requiredNode, property.getKey())) {
               requiredPropertiesNode.add(property.getKey());
            }
         }
      }
   }

   private JsonNode dereferencedNode(JsonNode node) {
      if (node.isObject()) {
         Iterator<String> fieldNames = node.fieldNames();
         while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode fieldValue = node.get(fieldName);
            if (fieldValue.has("$ref")) {
               JsonNode target = followRefIfAny(fieldValue);
               if (target != null) {
                  // Replace the field value with the dereferenced node.
                  ((ObjectNode) node).replace(fieldName, dereferencedNode(target));
               } else {
                  // If the target is null, remove the field.
                  ((ObjectNode) node).remove(fieldName);
               }
            } else if (fieldValue.isObject() || fieldValue.isArray()) {
               // Recursively process nested objects or arrays.
               dereferencedNode(fieldValue);
            }
         }
      } else if (node.isArray()) {
         for (int i = 0; i < node.size(); i++) {
            JsonNode arrayElement = node.get(i);
            if (arrayElement.has("$ref")) {
               JsonNode target = followRefIfAny(arrayElement);
               if (target != null) {
                  JsonNode dereferencedTarget = dereferencedNode(target);
                  // Replace the array element with the dereferenced node.
                  ((ArrayNode) node).set(i, dereferencedTarget);
               } else {
                  // If the target is null, remove the array element.
                  ((ArrayNode) node).remove(i);
               }
            } else if (arrayElement.isObject() || arrayElement.isArray()) {
               // Recursively process nested objects or arrays.
               dereferencedNode(arrayElement);
            }
         }
      }
      return node;
   }

   /** Check if the arrayNode contains the given value. */
   private boolean arrayNodeContains(ArrayNode arrayNode, String value) {
      // Iterate over arrayNode elements and check if any element matches the value.
      for (JsonNode element : arrayNode) {
         if (element.asText().equals(value)) {
            return true;
         }
      }
      return false;
   }

   /**
    * Extract all parameters applicable to an operation, combining path-level parameters and operation-level parameters.
    * Operation-level parameters override path-level parameters with the same name.
    */
   private Map<String, JsonNode> getParametersForOperation(String path, String verb) {
      Map<String, JsonNode> parametersByName = new LinkedHashMap<>();

      // 1. Path-level parameters first (can be overridden by operation-level parameters).
      String pathParamsPointer = OPEN_API_PATHS_ELEMENT + path + "/parameters";
      JsonNode pathParamsNode = schemaNode.at(pathParamsPointer);
      if (pathParamsNode.isArray()) {
         for (JsonNode paramNode : pathParamsNode) {
            JsonNode parameter = followRefIfAny(paramNode);
            if (parameter != null && parameter.has("name")) {
               parametersByName.put(parameter.path("name").asText(), parameter);
            }
         }
      }

      // 2. Operation-level parameters (override path-level parameters with the same name).
      String opParamsPointer = OPEN_API_PATHS_ELEMENT + path + "/" + verb.toLowerCase() + "/parameters";
      JsonNode opParamsNode = schemaNode.at(opParamsPointer);
      if (opParamsNode.isArray()) {
         for (JsonNode paramNode : opParamsNode) {
            JsonNode parameter = followRefIfAny(paramNode);
            if (parameter != null && parameter.has("name")) {
               parametersByName.put(parameter.path("name").asText(), parameter);
            }
         }
      }

      return parametersByName;
   }

   /** Build a JsonSchema property node for an OpenAPI parameter. */
   private ObjectNode buildParameterPropertyNode(JsonNode parameter) {
      ObjectNode propertyNode = mapper.createObjectNode();

      // Check if parameter has an OpenAPI schema.
      JsonNode paramSchema = parameter.path("schema");
      if (!paramSchema.isMissingNode()) {
         paramSchema = followRefIfAny(paramSchema);
      }

      if (paramSchema != null && !paramSchema.isMissingNode()) {
         if (paramSchema.has("type")) {
            propertyNode.put("type", paramSchema.path("type").asText());
         } else {
            propertyNode.put("type", "string");
         }
         if (paramSchema.has("format")) {
            propertyNode.put("format", paramSchema.path("format").asText());
         }
         if (paramSchema.has("description")) {
            propertyNode.put("description", paramSchema.path("description").asText());
         }
         if (paramSchema.has("enum")) {
            propertyNode.set("enum", paramSchema.path("enum"));
         }
         if (paramSchema.has("default")) {
            propertyNode.set("default", paramSchema.path("default"));
         }
         if (paramSchema.has("items")) {
            propertyNode.set("items", followRefIfAny(paramSchema.path("items")));
         }
      } else if (parameter.has("type")) {
         // OpenAPI 2.0 / Swagger style where type is directly on parameter.
         propertyNode.put("type", parameter.path("type").asText());
         if (parameter.has("format")) {
            propertyNode.put("format", parameter.path("format").asText());
         }
         if (parameter.has("enum")) {
            propertyNode.set("enum", parameter.path("enum"));
         }
         if (parameter.has("default")) {
            propertyNode.set("default", parameter.path("default"));
         }
         if (parameter.has("items")) {
            propertyNode.set("items", followRefIfAny(parameter.path("items")));
         }
      } else {
         propertyNode.put("type", "string");
      }

      // If parameter has description at parameter level, prefer it over schema description.
      if (parameter.has("description")) {
         propertyNode.put("description", parameter.path("description").asText());
      }

      return propertyNode;
   }
}
