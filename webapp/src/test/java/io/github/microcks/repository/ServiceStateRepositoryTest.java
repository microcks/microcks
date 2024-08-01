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

import io.github.microcks.domain.ServiceState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for ServiceStateRepository implementation.
 * @author laurent
 */
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class ServiceStateRepositoryTest {

   @Autowired
   ServiceStateRepository repository;

   @BeforeEach
   public void setUp() {
      ServiceState status = new ServiceState("azertyuiop", "foo");
      status.setValue("bar");
      repository.save(status);
   }

   @Test
   void testFindByServiceIdAndKey() {
      ServiceState status = repository.findByServiceIdAndKey("azertyuiop", "foo");
      assertEquals("bar", status.getValue());
   }
}
