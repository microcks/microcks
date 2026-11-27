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
import io.microcks.domain.ServiceState;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for ServiceStateRepository implementation.
 * @author laurent
 */
@QuarkusTest
class ServiceStateRepositoryTest {

   @Inject
   ServiceStateRepository repository;

   @BeforeEach
   public void setUp() {
      ServiceState status = new ServiceState("azertyuiop", "foo");
      status.setValue("bar");
      repository.persist(status);
   }

   @Test
   void testFindByServiceIdAndKey() {
      ServiceState status = repository.findByServiceIdAndKey("azertyuiop", "foo");
      assertEquals("bar", status.getValue());
   }
}
