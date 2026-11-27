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
package io.microcks.repository;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.Service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for ServiceRepository implementation.
 * @author laurent
 */
@QuarkusTest
class ServiceRepositoryTest {

   @Inject
   ServiceRepository repository;

   String serviceId;

   @BeforeEach
   public void setUp() {
      // Create a bunch of services...
      Service service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.2");
      repository.persist(service);
      // with same name and different version ...
      service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.1");
      repository.persist(service);
      // with different name ...
      service = new Service();
      service.setName("MyService-hello");
      service.setVersion("1.1");
      repository.persist(service);
      serviceId = service.getId();
   }

   @Test
   void testFindOne() {
      Service service = repository.findById(serviceId);
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
}
