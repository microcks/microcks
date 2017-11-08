/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.service;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.repository.RepositoryTestsConfiguration;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.EntityAlreadyExistsException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

/**
 * Test case for ServiceService class.
 * @author laurent
 */
@RunWith(SpringJUnit4ClassRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = RepositoryTestsConfiguration.class)
public class ServiceServiceTest {

   @Autowired
   private ServiceService service;

   @Autowired
   private ServiceRepository repository;

   @Test
   public void testCreateGenericResourceService() {
      Service created = null;
      try {
         created = service.createGenericResourceService("Order Service", "1.0", "order");
      } catch (Exception e) {
         fail("No exception should be thrown");
      }

      // Check created object.
      assertNotNull(created.getId());

      // Retrieve object by id and assert on what has been persisted.
      Service retrieved = repository.findOne(created.getId());
      assertEquals("Order Service", retrieved.getName());
      assertEquals("1.0", retrieved.getVersion());
      assertEquals(ServiceType.GENERIC_REST, retrieved.getType());

      // Now check operations.
      assertEquals(5, retrieved.getOperations().size());
      for (Operation op : retrieved.getOperations()) {
         if ("POST /order".equals(op.getName())) {
            assertEquals("POST", op.getMethod());
         } else if ("GET /order/:id".equals(op.getName())) {
            assertEquals("GET", op.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, op.getDispatcher());
            assertEquals("id", op.getDispatcherRules());
         } else if ("GET /order".equals(op.getName())) {
            assertEquals("GET", op.getMethod());
         } else if ("PUT /order/:id".equals(op.getName())) {
            assertEquals("PUT", op.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, op.getDispatcher());
            assertEquals("id", op.getDispatcherRules());
         } else if ("DELETE /order/:id".equals(op.getName())) {
            assertEquals("DELETE", op.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, op.getDispatcher());
            assertEquals("id", op.getDispatcherRules());
         } else {
            fail("Unknown operation name: " + op.getName());
         }
      }
   }

   @Test(expected = EntityAlreadyExistsException.class)
   public void testCreateGenericResourceServiceFailure() throws EntityAlreadyExistsException {
      try {
         Service first = service.createGenericResourceService("Order Service", "1.0", "order");
      } catch (Exception e) {
         fail("No exception should be raised on first save()!");
      }
      Service second = service.createGenericResourceService("Order Service", "1.0", "order");
   }
}
