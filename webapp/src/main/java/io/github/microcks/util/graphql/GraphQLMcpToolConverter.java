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
package io.github.microcks.util.graphql;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.util.ai.McpSchema;
import io.github.microcks.util.ai.McpToolConverter;
import io.github.microcks.web.BasicHttpServletRequest;
import io.github.microcks.web.GraphQLInvocationProcessor;
import io.github.microcks.web.MockControllerCommons;
import io.github.microcks.web.MockInvocationContext;
import io.github.microcks.web.ResponseResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.parser.Parser;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeInfo;
import graphql.schema.idl.TypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.github.microcks.util.JsonSchemaValidator.JSON_SCHEMA_ADD_PROPERTIES_ELEMENT;
import static io.github.microcks.util.JsonSchemaValidator.JSON_SCHEMA_ITEMS_ELEMENT;
import static io.github.microcks.util.JsonSchemaValidator.JSON_SCHEMA_PROPERTIES_ELEMENT;
import static io.github.microcks.util.JsonSchemaValidator.JSON_SCHEMA_REQUIRED_ELEMENT;
import static io.github.microcks.util.graphql.GraphQLImporter.VALID_OPERATION_TYPES;
import static io.github.microcks.util.graphql.JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_ENUM;

/**
 * Implementation of McpToolConverter for GraphQL services.
 * @author laurent
 */
