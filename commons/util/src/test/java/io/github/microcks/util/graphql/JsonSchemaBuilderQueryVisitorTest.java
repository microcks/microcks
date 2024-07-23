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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.TypeName;
import graphql.schema.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JsonSchemaBuilderQueryVisitorTest {

   private ObjectNode jsonSchemaData;
   private JsonSchemaBuilderQueryVisitor visitor;

   @BeforeEach
   public void setUp() {
      ObjectMapper mapper = new ObjectMapper();
      jsonSchemaData = mapper.createObjectNode();
      jsonSchemaData.putObject(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_PROPERTIES);
      visitor = new JsonSchemaBuilderQueryVisitor(jsonSchemaData);
   }

   @Test
   public void testVisitFieldWithScalarType() {
      QueryVisitorFieldEnvironment environment = mock(QueryVisitorFieldEnvironment.class);
      GraphQLOutputType outputType = mock(GraphQLOutputType.class);
      TypeName definitionType = TypeName.newTypeName().name("String").build();

      when(environment.getFieldDefinition()).thenReturn(mock(GraphQLFieldDefinition.class));
      when(environment.getFieldDefinition().getDefinition()).thenReturn(mock(FieldDefinition.class));
      when(environment.getFieldDefinition().getType()).thenReturn(outputType);
      when(environment.getFieldDefinition().getDefinition().getType()).thenReturn(definitionType);
      when(environment.getFieldDefinition().getName()).thenReturn("scalarField");

      visitor.visitField(environment);

      JsonNode fieldNode = jsonSchemaData.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_PROPERTIES).get("scalarField");
      assertEquals("string", fieldNode.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_TYPE).asText());
   }

   @Test
   public void testVisitFieldWithObjectType() {
      QueryVisitorFieldEnvironment environment = mock(QueryVisitorFieldEnvironment.class);
      GraphQLOutputType outputType = mock(GraphQLObjectType.class);
      TypeName definitionType = TypeName.newTypeName().name("Object").build();

      when(environment.getFieldDefinition()).thenReturn(mock(GraphQLFieldDefinition.class));
      when(environment.getFieldDefinition().getDefinition()).thenReturn(mock(FieldDefinition.class));
      when(environment.getFieldDefinition().getType()).thenReturn(outputType);
      when(environment.getFieldDefinition().getDefinition().getType()).thenReturn(definitionType);
      when(environment.getFieldDefinition().getName()).thenReturn("objectField");

      visitor.visitField(environment);

      JsonNode fieldNode = jsonSchemaData.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_PROPERTIES).get("objectField");
      assertEquals("object", fieldNode.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_TYPE).asText());
      assertFalse(fieldNode.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_ADDITIONAL_PROPERTIES).asBoolean());
   }

   @Test
   public void testVisitFieldWithEnumType() {
      QueryVisitorFieldEnvironment environment = mock(QueryVisitorFieldEnvironment.class);
      GraphQLEnumType enumType = mock(GraphQLEnumType.class);
      TypeName definitionType = TypeName.newTypeName().name("EnumType").build();

      when(environment.getFieldDefinition()).thenReturn(mock(GraphQLFieldDefinition.class));
      when(environment.getFieldDefinition().getDefinition()).thenReturn(mock(FieldDefinition.class));
      when(environment.getFieldDefinition().getType()).thenReturn(enumType);
      when(environment.getFieldDefinition().getDefinition().getType()).thenReturn(definitionType);
      when(environment.getFieldDefinition().getName()).thenReturn("enumField");

      EnumValueDefinition valueDef1 = EnumValueDefinition.newEnumValueDefinition().name("VALUE1").build();
      EnumValueDefinition valueDef2 = EnumValueDefinition.newEnumValueDefinition().name("VALUE2").build();

      GraphQLEnumValueDefinition enumValueDef1 = new GraphQLEnumValueDefinition.Builder().name("VALUE1")
            .value("Description").build();
      GraphQLEnumValueDefinition enumValueDef2 = new GraphQLEnumValueDefinition.Builder().name("VALUE2")
            .value("Description").build();

      when(enumType.getValues()).thenReturn(List.of(enumValueDef1, enumValueDef2));

      visitor.visitField(environment);

      JsonNode fieldNode = jsonSchemaData.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_PROPERTIES).get("enumField");
      assertEquals("string", fieldNode.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_TYPE).asText());
      assertEquals(2, fieldNode.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_ENUM).size());
      assertEquals("VALUE1", fieldNode.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_ENUM).get(0).asText());
      assertEquals("VALUE2", fieldNode.get(JsonSchemaBuilderQueryVisitor.JSON_SCHEMA_ENUM).get(1).asText());
   }
}
