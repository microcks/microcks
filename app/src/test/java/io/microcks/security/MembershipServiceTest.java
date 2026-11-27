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
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the group membership resolution.
 * @author laurent
 */
class MembershipServiceTest {

   private MembershipRepository memberships;
   private GroupRepository groups;
   private UserRepository users;
   private MembershipService service;

   @BeforeEach
   void setUp() {
      memberships = mock(MembershipRepository.class);
      groups = mock(GroupRepository.class);
      service = new MembershipService();
      service.memberships = memberships;
      service.groups = groups;
      users = mock(UserRepository.class);
      service.users = users;
   }

   private static Membership membership(String group, String role, String subject, String email) {
      Membership m = new Membership();
      m.groupName = group;
      m.role = role;
      m.subject = subject;
      m.email = email;
      return m;
   }

   @Test
   void managerIsAlsoUserOfItsGroups() {
      when(memberships.findBySubject("jane")).thenReturn(List.of(membership("a", "manager", "jane", null),
            membership("b", "user", "jane", null)));
      assertEquals(List.of("/microcks/manager/a", "/microcks/user/a", "/microcks/user/b"),
            service.groupPaths("jane"));
   }

   @Test
   void pendingMembershipIsBoundToSubjectOnClaim() {
      Membership pending = membership("a", "user", null, "jane@example.com");
      when(memberships.findPendingByEmail("jane@example.com")).thenReturn(List.of(pending));
      assertEquals(1, service.claimPending("jane", "Jane@Example.com"));
      assertEquals("jane", pending.subject);
      verify(memberships).update(pending);
   }

   @Test
   void addMemberValidatesArguments() {
      assertThrows(IllegalArgumentException.class, () -> service.addMember("nope", "user", "jane", null));
      when(groups.findByName("a")).thenReturn(new Group());
      assertThrows(IllegalArgumentException.class, () -> service.addMember("a", "admin", "jane", null));
      assertThrows(IllegalArgumentException.class, () -> service.addMember("a", "user", "jane", "j@e.com"));
      assertThrows(IllegalArgumentException.class, () -> service.addMember("a", "user", null, null));

      service.addMember("a", "user", null, "Jane@Example.com");
      ArgumentCaptor<Membership> captor = ArgumentCaptor.forClass(Membership.class);
      verify(memberships).persist(captor.capture());
      assertEquals("jane@example.com", captor.getValue().email);
      assertEquals(true, captor.getValue().isPending());
   }

   @Test
   void emailOfKnownUserIsBoundToItsAccount() {
      when(groups.findByName("a")).thenReturn(new Group());
      User known = new User();
      known.username = "jane";
      when(users.findVerifiedByEmail("jane@example.com")).thenReturn(List.of(known));
      Membership created = service.addMember("a", "user", null, "Jane@Example.com");
      assertEquals("jane", created.subject);
      assertEquals(false, created.isPending());
   }
}
