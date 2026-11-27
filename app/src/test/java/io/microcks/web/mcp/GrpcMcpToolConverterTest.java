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
package io.microcks.web.mcp;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.Operation;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Service;
import io.microcks.repository.ResourceRepository;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;
import io.microcks.util.ai.McpSchema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test class for GRPCMcpToolConverter.
 * @author laurent
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class GrpcMcpToolConverterTest {

   @Inject
   private ServiceService serviceService;

   @Inject
   private ResourceRepository resourceRepository;

   private Service servicev1;
   private Service servicev2;
   private GrpcMcpToolConverter toolConverterv1;
   private GrpcMcpToolConverter toolConverterv2;

   @BeforeAll
   void setUp() throws Exception {
      // Import the petstore service definition from the GRPC tutorial.
      File artifactFilev1 = new File("target/test-classes/io/microcks/artifact/grpc/petstore-v1.proto");
      File artifactFilev2 = new File("target/test-classes/io/microcks/artifact/grpc/petstore-v2.proto");
      // Extract service and resource. + tool converters.
      List<Service> services = serviceService.importServiceDefinition(artifactFilev1, null,
            new ArtifactInfo("petstore-v1.proto", true));
      servicev1 = services.getFirst();
      List<Resource> resources = resourceRepository.findByServiceIdAndType(servicev1.getId(),
            ResourceType.PROTOBUF_DESCRIPTOR);
      toolConverterv1 = new GrpcMcpToolConverter(servicev1, resources.getFirst(), null, new ObjectMapper());
      // For v2.
      services = serviceService.importServiceDefinition(artifactFilev2, null,
            new ArtifactInfo("petstore-v2.proto", true));
      servicev2 = services.getFirst();
      resources = resourceRepository.findByServiceIdAndType(servicev2.getId(), ResourceType.PROTOBUF_DESCRIPTOR);
      toolConverterv2 = new GrpcMcpToolConverter(servicev2, resources.getFirst(), null, new ObjectMapper());
   }

   @Test
   void testGetToolName() {
      for (Operation operation : servicev1.getOperations()) {
         String toolName = toolConverterv1.getToolName(operation);
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
      for (Operation operation : servicev1.getOperations()) {
         String toolDescription = toolConverterv1.getToolDescription(operation);
         assertNull(toolDescription);
      }
   }

   @Test
   void testGetInputSchema() throws Exception {
      for (Operation operation : servicev1.getOperations()) {
         McpSchema.JsonSchema inputSchema = toolConverterv1.getInputSchema(operation);
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

      String expectedCreatePetSchema = """
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
                    - "LIGHT"
                    - "DARK"
                required: []
                additionalProperties: false
              bugs:
                type: "array"
                items:
                  type: "string"
                  enum:
                  - "TICK"
                  - "FLEA"
              tags:
                type: "array"
                items:
                  type: "string"
              foobars:
                type: "array"
                items:
                  type: "object"
                  properties:
                    foo:
                      type: "string"
                    bar:
                      type: "string"
            required: []
            additionalProperties: false
            """;

      for (Operation operation : servicev2.getOperations()) {
         if ("createPet".equals(operation.getName())) {
            McpSchema.JsonSchema inputSchema = toolConverterv2.getInputSchema(operation);
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            try {
               String result = mapper.writeValueAsString(inputSchema);
               assertEquals(expectedCreatePetSchema, result);
            } catch (Exception e) {
               fail("Failed to serialize input schema for operation " + operation.getName(), e);
            }
         }
      }
   }
}
