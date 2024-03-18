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

import io.github.microcks.domain.Service;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Test case for ServiceRepository implementation.
 * @author laurent
 */
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
public class ServiceRepositoryTest {

   @Autowired
   ServiceRepository repository;

   String serviceId;

   @Before
   public void setUp() {
      // Create a bunch of services...
      Service service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.2");
      repository.save(service);
      // with same name and different version ...
      service = new Service();
      service.setName("HelloWorld");
      service.setVersion("1.1");
      repository.save(service);
      // with different name ...
      service = new Service();
      service.setName("MyService-hello");
      service.setVersion("1.1");
      repository.save(service);
      serviceId = service.getId();
   }

   @Test
   public void testFindOne() {
      Service service = repository.findById(serviceId).orElse(null);
      assertNotNull(service);
      assertEquals("MyService-hello", service.getName());
      assertEquals("1.1", service.getVersion());
   }

   @Test
   public void testFindByNameAndVersion() {
      Service service = repository.findByNameAndVersion("HelloWorld", "1.2");
      assertNotNull(service);
      assertEquals("HelloWorld", service.getName());
      assertEquals("1.2", service.getVersion());
   }

   @Test
   public void testFindByNameLike() {
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
