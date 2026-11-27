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

import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.test.ResourceReader;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for {@code Resource} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class ResourceRepository implements ObjectIdRepository<Resource>, ResourceReader {

   public List<Resource> findByName(String name) {
      return list("name", name);
   }

   public List<Resource> findByServiceId(String serviceId) {
      return list("serviceId", serviceId);
   }

   public List<Resource> findByServiceIdAndType(String serviceId, ResourceType type) {
      return list("serviceId = ?1 and type = ?2", serviceId, type.name());
   }

   public List<Resource> findByServiceIdAndSourceArtifact(String serviceId, String sourceArtifact) {
      return list("serviceId = ?1 and sourceArtifact = ?2", serviceId, sourceArtifact);
   }

   public List<Resource> findByServiceIdIn(List<String> serviceIds) {
      return list("serviceId in ?1", serviceIds);
   }

   public List<Resource> findMainByServiceId(String serviceId) {
      return list("mainArtifact = ?1 and serviceId = ?2", true, serviceId);
   }
}
