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

import io.microcks.domain.GenericResource;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;

import java.util.List;

/**
 * Repository for {@code GenericResource} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class GenericResourceRepository implements ObjectIdRepository<GenericResource> {

   public List<GenericResource> findByServiceId(String serviceId, Page page, Sort sort) {
      return find("serviceId", sort, serviceId).page(page).list();
   }

   public List<GenericResource> findReferencesByServiceId(String serviceId) {
      return list("reference = ?1 and serviceId = ?2", true, serviceId);
   }

   public long countByServiceId(String serviceId) {
      return count("serviceId", serviceId);
   }

   /**
    * Find resources of a service whose payload matches a JSON query (keys are relative to {@code payload}).
    * @param serviceId The service identifier
    * @param jsonQuery The JSON query, for example {@code {"name": "foo"}}
    * @return The matching resources
    */
   public List<GenericResource> findByServiceIdAndJSONQuery(String serviceId, String jsonQuery) {
      Document source = Document.parse(jsonQuery);
      Document query = new Document();
      for (String key : source.keySet()) {
         query.append("payload." + key, source.get(key));
      }
      query.append("serviceId", serviceId);
      return list(query);
   }
}
