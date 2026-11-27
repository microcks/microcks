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

import io.microcks.repository.ObjectIdRepository;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for {@code Membership} objects.
 * @author laurent
 */
@ApplicationScoped
public class MembershipRepository implements ObjectIdRepository<Membership> {

   public List<Membership> findByGroupName(String groupName) {
      return list("groupName", groupName);
   }

   public List<Membership> findBySubject(String subject) {
      return list("subject", subject);
   }

   public List<Membership> findPendingByEmail(String email) {
      return list("email = ?1 and subject is null", email);
   }

   public long deleteByGroupName(String groupName) {
      return delete("groupName", groupName);
   }
}
