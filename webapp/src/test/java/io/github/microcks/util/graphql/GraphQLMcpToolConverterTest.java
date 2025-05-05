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
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.RepositoryTestsConfiguration;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.service.ArtifactInfo;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.ai.McpSchema;
import io.github.microcks.web.ControllerTestsConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test class for GraphQLMcpToolConverter.
 * @author laurent
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringJUnitConfig(classes = { RepositoryTestsConfiguration.class, ControllerTestsConfiguration.class })
@TestPropertySource(locations = { "classpath:/config/test.properties" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GraphQLMcpToolConverterTest {

   @Autowired
   private ServiceService serviceService;

   @Autowired
   private ResourceRepository resourceRepository;

   private Service service;
   private GraphQLMcpToolConverter toolConverter;

   @BeforeAll
   void setUp() throws Exception {
      // Import the petstore service definition from the GraphQL tutorial.
      File artifactFile = new File("target/test-classes/io/github/microcks/util/graphql/petstore-1.0.graphql");
      // Extract service and resource. + tool converters.
      List<Service> services = serviceService.importServiceDefinition(artifactFile, null,
            new ArtifactInfo("petstore-1.0.graphql", true));
      service = services.getFirst();
      List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(),
            ResourceType.GRAPHQL_SCHEMA);
      toolConverter = new GraphQLMcpToolConverter(service, resources.getFirst(), new ObjectMapper());
   }

   @Test
   void testGetToolName() {
      for (Operation operation : service.getOperations()) {
         String toolName = toolConverter.getToolName(operation);
         if ("allPets".equals(operation.getName())) {
            assertEquals("allPets", toolName);
         } else if ("searchPets".equals(operation.getName())) {
            assertEquals("searchPets", toolName);
         } else if ("advancedSearchPets".equals(operation.getName())) {
            assertEquals("advancedSearchPets", toolName);
         } else if ("createPet".equals(operation.getName())) {
            assertEquals("createPet", toolName);
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testGetToolDescription() {
      for (Operation operation : service.getOperations()) {
         String toolDescription = toolConverter.getToolDescription(operation);
         if ("allPets".equals(operation.getName())) {
            assertEquals(" Retrieve all pets from the store. This is not a paginated query.", toolDescription);
         } else if ("searchPets".equals(operation.getName())) {
            assertNull(toolDescription);
         } else if ("advancedSearchPets".equals(operation.getName())) {
            assertNull(toolDescription);
         } else if ("createPet".equals(operation.getName())) {
            assertNull(toolDescription);
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testGetInputSchema() throws Exception {
      ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

      for (Operation operation : service.getOperations()) {
         McpSchema.JsonSchema inputSchema = toolConverter.getInputSchema(operation);

         if ("allPets".equals(operation.getName())) {
            assertTrue(inputSchema.properties().isEmpty());

            assertEquals("""
                  ---
                  type: "object"
                  properties: {}
                  required: []
                  additionalProperties: false
                  """, mapper.writeValueAsString(inputSchema));

         } else if ("searchPets".equals(operation.getName())) {
            assertFalse(inputSchema.properties().isEmpty());

            assertTrue(inputSchema.properties().containsKey("name"));
            assertEquals("""
                  ---
                  type: "object"
                  properties:
                    name:
                      type: "string"
                  required:
                  - "name"
                  additionalProperties: false
                  """, mapper.writeValueAsString(inputSchema));

         } else if ("advancedSearchPets".equals(operation.getName())) {
            assertFalse(inputSchema.properties().isEmpty());
            assertTrue(inputSchema.properties().containsKey("filters"));

            assertEquals("""
                  ---
                  type: "object"
                  properties:
                    filters:
                      type: "array"
                      items:
                        type: "object"
                        properties:
                          name:
                            type: "string"
                          value:
                            type: "string"
                        required:
                        - "name"
                        - "value"
                        additionalProperties: false
                  required:
                  - "filters"
                  additionalProperties: false
                  """, mapper.writeValueAsString(inputSchema));

         } else if ("createPet".equals(operation.getName())) {
            assertFalse(inputSchema.properties().isEmpty());
            assertTrue(inputSchema.properties().containsKey("newPet"));

            assertEquals("""
                  ---
                  type: "object"
                  properties:
                    newPet:
                      type: "object"
                      properties:
                        name:
                          type: "string"
                        color:
                          type: "string"
                      required:
                      - "name"
                      - "color"
                      additionalProperties: false
                  required:
                  - "newPet"
                  additionalProperties: false
                  """, mapper.writeValueAsString(inputSchema));
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }
}
