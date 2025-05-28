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
import io.github.microcks.util.URIBuilder;
import io.github.microcks.util.ai.McpSchema;
import io.github.microcks.util.ai.McpToolConverter;
import io.github.microcks.web.BasicHttpServletRequest;
import io.github.microcks.web.MockControllerCommons;
import io.github.microcks.web.MockInvocationContext;
import io.github.microcks.web.ResponseResult;
import io.github.microcks.web.RestInvocationProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
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

   private JsonNode schemaNode;

   /**
    * Build a new instance of OpenAPIMcpToolConverter.
    * @param service             The service to which this converter is attached
    * @param resource            The resource used for OpenAPI service conversion
    * @param invocationProcessor The invocation processor to use for processing the call
    * @param mapper              The ObjectMapper to use for JSON serialization
    */
   public OpenAPIMcpToolConverter(Service service, Resource resource, RestInvocationProcessor invocationProcessor,
         ObjectMapper mapper) {
      super(service, resource);
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

         // Add parameters to input schema.
         String paramsPointer = OPEN_API_PATHS_ELEMENT + path + "/" + verb + "/parameters";
         JsonNode paramsNode = schemaNode.at(paramsPointer);

         Iterator<JsonNode> parameters = paramsNode.elements();
         while (parameters.hasNext()) {
            JsonNode parameter = followRefIfAny(parameters.next());
            String paramName = parameter.path("name").asText();

            // Create a property node for the parameter.
            ObjectNode propertyNode = mapper.createObjectNode();
            propertyNode.put("type", "string");
            schemaPropertiesNode.set(paramName, propertyNode);
            if (parameter.path(JSON_SCHEMA_REQUIRED_ELEMENT).asBoolean(false)) {
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

      // Unwrap the request parameters and headers and remove them from request.
      try {
         if (schemaNode == null) {
            schemaNode = OpenAPISchemaValidator.getJsonNodeForSchema(resource.getContent());
         }

         // Extract JsonNode corresponding to request parameters.
         String path = resourcePath.replace("/", "~1");
         String paramsPointer = OPEN_API_PATHS_ELEMENT + path + "/" + verb.toLowerCase() + "/parameters";
         JsonNode paramsNode = schemaNode.at(paramsPointer);

         Iterator<JsonNode> parameters = paramsNode.elements();
         while (parameters.hasNext()) {
            JsonNode parameter = followRefIfAny(parameters.next());
            String paramName = parameter.path("name").asText();
            String paramIn = parameter.path("in").asText(); // Check if it's "path" or "query"

            if (request.arguments().containsKey(paramName)) {
               String paramValue = request.arguments().remove(paramName).toString();
               if ("path".equals(paramIn)) {
                  pathParams.put(paramName, paramValue);
               } else if ("query".equals(paramIn)) {
                  queryParams.put(paramName, paramValue);
               } else if ("header".equals(paramIn)) {
                  headers.put(paramName, List.of(paramValue));
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
         String body = mapper.writeValueAsString(request.arguments());

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
      // TODO: handle external references reusing imported resources?
      return null;
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
}
