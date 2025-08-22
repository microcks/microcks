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
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.listener.ListenerTestsConfiguration;
import io.github.microcks.repository.RepositoryTestsConfiguration;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.service.ArtifactInfo;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.ai.McpSchema;
import io.github.microcks.web.ControllerTestsConfiguration;
import io.github.microcks.web.RestInvocationProcessor;

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
 * This is a test class for OpenAPIMcpToolConverter.
 * @author laurent
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringJUnitConfig(classes = { RepositoryTestsConfiguration.class, ControllerTestsConfiguration.class,
      ListenerTestsConfiguration.class })
@TestPropertySource(locations = { "classpath:/config/test.properties" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAPIMcpToolConverterTest {

   @Autowired
   private ServiceService serviceService;

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private RestInvocationProcessor restInvocationProcessor;

   private Service servicev1;
   private Service servicev2;
   private OpenAPIMcpToolConverter toolConverterv1;
   private OpenAPIMcpToolConverter toolConverterv2;

   @BeforeAll
   void setUp() throws Exception {
      // Import the petstore service definition from the REST tutorial.
      File artifactFilev1 = new File("target/test-classes/io/github/microcks/util/openapi/petstore-1.0.0-openapi.yaml");
      File artifactFilev2 = new File("target/test-classes/io/github/microcks/util/openapi/petstore-2.0.0-openapi.yaml");
      // Extract service and resource. + tool converters.
      List<Service> services = serviceService.importServiceDefinition(artifactFilev1, null,
            new ArtifactInfo("petstore-1.0.0-openapi.yaml", true));
      servicev1 = services.getFirst();
      List<Resource> resources = resourceRepository.findByServiceIdAndType(servicev1.getId(),
            ResourceType.OPEN_API_SPEC);
      toolConverterv1 = new OpenAPIMcpToolConverter(servicev1, resources.getFirst(), restInvocationProcessor,
            new ObjectMapper());
      // For v2.
      services = serviceService.importServiceDefinition(artifactFilev2, null,
            new ArtifactInfo("petstore-2.0.0-openapi.yaml", true));
      servicev2 = services.getFirst();
      resources = resourceRepository.findByServiceIdAndType(servicev2.getId(), ResourceType.OPEN_API_SPEC);
      toolConverterv2 = new OpenAPIMcpToolConverter(servicev2, resources.getFirst(), restInvocationProcessor,
            new ObjectMapper());
   }

   @Test
   void testGetToolName() {
      for (Operation operation : servicev1.getOperations()) {
         String toolName = toolConverterv1.getToolName(operation);
         if ("GET /my/pets".equals(operation.getName())) {
            assertEquals("get_my_pets", toolName);
         } else if ("GET /pets".equals(operation.getName())) {
            assertEquals("get_pets", toolName);
         } else if ("POST /pets".equals(operation.getName())) {
            assertEquals("post_pets", toolName);
         } else if ("GET /pets/{id}".equals(operation.getName())) {
            assertEquals("get_pets_id", toolName);
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testGetToolDescription() {
      for (Operation operation : servicev1.getOperations()) {
         String toolDescription = toolConverterv1.getToolDescription(operation);
         if ("GET /my/pets".equals(operation.getName())) {
            assertEquals("A list of pets owned by the user", toolDescription);
         } else if ("GET /pets".equals(operation.getName())) {
            assertEquals("A list of all pets filtered by name", toolDescription);
         } else if ("POST /pets".equals(operation.getName())) {
            assertEquals("Add a new pet", toolDescription);
         } else if ("GET /pets/{id}".equals(operation.getName())) {
            assertEquals("Get a pet by its ID", toolDescription);
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testGetInputSchema() {
      for (Operation operation : servicev1.getOperations()) {
         McpSchema.JsonSchema inputSchema = toolConverterv1.getInputSchema(operation);
         assertEquals("object", inputSchema.type());
         if ("GET /my/pets".equals(operation.getName())) {
            assertTrue(inputSchema.properties().isEmpty());
         } else if ("GET /pets".equals(operation.getName())) {
            assertFalse(inputSchema.properties().isEmpty());
            assertTrue(inputSchema.properties().containsKey("filter"));
         } else if ("POST /pets".equals(operation.getName())) {
            assertFalse(inputSchema.properties().isEmpty());
            assertTrue(inputSchema.properties().containsKey("name"));
         } else if ("GET /pets/{id}".equals(operation.getName())) {
            assertFalse(inputSchema.properties().isEmpty());
            assertTrue(inputSchema.properties().containsKey("id"));
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }

      String expectedPostPetsSchema = """
            ---
            type: "object"
            properties:
              name:
                type: "string"
              coat:
                type: "object"
                properties:
                  name:
                    type: "string"
                  tint:
                    type: "string"
                    enum:
                    - "light"
                    - "dark"
                required:
                - "name"
                additionalProperties: false
              bugs:
                type: "array"
                items:
                  type: "string"
                  enum:
                  - "tick"
                  - "flea"
            required:
            - "name"
            additionalProperties: false
            """;

      for (Operation operation : servicev2.getOperations()) {
         if ("POST /pets".equals(operation.getName())) {
            McpSchema.JsonSchema inputSchema = toolConverterv2.getInputSchema(operation);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try {
               String result = mapper.writeValueAsString(inputSchema);
               assertEquals(expectedPostPetsSchema, result);
            } catch (Exception e) {
               fail("Failed to serialize input schema for operation " + operation.getName(), e);
            }
         }
      }
   }
}