public class GraphQLMcpToolConverter extends McpToolConverter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(GraphQLMcpToolConverter.class);

   private final GraphQLInvocationProcessor invocationProcessor;
   private final ObjectMapper mapper;

   private Document graphqlDocument;

   /**
    * Build a new instance of GraphQLMcpToolConverter.
    * @param service             The service to which this converter is attached
    * @param resource            The resource used for GraphQL service conversion
    * @param invocationProcessor The invocation processor to use for processing the call
    * @param mapper              The ObjectMapper to use for JSON serialization
    */
   public GraphQLMcpToolConverter(Service service, Resource resource, GraphQLInvocationProcessor invocationProcessor,
         ObjectMapper mapper) {
      super(service, resource);
      this.invocationProcessor = invocationProcessor;
      this.mapper = mapper;
   }

   @Override
   public String getToolDescription(Operation operation) {
      try {
         if (graphqlDocument == null) {
            graphqlDocument = new Parser().parseDocument(resource.getContent());
         }
         FieldDefinition operationDefinition = getOperationDefinition(operation.getName());

         if (operationDefinition != null) {
            // #1 Look for a description in the operation definition.
            if (operationDefinition.getDescription() != null) {
               return operationDefinition.getDescription().content;
            } else if (operationDefinition.getComments() != null && !operationDefinition.getComments().isEmpty()) {
               // #2 Look for comments in the operation definition.
               StringBuilder result = new StringBuilder();
               operationDefinition.getComments().forEach(comment -> { result.append(comment.getContent()); });
               return result.toString().trim();
            }
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
         if (graphqlDocument == null) {
            graphqlDocument = new Parser().parseDocument(resource.getContent());
         }
         FieldDefinition operationDefinition = getOperationDefinition(operation.getName());

         if (operationDefinition != null && operationDefinition.getInputValueDefinitions() != null
               && !operationDefinition.getInputValueDefinitions().isEmpty()) {
            for (InputValueDefinition inputValueDef : operationDefinition.getInputValueDefinitions()) {
               visitProperty(inputValueDef.getName(), inputValueDef.getType(), schemaPropertiesNode,
                     requiredPropertiesNode);
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
      // Build a mock invocation context needed for the invocation processor.
      MockInvocationContext ic = new MockInvocationContext(service, operation, null);

      try {
         String query = null;
         if (operation.getDispatcher() != null && !operation.getDispatcher().startsWith("PROXY")) {
            // We target Microcks so query can be simple due to permissive parsing.
            query = operation.getName();
         } else {
            // We target a real GraphQL service so query, we must build a full and correct query.
            query = buildCompleteGraphQLQuery(operation, request).replace("\"", "\\\"");
         }

         // De-serialize remaining arguments as the body variables.
         String body = "{\"variables\": " + mapper.writeValueAsString(request.arguments()) + ", \"query\": \"" + query
               + "\" }";

         ObjectNode variables = mapper.convertValue(request.arguments(), ObjectNode.class);
         GraphQLHttpRequest graphqlRequest = GraphQLHttpRequest.from(operation.getName(), variables);

         // Query parameters are actually the body but simplified to a map of string values.
         Map<String, String> queryParams = request.arguments().entrySet().stream()
               .map(entry -> Map.entry(entry.getKey(), entry.getValue().toString()))
               .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

         // Execute the invocation processor after having cleaned the headers to propagate.
         headers = sanitizeHttpHeaders(headers);
         ResponseResult result = invocationProcessor.processInvocation(ic, System.currentTimeMillis(), queryParams,
               body, graphqlRequest, headers,
               new BasicHttpServletRequest(
                     "http://localhost:8080/graphql/"
                           + MockControllerCommons.composeServiceAndVersion(service.getName(), service.getVersion()),
                     "POST", "", "", Map.of(), headers));

         // Build a Microcks Response from the result.
         Response response = new Response();
         response.setStatus(result.status().toString());
         response.setHeaders(null);

         String resultContent = extractResponseContent(result);

         // As we're no longer tied to the GraphQL semantics, we can get rid of the data/<operationName> wrapper.
         JsonNode responseNode = mapper.readTree(resultContent);
         if (responseNode.has("data") && responseNode.get("data").has(operation.getName())) {
            response.setContent(mapper.writeValueAsString(responseNode.get("data").get(operation.getName())));
         } else {
            // Default to the full response.
            response.setContent(resultContent);
         }

         if (result.status().isError()) {
            response.setFault(true);
         }
         return response;
      } catch (Exception e) {
         log.error("Exception while processing the MCP call invocation", e);
      }
      return null;
   }

   /** Retrieve the correct operation definition in GraphQL schema document. */
   private FieldDefinition getOperationDefinition(String operationName) {
      for (ObjectTypeDefinition typeDefinition : graphqlDocument.getDefinitionsOfType(ObjectTypeDefinition.class)) {
         if (VALID_OPERATION_TYPES.contains(typeDefinition.getName().toLowerCase())) {
            for (FieldDefinition fieldDef : typeDefinition.getFieldDefinitions()) {
               if (fieldDef.getName().equals(operationName)) {
                  return fieldDef;
               }
            }
         }
      }
      return null;
   }

   /** Check if the type is a scalar type. */
   private boolean isScalarType(String typeName) {
      return ScalarInfo.isGraphqlSpecifiedScalar(typeName)
            || graphqlDocument.getDefinitionsOfType(ScalarTypeDefinition.class).stream()
                  .anyMatch(scalarTypeDefinition -> scalarTypeDefinition.getName().equals(typeName));
   }

   /** Retrieve the correct type definition in GraphQL schema document. */
   private ObjectTypeDefinition getTypeDefinition(String typeName) {
      for (ObjectTypeDefinition typeDefinition : graphqlDocument.getDefinitionsOfType(ObjectTypeDefinition.class)) {
         if (typeDefinition.getName().equals(typeName)) {
            return typeDefinition;
         }
      }
      return null;
   }

   /** Retrieve the correct enum type definition in GraphQL schema document. */
   private EnumTypeDefinition getEnumTypeDefinition(String typeName) {
      for (EnumTypeDefinition enumTypeDefinition : graphqlDocument.getDefinitionsOfType(EnumTypeDefinition.class)) {
         if (enumTypeDefinition.getName().equals(typeName)) {
            return enumTypeDefinition;
         }
      }
      return null;
   }

   /** Retrieve the correct input value definition in GraphQL schema document. */
   private InputObjectTypeDefinition getInputValueDefinition(String typeName) {
      for (InputObjectTypeDefinition inputObjectTypeDefinition : graphqlDocument
            .getDefinitionsOfType(InputObjectTypeDefinition.class)) {
         if (inputObjectTypeDefinition.getName().equals(typeName)) {
            return inputObjectTypeDefinition;
         }
      }
      return null;
   }

   /** Visit a property and add it to the input schema elements (properties and required properties). */
   private void visitProperty(String propertyName, Type propertyType, ObjectNode propertiesNode,
         ArrayNode requiredPropertiesNode) {
      if (TypeUtil.isNonNull(propertyType)) {
         requiredPropertiesNode.add(propertyName);
         propertyType = TypeUtil.unwrapOne(propertyType);
      }

      TypeInfo propertyTypeInfo = TypeInfo.typeInfo(propertyType);

      // Check if the field is a scalar.
      if (isScalarType(propertyTypeInfo.getName())) {
         ObjectNode propertySchemaNode = mapper.createObjectNode();
         propertySchemaNode.put("type", "string");
         if (propertyName != null) {
            propertiesNode.set(propertyName, propertySchemaNode);
         } else {
            // We may have no name if inside an array items.
            propertiesNode.setAll(propertySchemaNode);
         }

      } else if (TypeUtil.isList(propertyType)) {
         // We must convert to a type array.
         ObjectNode arraySchemaNode = mapper.createObjectNode();
         ObjectNode subitemsNode = mapper.createObjectNode();

         arraySchemaNode.put("type", "array");
         arraySchemaNode.set(JSON_SCHEMA_ITEMS_ELEMENT, subitemsNode);
         propertiesNode.set(propertyName, arraySchemaNode);

         visitProperty(null, TypeUtil.unwrapAll(propertyType), subitemsNode, requiredPropertiesNode);
      } else {
         // We must check if it's an enum type.
         EnumTypeDefinition enumTypeDefinition = getEnumTypeDefinition(propertyTypeInfo.getName());
         if (enumTypeDefinition != null) {
            ObjectNode enumSchemaNode = mapper.createObjectNode();
            enumSchemaNode.put("type", "string");
            ArrayNode enumValuesNode = mapper.createArrayNode();
            for (EnumValueDefinition valueDef : enumTypeDefinition.getEnumValueDefinitions()) {
               enumValuesNode.add(valueDef.getName());
            }
            enumSchemaNode.set(JSON_SCHEMA_ENUM, enumValuesNode);
            if (propertyName != null) {
               propertiesNode.set(propertyName, enumSchemaNode);
            } else {
               // We may have no name if inside an array items.
               propertiesNode.setAll(enumSchemaNode);
            }
         } else {
            // So finally, it should be an object type.
            // Initialize a new subschema node we must visit to resolve all possible references.
            ObjectNode subschemaNode = mapper.createObjectNode();
            ObjectNode subpropertiesNode = mapper.createObjectNode();
            ArrayNode requiredSubpropertiesNode = mapper.createArrayNode();

            subschemaNode.put("type", "object");
            subschemaNode.set(JSON_SCHEMA_PROPERTIES_ELEMENT, subpropertiesNode);
            subschemaNode.set(JSON_SCHEMA_REQUIRED_ELEMENT, requiredSubpropertiesNode);
            subschemaNode.put(JSON_SCHEMA_ADD_PROPERTIES_ELEMENT, false);
            if (propertyName != null) {
               propertiesNode.set(propertyName, subschemaNode);
            } else {
               // We may have no name if inside an array items.
               propertiesNode.setAll(subschemaNode);
            }

            ObjectTypeDefinition typeDef = getTypeDefinition(propertyTypeInfo.getName());
            if (typeDef != null) {
               visitObjectTypeDefinition(typeDef, subpropertiesNode, requiredSubpropertiesNode);
            } else {
               // It could be an input type definition.
               InputObjectTypeDefinition inputTypeDef = getInputValueDefinition(propertyTypeInfo.getName());
               if (inputTypeDef != null) {
                  visitInputObjectTypeDefinition(inputTypeDef, subpropertiesNode, requiredSubpropertiesNode);
               } else {
                  log.warn("Cannot find type definition for {}", propertyTypeInfo.getName());
               }
            }
         }
      }
   }

   /** Visit a GraphQL object type definition and add its properties to the input schema. */
   private void visitObjectTypeDefinition(ObjectTypeDefinition typeDefinition, ObjectNode propertiesNode,
         ArrayNode requiredPropertiesNode) {
      for (FieldDefinition fieldDef : typeDefinition.getFieldDefinitions()) {
         visitProperty(fieldDef.getName(), fieldDef.getType(), propertiesNode, requiredPropertiesNode);
      }
   }

   /** Visit a GraphQL input object type definition and add its properties to the input schema. */
   private void visitInputObjectTypeDefinition(InputObjectTypeDefinition typeDefinition, ObjectNode propertiesNode,
         ArrayNode requiredPropertiesNode) {
      for (InputValueDefinition fieldDef : typeDefinition.getInputValueDefinitions()) {
         visitProperty(fieldDef.getName(), fieldDef.getType(), propertiesNode, requiredPropertiesNode);
      }
   }

   /** Build a complete a query with all the fields to fetch. */
   private String buildCompleteGraphQLQuery(Operation operation, McpSchema.CallToolRequest request) {
      // Extract the type information from the operation.
      if (graphqlDocument == null) {
         graphqlDocument = new Parser().parseDocument(resource.getContent());
      }
      FieldDefinition operationDefinition = getOperationDefinition(operation.getName());
      Type operationOutputType = operationDefinition.getType();
      TypeInfo operationOutputTypeInfo = TypeInfo.typeInfo(operationOutputType);
      ObjectTypeDefinition typeDef = getTypeDefinition(operationOutputTypeInfo.getName());

      StringBuilder queryBuilder = new StringBuilder();
      queryBuilder.append("query {");
      queryBuilder.append("  ").append(operation.getName()).append("(");

      // Append the arguments to the operation.
      queryBuilder.append(request.arguments().entrySet().stream()
            .map(entry -> entry.getKey() + ": \"" + entry.getValue() + "\"").collect(Collectors.joining(", ", "", "")));

      queryBuilder.append("){\\n");

      if (typeDef != null) {
         for (FieldDefinition fd : typeDef.getFieldDefinitions()) {
            TypeInfo fdTypeInfo = TypeInfo.typeInfo(fd.getType());
            if (isScalarType(fdTypeInfo.getName())) {
               queryBuilder.append(fd.getName()).append("\\n");
            } else {
               // We must check if it's an enum type.
               EnumTypeDefinition enumTypeDefinition = getEnumTypeDefinition(fdTypeInfo.getName());
               if (enumTypeDefinition != null) {
                  queryBuilder.append(fd.getName()).append("\\n");
               }
            }
         }
      }

      // Finalize query before returning it.
      queryBuilder.append("}}");
      return queryBuilder.toString();
   }
}
