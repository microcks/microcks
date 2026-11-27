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
package io.github.microcks.repository;

import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for ServiceRepository implementation.
 * @author laurent
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class ServiceRepositoryTest {

   @Autowired
   ServiceRepository repository;

   String serviceId;

   @BeforeEach
   public void setUp() throws Exception {
      // Create a bunch of services...
      Service service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.2");
      service.setType(ServiceType.REST);
      Metadata metadata = new Metadata();
      metadata.setLabels(Map.of("domain", "test", "env", "prod"));
      service.setMetadata(metadata);
      repository.save(service);

      Thread.sleep(10);

      // with same name and different version ...
      service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.1");
      service.setType(ServiceType.SOAP_HTTP);
      metadata = new Metadata();
      metadata.setLabels(Map.of("domain", "test", "env", "stage"));
      service.setMetadata(metadata);
      repository.save(service);

      Thread.sleep(10);

      // with different name ...
      service = new Service();
      service.setName("MyService-hello");
      service.setVersion("1.1");
      service.setType(ServiceType.EVENT);
      metadata = new Metadata();
      metadata.setLabels(Map.of("domain", "finance"));
      service.setMetadata(metadata);
      repository.save(service);
      serviceId = service.getId();
   }

   @Test
   void testFindOne() {
      Service service = repository.findById(serviceId).orElse(null);
      assertNotNull(service);
      assertEquals("MyService-hello", service.getName());
      assertEquals("1.1", service.getVersion());
   }

   @Test
   void testFindByNameAndVersion() {
      Service service = repository.findByNameAndVersion("HelloWorld", "1.2");
      assertNotNull(service);
      assertEquals("HelloWorld", service.getName());
      assertEquals("1.2", service.getVersion());
   }

   @Test
   void testFindByNameLike() {
      List<Service> services = repository.findByNameLike("world");
      assertTrue(!services.isEmpty());
      assertEquals(2, services.size());
      for (Service service : services) {
         assertEquals("HelloWorld", service.getName());
      }

      services = repository.findByNameLike("Hello");
      assertTrue(!services.isEmpty());
      assertEquals(3, services.size());
   }

   @Test
   void testSearchWithNoCriteriaReturnsAllServices() {
      // Calling search without criteria returns all services without throwing IllegalArgumentException
      List<Service> services = repository.search(null, null, null, null, (Sort) null);
      assertEquals(3, services.size());
      assertEquals(3, repository.countSearch(null, null, null, null));
   }

   @Test
   void testSearchByName() {
      List<Service> services = repository.search("world", null, null, null, (Sort) null);
      assertEquals(2, services.size());
      for (Service service : services) {
         assertEquals("HelloWorld", service.getName());
      }
   }

   @Test
   void testSearchByLabels() {
      List<Service> services = repository.search(null, Map.of("domain", "test"), null, null, (Sort) null);
      assertEquals(2, services.size());

      services = repository.search(null, Map.of("domain", "test", "env", "prod"), null, null, (Sort) null);
      assertEquals(1, services.size());
      assertEquals("1.2", services.get(0).getVersion());
   }

   @Test
   void testSearchByVersion() {
      List<Service> services = repository.search(null, null, "1.2", null, (Sort) null);
      assertEquals(1, services.size());
      assertEquals("HelloWorld", services.get(0).getName());

      services = repository.search(null, null, "1.1", null, (Sort) null);
      assertEquals(2, services.size());
   }

   @Test
   void testSearchByType() {
      List<Service> services = repository.search(null, null, null, ServiceType.REST, (Sort) null);
      assertEquals(1, services.size());
      assertEquals(ServiceType.REST, services.get(0).getType());

      services = repository.search(null, null, null, ServiceType.EVENT, (Sort) null);
      assertEquals(1, services.size());
      assertEquals("MyService-hello", services.get(0).getName());
   }

   @Test
   void testSearchCombinedFilters() {
      List<Service> services = repository.search("Hello", Map.of("domain", "test"), "1.1", ServiceType.SOAP_HTTP,
            (Sort) null);
      assertEquals(1, services.size());
      assertEquals("HelloWorld", services.get(0).getName());
      assertEquals("1.1", services.get(0).getVersion());
      assertEquals(ServiceType.SOAP_HTTP, services.get(0).getType());

      // Should return empty if one filter doesn't match
      services = repository.search("Hello", Map.of("domain", "finance"), "1.1", ServiceType.SOAP_HTTP, (Sort) null);
      assertTrue(services.isEmpty());
   }

   @Test
   void testSearchPagination() {
      // 3 services in total matching "Hello"
      PageRequest page1 = PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "name", "version"));
      List<Service> resultPage1 = repository.search("Hello", null, null, null, page1);
      assertEquals(2, resultPage1.size());
      assertEquals("HelloWorld", resultPage1.get(0).getName());
      assertEquals("1.1", resultPage1.get(0).getVersion());
      assertEquals("HelloWorld", resultPage1.get(1).getName());
      assertEquals("1.2", resultPage1.get(1).getVersion());

      PageRequest page2 = PageRequest.of(1, 2, Sort.by(Sort.Direction.ASC, "name", "version"));
      List<Service> resultPage2 = repository.search("Hello", null, null, null, page2);
      assertEquals(1, resultPage2.size());
      assertEquals("MyService-hello", resultPage2.get(0).getName());
   }

   @Test
   void testSearchSorting() {
      // Sort by metadata.lastUpdate DESC
      List<Service> services = repository.search(null, null, null, null,
            Sort.by(Sort.Direction.DESC, "metadata.lastUpdate"));
      assertEquals(3, services.size());
      assertEquals("MyService-hello", services.get(0).getName()); // lastUpdate = 3000000L
      assertEquals("1.1", services.get(1).getVersion()); // lastUpdate = 2000000L
      assertEquals("1.2", services.get(2).getVersion()); // lastUpdate = 1000000L

      // Sort by version DESC
      services = repository.search("HelloWorld", null, null, null, Sort.by(Sort.Direction.DESC, "version"));
      assertEquals(2, services.size());
      assertEquals("1.2", services.get(0).getVersion());
      assertEquals("1.1", services.get(1).getVersion());
   }

   @Test
   void testCountSearch() {
      assertEquals(3, repository.countSearch("Hello", null, null, null));
      assertEquals(2, repository.countSearch("world", null, null, null));
      assertEquals(2, repository.countSearch(null, Map.of("domain", "test"), null, null));
      assertEquals(1, repository.countSearch(null, Map.of("domain", "test", "env", "prod"), null, null));
      assertEquals(1, repository.countSearch(null, null, "1.2", null));
      assertEquals(1, repository.countSearch(null, null, null, ServiceType.REST));
      assertEquals(0, repository.countSearch("nonexistent", null, null, null));
   }
}
