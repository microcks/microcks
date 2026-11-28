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
package io.microcks.repository.workflow;

import io.microcks.domain.workflow.WorkflowSessionRecordedCall;
import io.microcks.repository.ObjectIdRepository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import io.quarkus.panache.common.Sort;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Repository for the calls the mocks received in the verification sessions of Arazzo workflows, kept in their own
 * collection. Calls expire at their {@code expireAt} date.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class WorkflowSessionRecordedCallRepository implements ObjectIdRepository<WorkflowSessionRecordedCall> {

   /** Panache has no declarative index support: create the indexes (idempotent) at startup. */
   void ensureIndexes(@Observes StartupEvent event) {
      mongoCollection().createIndex(Indexes.ascending("sessionId"));
      mongoCollection().createIndex(Indexes.ascending("expireAt"),
            new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
   }

   /**
    * @param sessionId The id of a session
    * @return The calls the session received, in their order of receipt
    */
   public List<WorkflowSessionRecordedCall> findBySessionIdOrderByIndexAsc(String sessionId) {
      return find("sessionId", Sort.ascending("index"), sessionId).list();
   }

   /**
    * Make the calls of a session expire at the given date, as the session itself does when it is closed.
    * @param sessionId The id of a session
    * @param expireAt  When its calls expire
    */
   public void expireCalls(String sessionId, Date expireAt) {
      mongoCollection().updateMany(Filters.eq("sessionId", sessionId),
            Updates.set("expireAt", expireAt));
   }
}
