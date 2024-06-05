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

import io.github.microcks.domain.GenericResource;
import io.github.microcks.domain.Service;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for DynamicResourceRepository class.
 * @author laurent
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class GenericResourceRepositoryTest {

   @Autowired
   GenericResourceRepository repository;

   @Autowired
   ServiceRepository serviceRepository;

   @Test
   void testCreateGenericResource() {

      // Create a minimal service.
      Service service = new Service();
      service.setName("DynamicAPI");
      service.setVersion("1.0");
      serviceRepository.save(service);

      // Define a JSON payload to put into a GenericResource associated to service.
      String jsonString = "{ \"foo\": 1234, \"bar\": \"string value\" }";
      GenericResource resource = new GenericResource();
      resource.setServiceId(service.getId());
      resource.setPayload(Document.parse(jsonString));

      // Save to repository.
      GenericResource dynaResource = repository.save(resource);

      // Test some manipulations of the payload Document.
      Document payload = dynaResource.getPayload();
      payload.append("id", dynaResource.getId());

      assertTrue(1234 == payload.getInteger("foo"));
      assertEquals("string value", payload.getString("bar"));
      assertEquals(dynaResource.getId(), payload.getString("id"));

      // Adding more resources before querying.
      jsonString = "{ \"foo\": 1234, \"bar\": \"other value\" }";
      GenericResource resource1 = new GenericResource();
      resource1.setServiceId(service.getId());
      resource1.setPayload(Document.parse(jsonString));
      repository.save(resource1);

      jsonString = "{ \"foo\": 1235, \"bar\": \"other value\" }";
      GenericResource resource2 = new GenericResource();
      resource2.setServiceId(service.getId());
      resource2.setPayload(Document.parse(jsonString));
      repository.save(resource2);

      // Query by example using 1 field.
      List<GenericResource> dynaResources = repository.findByServiceIdAndJSONQuery(service.getId(),
            "{ \"foo\": 1234 }");
      assertEquals(2, dynaResources.size());
      for (GenericResource r : dynaResources) {
         assertTrue(resource.getId().equals(r.getId()) || resource1.getId().equals(r.getId()));
      }

      // Query by example using 1 other field value.
      dynaResources = repository.findByServiceIdAndJSONQuery(service.getId(), "{ \"foo\": 1235 }");
      assertEquals(1, dynaResources.size());
      assertEquals(resource2.getId(), dynaResources.get(0).getId());

      // Query by example using 2 fields.
      dynaResources = repository.findByServiceIdAndJSONQuery(service.getId(),
            "{ \"foo\": 1234, \"bar\": \"other value\"}");
      assertEquals(1, dynaResources.size());
      assertEquals(resource1.getId(), dynaResources.get(0).getId());

      // Query by example using 1 field with complex expression.
      dynaResources = repository.findByServiceIdAndJSONQuery(service.getId(), "{ \"foo\": {$gt: 1234, $lt: 1236} }");
      assertEquals(1, dynaResources.size());
      assertEquals(resource2.getId(), dynaResources.get(0).getId());
   }
}
