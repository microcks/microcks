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

import io.microcks.domain.Secret;
import io.microcks.test.SecretReader;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for {@code Secret} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class SecretRepository implements ObjectIdRepository<Secret>, SecretReader {

   @Override
   public Secret findById(String id) {
      return ObjectIdRepository.super.findById(id);
   }

   public List<Secret> findByName(String name) {
      return list("name", name);
   }

   public List<Secret> findByNameLike(String name) {
      return list(RepositoryQueries.nameLike(name));
   }
}
