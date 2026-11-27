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
import io.microcks.web.RestInvocationProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test class for OpenAPIMcpToolConverter.
 * @author laurent
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAPIMcpToolConverterTest {

   @Inject
   private ServiceService serviceService;

   @Inject
   private ResourceRepository resourceRepository;

   @Inject
   private RestInvocationProcessor restInvocationProcessor;

   private Service servicev1;
   private Service servicev2;
   private OpenAPIMcpToolConverter toolConverterv1;
   private OpenAPIMcpToolConverter toolConverterv2;

   @BeforeAll
   void setUp() throws Exception {
      // Import the petstore service definition from the REST tutorial.
      File artifactFilev1 = new File("target/test-classes/io/microcks/artifact/openapi/petstore-1.0.0-openapi.yaml");
      File artifactFilev2 = new File("target/test-classes/io/microcks/artifact/openapi/petstore-2.0.0-openapi.yaml");
      // Extract service and resource. + tool converters.
      List<Service> services = serviceService.importServiceDefinition(artifactFilev1, null,
            new ArtifactInfo("petstore-1.0.0-openapi.yaml", true));
      servicev1 = services.getFirst();
      List<Resource> resources = resourceRepository.findByServiceIdAndType(servicev1.getId(),
            ResourceType.OPEN_API_SPEC);
      toolConverterv1 = new OpenAPIMcpToolConverter(servicev1, resources.getFirst(), resourceRepository,
            restInvocationProcessor, new ObjectMapper());
      // For v2.
      services = serviceService.importServiceDefinition(artifactFilev2, null,
            new ArtifactInfo("petstore-2.0.0-openapi.yaml", true));
      servicev2 = services.getFirst();
      resources = resourceRepository.findByServiceIdAndType(servicev2.getId(), ResourceType.OPEN_API_SPEC);
      toolConverterv2 = new OpenAPIMcpToolConverter(servicev2, resources.getFirst(), resourceRepository,
            restInvocationProcessor, new ObjectMapper());
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

   @Test
   void testExternalReferenceResolution() {
      // 1. Create a dummy Service
      Service externalRefService = new Service();
      externalRefService.setId("6626365f24874269b65c6a01");
      externalRefService.setName("External Ref API");
      externalRefService.setVersion("1.0.0");
      externalRefService.setType(io.microcks.domain.ServiceType.REST);

      // 2. Create the main Resource
      Resource mainResource = new Resource();
      mainResource.setId("6626365f24874269b65c6a02");
      mainResource.setServiceId(externalRefService.getId());
      mainResource.setType(ResourceType.OPEN_API_SPEC);
      mainResource.setName("main.yaml");
      mainResource.setContent("""
            openapi: 3.0.0
            info:
              title: External Ref API
              version: 1.0.0
            paths:
              /test:
                post:
                  operationId: postTest
                  requestBody:
                    content:
                      application/json:
                        schema:
                          $ref: 'models.yaml#/components/schemas/TestModel'
            """);
      resourceRepository.persist(mainResource);

      // 3. Create the external Resource
      Resource extResource = new Resource();
      extResource.setId("6626365f24874269b65c6a03");
      extResource.setServiceId(externalRefService.getId());
      extResource.setType(ResourceType.OPEN_API_SPEC);
      extResource.setName("models.yaml");
      extResource.setPath("models.yaml");
      extResource.setContent("""
            components:
              schemas:
                TestModel:
                  type: object
                  properties:
                    testField:
                      type: string
                  required:
                    - testField
            """);
      resourceRepository.persist(extResource);

      // 4. Create the Operation
      Operation operation = new Operation();
      operation.setName("POST /test");
      operation.setMethod("POST");

      // 5. Instantiate converter
      OpenAPIMcpToolConverter converter = new OpenAPIMcpToolConverter(externalRefService, mainResource,
            resourceRepository, restInvocationProcessor, new ObjectMapper());

      // 6. Assert input schema
      McpSchema.JsonSchema inputSchema = converter.getInputSchema(operation);
      assertNotNull(inputSchema, "Input schema should not be null");
      assertEquals("object", inputSchema.type());
      assertFalse(inputSchema.properties().isEmpty(), "Properties should not be empty");
      assertTrue(inputSchema.properties().containsKey("testField"),
            "Properties should contain 'testField' from external ref");
   }

   @Test
   @SuppressWarnings("unchecked")
   void testPathLevelAndOperationLevelParameters() {
      // 1. Create a dummy Service
      Service pathParamService = new Service();
      pathParamService.setId("6626365f24874269b65c6a04");
      pathParamService.setName("Path Params API");
      pathParamService.setVersion("1.0.0");
      pathParamService.setType(io.microcks.domain.ServiceType.REST);

      // 2. Create the main Resource with path-level and operation-level parameters
      Resource mainResource = new Resource();
      mainResource.setId("6626365f24874269b65c6a05");
      mainResource.setServiceId(pathParamService.getId());
      mainResource.setType(ResourceType.OPEN_API_SPEC);
      mainResource.setName("path-params.yaml");
      mainResource.setContent("""
            openapi: 3.0.0
            info:
              title: Path Params API
              version: 1.0.0
            paths:
              /orders/{orderId}:
                parameters:
                  - name: orderId
                    in: path
                    description: The unique order identifier
                    schema:
                      type: integer
                      format: int64
                get:
                  operationId: getOrder
                  parameters:
                    - name: includeDetails
                      in: query
                      description: Whether to include order details
                      schema:
                        type: boolean
                        default: false
                  responses:
                    '200':
                      description: OK
            """);
      resourceRepository.persist(mainResource);

      // 3. Create the Operation
      Operation operation = new Operation();
      operation.setName("GET /orders/{orderId}");
      operation.setMethod("GET");

      // 4. Instantiate converter
      OpenAPIMcpToolConverter converter = new OpenAPIMcpToolConverter(pathParamService, mainResource,
            resourceRepository, restInvocationProcessor, new ObjectMapper());

      // 5. Assert input schema
      McpSchema.JsonSchema inputSchema = converter.getInputSchema(operation);
      assertNotNull(inputSchema, "Input schema should not be null");
      assertEquals("object", inputSchema.type());
      assertEquals(2, inputSchema.properties().size(), "Should contain 2 parameters");

      // Verify path-level parameter 'orderId'
      assertTrue(inputSchema.properties().containsKey("orderId"),
            "Properties should contain 'orderId' from path-level");
      Map<String, Object> orderIdProp = (Map<String, Object>) inputSchema.properties().get("orderId");
      assertEquals("integer", orderIdProp.get("type"), "orderId type should be preserved as integer");
      assertEquals("int64", orderIdProp.get("format"), "orderId format should be preserved as int64");
      assertEquals("The unique order identifier", orderIdProp.get("description"));
      assertTrue(inputSchema.required().contains("orderId"), "Path parameter 'orderId' should be required");

      // Verify operation-level parameter 'includeDetails'
      assertTrue(inputSchema.properties().containsKey("includeDetails"), "Properties should contain 'includeDetails'");
      Map<String, Object> detailsProp = (Map<String, Object>) inputSchema.properties().get("includeDetails");
      assertEquals("boolean", detailsProp.get("type"), "includeDetails type should be boolean");
      assertEquals("Whether to include order details", detailsProp.get("description"));
   }

   @Test
   @SuppressWarnings("unchecked")
   void testOperationLevelParameterOverridesPathLevel() {
      // 1. Create a dummy Service
      Service overrideService = new Service();
      overrideService.setId("6626365f24874269b65c6a06");
      overrideService.setName("Override Params API");
      overrideService.setVersion("1.0.0");
      overrideService.setType(io.microcks.domain.ServiceType.REST);

      // 2. Create Resource where operation-level overrides path-level description
      Resource mainResource = new Resource();
      mainResource.setId("6626365f24874269b65c6a07");
      mainResource.setServiceId(overrideService.getId());
      mainResource.setType(ResourceType.OPEN_API_SPEC);
      mainResource.setName("override-params.yaml");
      mainResource.setContent("""
            openapi: 3.0.0
            info:
              title: Override Params API
              version: 1.0.0
            paths:
              /items/{itemId}:
                parameters:
                  - name: itemId
                    in: path
                    description: Path-level item identifier
                    schema:
                      type: string
                get:
                  operationId: getItem
                  parameters:
                    - name: itemId
                      in: path
                      description: Operation-level item identifier override
                      schema:
                        type: string
                  responses:
                    '200':
                      description: OK
            """);
      resourceRepository.persist(mainResource);

      Operation operation = new Operation();
      operation.setName("GET /items/{itemId}");
      operation.setMethod("GET");

      OpenAPIMcpToolConverter converter = new OpenAPIMcpToolConverter(overrideService, mainResource, resourceRepository,
            restInvocationProcessor, new ObjectMapper());

      McpSchema.JsonSchema inputSchema = converter.getInputSchema(operation);
      assertNotNull(inputSchema);
      assertTrue(inputSchema.properties().containsKey("itemId"));
      Map<String, Object> itemIdProp = (Map<String, Object>) inputSchema.properties().get("itemId");
      assertEquals("Operation-level item identifier override", itemIdProp.get("description"),
            "Operation-level parameter should override path-level parameter");
   }
}

