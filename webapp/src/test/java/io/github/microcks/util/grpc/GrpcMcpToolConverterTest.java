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
package io.github.microcks.util.grpc;

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
 * This is a test class for GRPCMcpToolConverter.
 * @author laurent
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringJUnitConfig(classes = { RepositoryTestsConfiguration.class, ControllerTestsConfiguration.class })
@TestPropertySource(locations = { "classpath:/config/test.properties" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GrpcMcpToolConverterTest {

   @Autowired
   private ServiceService serviceService;

   @Autowired
   private ResourceRepository resourceRepository;

   private Service service;
   private GrpcMcpToolConverter toolConverter;

   @BeforeAll
   void setUp() throws Exception {
      // Import the petstore service definition from the GRPC tutorial.
      File artifactFile = new File("target/test-classes/io/github/microcks/util/grpc/petstore-v1.proto");
      List<Service> services = serviceService.importServiceDefinition(artifactFile, null,
            new ArtifactInfo("petstore-v1.proto", true));
      // Extract service and resource.
      service = services.getFirst();
      List<Resource> resources = resourceRepository.findByServiceIdAndType(service.getId(),
            ResourceType.PROTOBUF_DESCRIPTOR);
      // Prepare the tool converter.
      toolConverter = new GrpcMcpToolConverter(service, resources.getFirst(), null, null);
   }

   @Test
   void testGetToolName() {
      for (Operation operation : service.getOperations()) {
         String toolName = toolConverter.getToolName(operation);
         if ("getPets".equals(operation.getName())) {
            assertEquals("getPets", toolName);
         } else if ("searchPets".equals(operation.getName())) {
            assertEquals("searchPets", toolName);
         } else if ("createPet".equals(operation.getName())) {
            assertEquals("createPet", toolName);
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testGetToolDescription() {
      // Tool description is not implemented in GrpcMcpToolConverter.
      for (Operation operation : service.getOperations()) {
         String toolDescription = toolConverter.getToolDescription(operation);
         assertNull(toolDescription);
      }
   }

   @Test
   void testGetInputSchema() {
      for (Operation operation : service.getOperations()) {
         McpSchema.JsonSchema inputSchema = toolConverter.getInputSchema(operation);
         assertEquals("object", inputSchema.type());
         if ("getPets".equals(operation.getName())) {
            assertTrue(inputSchema.properties().isEmpty());
         } else if ("searchPets".equals(operation.getName())) {
            assertFalse(inputSchema.properties().isEmpty());
            assertTrue(inputSchema.properties().containsKey("name"));
         } else if ("createPet".equals(operation.getName())) {
            assertFalse(inputSchema.properties().isEmpty());
            assertTrue(inputSchema.properties().containsKey("name"));
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }
}
