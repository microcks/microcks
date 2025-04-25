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
import io.github.microcks.util.DispatchStyles;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Implementation of McpToolConverter for OpenAPI services.
 * @author laurent
 */
public class OpenAPIMcpToolConverter extends McpToolConverter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenAPIMcpToolConverter.class);

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
      return operation.getName().replace(" ", "_").replace("/", "_").replace("{", "").replace("}", "");
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

         String operationPointer = "/paths/" + path + "/" + verb;
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
      Map<String, Object> properties = new HashMap<>();
      List<String> requiredProperties = new ArrayList<>();

      try {
         if (schemaNode == null) {
            schemaNode = OpenAPISchemaValidator.getJsonNodeForSchema(resource.getContent());
         }

         // Extract JsonNode corresponding to request body.
         String verb = operation.getName().split(" ")[0].toLowerCase();
         String path = operation.getName().split(" ")[1].trim().replace("/", "~1");

         String schemaPointer = "/paths/" + path + "/" + verb + "/requestBody/content/application~1json/schema";
         JsonNode requestSchemaNode = schemaNode.at(schemaPointer);
         if (!requestSchemaNode.isMissingNode()) {
            // TODO: we must follow ref here...
            if (requestSchemaNode.has("$ref")) {
               String ref = requestSchemaNode.path("$ref").asText();
               if (ref.startsWith("#/")) {
                  requestSchemaNode = schemaNode.at(ref.substring(1));
               }
            }

            // Recopy all simple and required properties to MCP input schema.
            JsonNode requiredNode = requestSchemaNode.path("required");
            Iterator<Map.Entry<String, JsonNode>> propertiesList = requestSchemaNode.path("properties").fields();
            while (propertiesList.hasNext()) {
               Map.Entry<String, JsonNode> property = propertiesList.next();
               properties.put(property.getKey(), property.getValue());
               if (!requiredNode.isMissingNode() && requiredNode.isArray() && requiredNode.has(property.getKey())) {
                  requiredProperties.add(property.getKey());
               }
            }
         }

         // Add parameters to input schema.
         String paramsPointer = "/paths/" + path + "/" + verb + "/parameters";
         JsonNode paramsNode = schemaNode.at(paramsPointer);

         Iterator<JsonNode> parameters = paramsNode.elements();
         while (parameters.hasNext()) {
            // TODO: we must follow ref here...
            JsonNode parameter = parameters.next();
            String paramName = parameter.path("name").asText();
            properties.put(paramName, Map.of("type", "string"));
            if (parameter.path("required").asBoolean(false)) {
               requiredProperties.add(paramName);
            }
         }
      } catch (Exception e) {
         log.error("Exception while trying to get input schema", e);
      }
      return new McpSchema.JsonSchema("object", properties, requiredProperties, false);
   }

   @Override
   public Response getCallResponse(Operation operation, McpSchema.CallToolRequest request) {
      String queryString = "";
      String verb = operation.getName().split(" ")[0];
      String resourcePath = operation.getName().split(" ")[1].trim();
      Map<String, String> uriParams = new HashMap<>();

      // Unwrap the request parameters and remove them from request.
      try {
         if (schemaNode == null) {
            schemaNode = OpenAPISchemaValidator.getJsonNodeForSchema(resource.getContent());
         }

         // Extract JsonNode corresponding to request parameters.
         String path = resourcePath.replace("/", "~1");

         String paramsPointer = "/paths/" + path + "/" + verb.toLowerCase() + "/parameters";
         JsonNode paramsNode = schemaNode.at(paramsPointer);

         Iterator<JsonNode> parameters = paramsNode.elements();
         while (parameters.hasNext()) {
            // TODO: we must follow ref here...
            JsonNode parameter = parameters.next();
            String paramName = parameter.path("name").asText();
            if (request.arguments().containsKey(paramName)) {
               uriParams.put(paramName, request.arguments().remove(paramName).toString());
            }
         }
      } catch (Exception e) {
         log.error("Exception while extracting URI parameters from arguments", e);
      }

      // Re-build the resource path with parameters if needed.
      if (operation.getName().contains("{")) {
         resourcePath = URIBuilder.buildURIFromPattern(resourcePath, uriParams);
      }
      // Re-build the query string with parameters if needed.
      if (DispatchStyles.URI_PARAMS.equals(operation.getDispatcher())
            || DispatchStyles.URI_ELEMENTS.equals(operation.getDispatcher())) {
         queryString = URIBuilder.buildURIFromPattern("", uriParams);
         if (queryString.startsWith("?")) {
            queryString = queryString.substring(1);
         }
      }

      MockInvocationContext ic = new MockInvocationContext(service, operation, resourcePath);

      try {
         // Serialize remaining arguments as the request body.
         String body = mapper.writeValueAsString(request.arguments());

         // Create a mock request to pass to the invocation processor.
         ResponseResult result = invocationProcessor.processInvocation(ic, System.currentTimeMillis(), null, body,
               new HttpHeaders(),
               new BasicHttpServletRequest(
                     "http://localhost:8080/rest/"
                           + MockControllerCommons.composeServiceAndVersion(service.getName(), service.getVersion()),
                     verb, resourcePath, queryString, uriParams));

         // Build a Microcks Response from the result.
         Response response = new Response();
         response.setStatus(result.status().toString());
         response.setHeaders(null);
         response.setContent(new String(result.content(), StandardCharsets.UTF_8));
         if (result.status().isError()) {
            response.setFault(true);
         }
         return response;
      } catch (Exception e) {
         log.error("Exception while processing the MCP call invocation", e);
      }
      return null;
   }
}
