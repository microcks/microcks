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

import io.microcks.domain.ServiceState;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.concurrent.TimeUnit;

/**
 * Repository for {@code ServiceState} domain objects. Documents expire at their {@code expireAt} date.
 * @author laurent
 */
@ApplicationScoped
public class ServiceStateRepository implements ObjectIdRepository<ServiceState> {

   /** Panache has no declarative index support: create the TTL index (idempotent) at startup. */
   void ensureTtlIndex(@Observes StartupEvent event) {
      mongoCollection().createIndex(Indexes.ascending("expireAt"),
            new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
   }

   public ServiceState findByServiceIdAndKey(String serviceId, String key) {
      return find("serviceId = ?1 and key = ?2", serviceId, key).firstResult();
   }
}
