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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.analysis.QueryVisitor;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.analysis.QueryVisitorFragmentSpreadEnvironment;
import graphql.analysis.QueryVisitorInlineFragmentEnvironment;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.GraphQLEnumType;
import graphql.schema.GraphQLEnumValueDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.idl.ScalarInfo;
import graphql.schema.idl.TypeInfo;
import graphql.schema.idl.TypeUtil;

/**
 * This is an implementation of GraphQL-Java QueryVisitor that takes care of building a Json Schema that could be
 * applied to the response of a GraphQL query.
 * @author laurent
 */
public class JsonSchemaBuilderQueryVisitor implements QueryVisitor {

   public static final String JSON_SCHEMA_TYPE = "type";
   public static final String JSON_SCHEMA_ENUM = "enum";
   public static final String JSON_SCHEMA_ITEMS = "items";
   public static final String JSON_SCHEMA_OBJECT_TYPE = "object";
   public static final String JSON_SCHEMA_ARRAY_TYPE = "array";
   public static final String JSON_SCHEMA_STRING_TYPE = "string";

   public static final String JSON_SCHEMA_REQUIRED = "required";
   public static final String JSON_SCHEMA_PROPERTIES = "properties";
   public static final String JSON_SCHEMA_ADDITIONAL_PROPERTIES = "additionalProperties";

   private ObjectNode parentNode;
   private ObjectNode currentNode;

   /**
    * Build a new JsonSchemaBuilderQueryVisitor.
    * @param jsonSchemaData The Json Schema to complete. This node must be the /properties/data path of schema object.
    */
   public JsonSchemaBuilderQueryVisitor(ObjectNode jsonSchemaData) {
      this.parentNode = jsonSchemaData;
      this.currentNode = (ObjectNode) jsonSchemaData.path(JSON_SCHEMA_PROPERTIES);
   }

   @Override
   public void visitField(QueryVisitorFieldEnvironment queryVisitorFieldEnvironment) {

      // Each new property should put as required and we should not allow additional properties.
      ArrayNode required = getRequiredArrayNode();
      required.add(queryVisitorFieldEnvironment.getFieldDefinition().getName());

      // Even if type is marked as optional in the GraphQL Schema, it must be present and
      // serialized as null into the Json response. We have to unwrap it first.
      GraphQLOutputType outputType = queryVisitorFieldEnvironment.getFieldDefinition().getType();
      Type definitionType = queryVisitorFieldEnvironment.getFieldDefinition().getDefinition().getType();
      if (TypeUtil.isNonNull(definitionType)) {
         definitionType = TypeUtil.unwrapOne(definitionType);
      }

      // Add this field to current node.
      ObjectNode fieldNode = currentNode.putObject(queryVisitorFieldEnvironment.getFieldDefinition().getName());

      TypeInfo definitionTypeInfo = TypeInfo.typeInfo(definitionType);

      // Treat most common case first: we've got a scalar property.
      if (ScalarInfo.isGraphqlSpecifiedScalar(definitionTypeInfo.getName())) {
         fieldNode.put(JSON_SCHEMA_TYPE, getJsonScalarType(definitionTypeInfo.getName()));
      } else if (outputType instanceof GraphQLObjectType) {
         // Then we deal with objects.
         fieldNode.put(JSON_SCHEMA_TYPE, JSON_SCHEMA_OBJECT_TYPE);
         ObjectNode properties = fieldNode.putObject(JSON_SCHEMA_PROPERTIES);
         parentNode.put(JSON_SCHEMA_ADDITIONAL_PROPERTIES, false);
         fieldNode.put(JSON_SCHEMA_ADDITIONAL_PROPERTIES, false);
         parentNode = fieldNode;
         currentNode = properties;
      } else if (TypeUtil.isList(definitionType)) {
         // Then we deal with lists.
         fieldNode.put(JSON_SCHEMA_TYPE, JSON_SCHEMA_ARRAY_TYPE);
         ObjectNode items = fieldNode.putObject(JSON_SCHEMA_ITEMS);

         // Depending on item type, we should initialize an object structure.
         TypeName itemTypeInfo = TypeUtil.unwrapAll(definitionType);
         if (!ScalarInfo.isGraphqlSpecifiedScalar(itemTypeInfo.getName())) {
            items.put(JSON_SCHEMA_TYPE, JSON_SCHEMA_OBJECT_TYPE);
            ObjectNode properties = items.putObject(JSON_SCHEMA_PROPERTIES);
            items.put(JSON_SCHEMA_ADDITIONAL_PROPERTIES, false);
            parentNode = items;
            currentNode = properties;
         }
      } else if (outputType instanceof GraphQLEnumType enumType) {
         // Then we deal with enumerations.
         fieldNode.put(JSON_SCHEMA_TYPE, JSON_SCHEMA_STRING_TYPE);
         ArrayNode enumNode = fieldNode.putArray(JSON_SCHEMA_ENUM);

         for (GraphQLEnumValueDefinition valDef : enumType.getValues()) {
            enumNode.add(valDef.getName());
         }
      }
   }

   @Override
   public void visitInlineFragment(QueryVisitorInlineFragmentEnvironment queryVisitorInlineFragmentEnvironment) {
      // Nothing to do when visiting a fragment.
   }

   @Override
   public void visitFragmentSpread(QueryVisitorFragmentSpreadEnvironment queryVisitorFragmentSpreadEnvironment) {
      // Nothing to do when visiting a spread.
   }

   private ArrayNode getRequiredArrayNode() {
      JsonNode required = parentNode.get(JSON_SCHEMA_REQUIRED);
      if (required == null) {
         required = parentNode.putArray(JSON_SCHEMA_REQUIRED);
      }
      return (ArrayNode) required;
   }

   private String getJsonScalarType(String graphqlScalarType) {
      switch (graphqlScalarType) {
         case "Int":
            return "integer";
         case "Float":
            return "number";
         case "ID":
            return JSON_SCHEMA_STRING_TYPE;
         default:
            return graphqlScalarType.toLowerCase();
      }
   }
}
