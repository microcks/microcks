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
import io.github.microcks.repository.RepositoryTestsConfiguration;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.service.ArtifactInfo;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.ai.McpSchema;
import io.github.microcks.web.ControllerTestsConfiguration;
import io.github.microcks.web.RestInvocationProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@SpringJUnitConfig(classes = { RepositoryTestsConfiguration.class, ControllerTestsConfiguration.class})
@TestPropertySource(locations = { "classpath:/config/test.properties" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAPIMcpToolConverterTest {

   @Autowired
   private ServiceService serviceService;

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private RestInvocationProcessor restInvocationProcessor;

   private Service service;
   private OpenAPIMcpToolConverter toolConverter;

   @BeforeAll
   void setUp() throws Exception {
      // Import the petstore service definition from the REST tutorial.
      File artifactFile = new File("target/test-classes/io/github/microcks/util/openapi/petstore-1.0.0-openapi.yaml");
      List<Service> services = serviceService.importServiceDefinition(artifactFile, null, new ArtifactInfo("petstore-1.0.0-openapi.yaml", true));
      // Extract service and resource.
      service = services.getFirst();
      List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.OPEN_API_SPEC);
      // Prepare the tool converter.
      toolConverter = new OpenAPIMcpToolConverter(service, resources.getFirst(), restInvocationProcessor, new ObjectMapper());
   }

   @Test
   void testGetToolName() {
      for (Operation operation : service.getOperations()) {
         String toolName = toolConverter.getToolName(operation);
         if ("GET /my/pets".equals(operation.getName())) {
            assertEquals("GET__my_pets", toolName);
         } else if ("GET /pets".equals(operation.getName())) {
            assertEquals("GET__pets", toolName);
         } else if ("POST /pets".equals(operation.getName())) {
            assertEquals("POST__pets", toolName);
         } else if ("GET /pets/{id}".equals(operation.getName())) {
            assertEquals("GET__pets_id", toolName);
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testGetToolDescription() {
      for (Operation operation : service.getOperations()) {
         String toolDescription = toolConverter.getToolDescription(operation);
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
      for (Operation operation : service.getOperations()) {
         McpSchema.JsonSchema inputSchema = toolConverter.getInputSchema(operation);
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
   }
}


