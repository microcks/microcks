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

import io.microcks.domain.ImportJob;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/**
 * Repository for {@code ImportJob} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class ImportJobRepository implements ObjectIdRepository<ImportJob> {

   public List<ImportJob> findByNameLike(String name) {
      return list(RepositoryQueries.nameLike(name));
   }

   public List<ImportJob> findByServiceRefId(String serviceRefId) {
      return list("{'serviceRefs': {'$elemMatch': {'serviceId': ?1}}}", serviceRefId);
   }

   public List<ImportJob> findByLabels(Map<String, String> labels) {
      return list(RepositoryQueries.labels(labels, null));
   }

   public List<ImportJob> findByLabelsAndNameLike(Map<String, String> labels, String name) {
      return list(RepositoryQueries.labels(labels, name));
   }
}
