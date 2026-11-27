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
 * Repository for {@code User} objects.
 * @author laurent
 */
@ApplicationScoped
public class UserRepository implements ObjectIdRepository<User> {

   public User findByUsername(String username) {
      return find("username", username).firstResult();
   }

   public List<User> findVerifiedByEmail(String email) {
      return list("email = ?1 and emailVerified = true", email);
   }
}
