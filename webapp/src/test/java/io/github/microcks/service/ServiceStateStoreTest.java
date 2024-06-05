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

import io.github.microcks.domain.ServiceState;
import io.github.microcks.repository.RepositoryTestsConfiguration;
import io.github.microcks.repository.ServiceStateRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for ServiceStateStore class.
 * @author laurent
 */
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class ServiceStateStoreTest {

   private static final String SERVICE_ID = "serviceId";

   @Autowired
   ServiceStateRepository repository;

   ServiceStateStore store;

   @BeforeEach
   public void setUp() {
      store = new ServiceStateStore(repository, SERVICE_ID);
   }

   @Test
   void testServiceStateStore() {
      // Test empty repo.
      String value = store.get("foo");
      assertNull(value);

      // Put one value with default TTL and check.
      store.put("foo", "bar");
      ServiceState state = repository.findByServiceIdAndKey(SERVICE_ID, "foo");
      assertEquals("foo", state.getKey());
      assertEquals("bar", state.getValue());
      assertEquals(SERVICE_ID, state.getServiceId());

      Calendar in9sec = Calendar.getInstance();
      in9sec.add(Calendar.SECOND, 9);
      Calendar in11sec = Calendar.getInstance();
      in11sec.add(Calendar.SECOND, 11);
      assertTrue(state.getExpireAt().after(in9sec.getTime()));
      assertTrue(state.getExpireAt().before(in11sec.getTime()));

      // Test retrieve existing key.
      value = store.get("foo");
      assertEquals("bar", value);

      // Update value with non default TTL and check.
      store.put("foo", "baz", 3600);
      state = repository.findByServiceIdAndKey(SERVICE_ID, "foo");
      assertEquals("foo", state.getKey());
      assertEquals("baz", state.getValue());
      assertEquals(SERVICE_ID, state.getServiceId());

      Calendar inL1h = Calendar.getInstance();
      inL1h.add(Calendar.SECOND, 3500);
      Calendar inG1h = Calendar.getInstance();
      inG1h.add(Calendar.SECOND, 3700);
      assertTrue(state.getExpireAt().after(inL1h.getTime()));
      assertTrue(state.getExpireAt().before(inG1h.getTime()));

      // Remove key.
      store.delete("foo");
      state = repository.findByServiceIdAndKey(SERVICE_ID, "foo");
      assertNull(state);
   }
}
