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

import io.github.microcks.util.JsonSchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.analysis.QueryTraverser;
import graphql.analysis.QueryVisitor;
import graphql.language.Document;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.util.HashMap;
import java.util.List;

import static io.github.microcks.util.JsonSchemaValidator.*;

/**
 * Helper class for validating Json objects against their GraphQL schema. Supported version of GraphQL schema is
 * https://spec.graphql.org/October2021/.
 * @author laurent
 */
public class GraphQLSchemaValidator {

   /** Name of 'data' element in GraphQL response. */
   public static final String GRAPHQL_RESPONSE_DATA = "data";

   /** Have a static mapper to avoid initialization cost. */
   private static final ObjectMapper mapper = new ObjectMapper();

   /** Private constructor for utility class. */
   private GraphQLSchemaValidator() {
   }

   /**
    * Build a JSON Schema that should apply to a GraphQL response giving the API GraphQL SDL and the query
    * specification. Query specification allows to get structure and mandatory fields from selection ; Schema allows to
    * get type definition (scalar, arrays, objects).
    * @param schemaText The text representation of a GraphQL Schema
    * @param query      The text representation of a GraphQL query
    * @return The Jackson JsonNode representing the Json schema for response
    */
   public static JsonNode buildResponseJsonSchema(String schemaText, String query) {
      TypeDefinitionRegistry registry = new SchemaParser().parse(schemaText);
      GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(registry, RuntimeWiring.MOCKED_WIRING);

      Document graphqlRequest = new Parser().parseDocument(query);
      QueryTraverser queryTraversal = QueryTraverser.newQueryTraverser().schema(schema).document(graphqlRequest)
            .variables(new HashMap<>()).build();

      ObjectNode jsonSchema = initResponseJsonSchema();
      QueryVisitor visitor = new JsonSchemaBuilderQueryVisitor(
            (ObjectNode) jsonSchema.get(JSON_SCHEMA_PROPERTIES_ELEMENT).get(GRAPHQL_RESPONSE_DATA));

      queryTraversal.visitPreOrder(visitor);
      return jsonSchema;
   }


   /**
    * Commodity method: just a shortcut to JsonSchemaValidator.validateJson(schemaNode, jsonNode)
    * @param schemaNode The Json schema specification as a Jackson node
    * @param jsonNode   The Json object as a Jackson node
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode) {
      return JsonSchemaValidator.validateJson(schemaNode, jsonNode);
   }

   /** Initialize the schema structure of a GraphQL Json response. */
   private static ObjectNode initResponseJsonSchema() {
      ObjectNode jsonSchema = mapper.createObjectNode();

      jsonSchema.put(JsonSchemaValidator.JSON_SCHEMA_IDENTIFIER_ELEMENT,
            JsonSchemaValidator.JSON_V12_SCHEMA_IDENTIFIER);
      jsonSchema.put(JSON_SCHEMA_TYPE_ELEMENT, "object");
      jsonSchema.put(JSON_SCHEMA_ADD_PROPERTIES_ELEMENT, false);
      jsonSchema.putArray(JSON_SCHEMA_REQUIRED_ELEMENT).add(GRAPHQL_RESPONSE_DATA);

      ObjectNode properties = jsonSchema.putObject(JSON_SCHEMA_PROPERTIES_ELEMENT);
      ObjectNode data = properties.putObject(GRAPHQL_RESPONSE_DATA);
      data.put(JSON_SCHEMA_TYPE_ELEMENT, "object");
      data.putObject(JSON_SCHEMA_PROPERTIES_ELEMENT);

      return jsonSchema;
   }
}
