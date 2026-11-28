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
package io.microcks.repository.arazzo;

import io.microcks.repository.ObjectIdRepository;
import io.microcks.domain.arazzo.ArazzoMetadata;

import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for the ArazzoMetadata imported into Microcks.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoMetadataRepository implements ObjectIdRepository<ArazzoMetadata> {

   public ArazzoMetadata findByNameAndVersion(String name, String version) {
      return find("name = ?1 and version = ?2", name, version).firstResult();
   }

   /** @return Every stored item, sorted by name then version. */
   public List<ArazzoMetadata> findAllSorted() {
      return listAll(Sort.by("name", "version"));
   }
}
