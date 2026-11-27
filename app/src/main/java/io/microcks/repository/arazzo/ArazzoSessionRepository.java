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

import io.microcks.domain.arazzo.ArazzoSession;
import io.microcks.repository.ObjectIdRepository;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.bson.conversions.Bson;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Repository for the verification sessions of Arazzo workflows. Sessions expire at their {@code expireAt} date.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoSessionRepository implements ObjectIdRepository<ArazzoSession> {

   private static final String ID = "_id";
   private static final String CLOSED_DATE = "closedDate";
   private static final String EXPIRE_AT = "expireAt";

   /** Panache has no declarative index support: create the TTL index (idempotent) at startup. */
   void ensureTtlIndex(@Observes StartupEvent event) {
      mongoCollection().createIndex(Indexes.ascending(EXPIRE_AT), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
   }

   /**
    * Count a call an open session receives, atomically, so that calls received at once each get their own order index.
    * @param sessionId The id of a session
    * @param maxCalls  How many calls a session records at most
    * @param now       When the call is received
    * @return The session with the call counted, its count telling the order index of the call; null when the session
    *         does not exist, is closed, has expired or has recorded its maximum of calls
    */
   public ArazzoSession countCall(String sessionId, long maxCalls, Date now) {
      // The count is incremented in the database, so that two calls received at once get two indexes.
      Bson filter = Filters.and(idFilter(sessionId), Filters.eq(CLOSED_DATE, null), Filters.gt(EXPIRE_AT, now),
            Filters.lt("callCount", maxCalls));
      return mongoCollection().findOneAndUpdate(filter, Updates.inc("callCount", 1L),
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
   }

   /**
    * Count a call an open session does not record, as it recorded its maximum of calls; atomically.
    * @param sessionId The id of a session
    * @param maxCalls  How many calls a session records at most
    * @param now       When the call is received
    */
   public void countDroppedCall(String sessionId, long maxCalls, Date now) {
      mongoCollection().updateOne(Filters.and(idFilter(sessionId), Filters.eq(CLOSED_DATE, null),
            Filters.gt(EXPIRE_AT, now), Filters.gte("callCount", maxCalls)), Updates.inc("droppedCallCount", 1L));
   }

   /**
    * Close an open session: it records no more calls, and it expires at the given date. A session already closed is
    * left as it is.
    * @param sessionId  The id of a session
    * @param closedDate When it is closed
    * @param expireAt   When it expires
    * @return Whether this call closed the session; false when it does not exist or was already closed
    */
   public boolean closeOpen(String sessionId, Date closedDate, Date expireAt) {
      return mongoCollection().findOneAndUpdate(Filters.and(idFilter(sessionId), Filters.eq(CLOSED_DATE, null)),
            Updates.combine(Updates.set(CLOSED_DATE, closedDate), Updates.set(EXPIRE_AT, expireAt))) != null;
   }

   private static Bson idFilter(String sessionId) {
      return Filters.eq(ID, ObjectIdRepository.toStoredId(sessionId));
   }
}
