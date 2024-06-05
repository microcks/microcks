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
package io.github.microcks.service;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.RepositoryTestsConfiguration;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ServiceRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for ImportExportService class.
 * @author laurent
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class ImportExportServiceTest {

   @Autowired
   private ImportExportService service;

   @Autowired
   private ServiceRepository repository;

   @Autowired
   private ResourceRepository resourceRepository;

   private List<String> ids = new ArrayList<>();

   @BeforeEach
   public void setUp() {
      // Create a bunch of services...
      Service service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.2");
      repository.save(service);
      ids.add(service.getId());
      // with same name and different version ...
      service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.1");
      repository.save(service);
      ids.add(service.getId());
      // with different name ...
      service = new Service();
      service.setName("MyService-hello");
      service.setVersion("1.1");
      repository.save(service);
      ids.add(service.getId());

      // Create associated resources.
      Resource resource = new Resource();
      resource.setServiceId(ids.get(0));
      resource.setName("Resource 1");
      resource.setType(ResourceType.WSDL);
      resource.setContent("<wsdl></wsdl>");
      resourceRepository.save(resource);
   }

   @Test
   void testExportRepository() {
      String result = service.exportRepository(ids, "json");

      ObjectMapper mapper = new ObjectMapper();

      // Check that result is a valid JSON object.
      JsonNode jsonObj = null;
      try {
         jsonObj = mapper.readTree(result);
      } catch (IOException e) {
         fail("No exception should be thrown when parsing Json");
      }

      try {
         // Retrieve and assert on services part.
         ArrayNode services = (ArrayNode) jsonObj.get("services");
         assertEquals(3, services.size());
         for (int i = 0; i < services.size(); i++) {
            JsonNode service = services.get(i);
            String name = service.get("name").asText();
            assertTrue("HelloWorld".equals(name) || "MyService-hello".equals(name));
         }
      } catch (Exception e) {
         fail("Exception while getting services array");
      }

      try {
         // Retrieve and assert on resources part.
         ArrayNode resources = (ArrayNode) jsonObj.get("resources");
         assertEquals(1, resources.size());
         JsonNode resource = resources.get(0);
         assertEquals("Resource 1", resource.get("name").asText());
         assertEquals("<wsdl></wsdl>", resource.get("content").asText());
      } catch (Exception e) {
         fail("Exception while getting resources array");
      }
   }

   @Test
   void testImportRepository() {
      // Setup and export result.
      String json = "{\"services\":[{\"name\":\"Imp1\",\"version\":\"1.2\",\"xmlNS\":null,\"type\":null,\"operations\":[],\"id\":25638445759706201468670970602},"
            + "{\"name\":\"Imp2\",\"version\":\"1.1\",\"xmlNS\":null,\"type\":null,\"operations\":[],\"id\":25638445759706201468670970603}], "
            + "\"resources\":[{\"name\":\"Resource 1\",\"content\":\"<wsdl></wsdl>\",\"type\":\"WSDL\",\"serviceId\":25638445759706201468670970602,\"id\":25638445759706201468670970605}], "
            + "\"requests\":[], \"responses\":[]}";

      boolean result = service.importRepository(json);

      // Get imported service and assert on content.
      Service service = repository.findByNameAndVersion("Imp1", "1.2");
      assertNotNull(service);
      assertEquals("Imp1", service.getName());
      assertEquals("1.2", service.getVersion());
      assertEquals("25638445759706201468670970602", service.getId().toString());

      // Get imported resources and assert on content.
      List<Resource> resources = resourceRepository.findByServiceId(service.getId());
      assertEquals(1, resources.size());
      assertEquals("Resource 1", resources.get(0).getName());
      assertEquals("<wsdl></wsdl>", resources.get(0).getContent());
      assertEquals(ResourceType.WSDL, resources.get(0).getType());
   }
}
