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

package io.microcks.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the user onboarding and disabling.
 * @author laurent
 */
class UserServiceTest {

   private UserRepository users;
   private UserService service;

   @BeforeEach
   void setUp() {
      users = mock(UserRepository.class);
      service = new UserService();
      service.users = users;
   }

   private static UserInfo info(String role) {
      return new UserInfo("Jane Doe", "jane", "Jane", "Doe", "Jane@Example.com", new String[] { role }, new String[0]);
   }

   @Test
   void firstLoginCreatesUserAndNextRefreshesRoles() {
      User created = service.recordLogin(info("manager"), "sub-1", true);
      verify(users).persist(created);
      assertEquals("jane@example.com", created.email);
      assertEquals(List.of("manager"), created.roles);

      when(users.findByUsername("jane")).thenReturn(created);
      service.recordLogin(info("admin"), "sub-1", true);
      verify(users).update(created);
      assertEquals(List.of("admin"), created.roles);
   }

   @Test
   void disabledStateIsEffectiveAfterToggle() {
      User user = new User();
      user.username = "jane";
      when(users.findByUsername("jane")).thenReturn(user);
      assertFalse(service.isDisabled("jane"));
      service.setDisabled(user, true);
      assertTrue(service.isDisabled("jane"));
      assertFalse(service.isDisabled("unknown-service-account"));
   }
}
