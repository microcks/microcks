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

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Repository for {@code ServiceState} domain objects. Documents expire at their {@code expireAt} date, and a service
 * has one state per key.
 * @author laurent
 */
@ApplicationScoped
public class ServiceStateRepository implements ObjectIdRepository<ServiceState> {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ServiceStateRepository.class);

   /** Panache has no declarative index support: ensure the indexes (idempotent) at startup. */
   void ensureIndexesAtStartup(@Observes StartupEvent event) {
      ensureIndexes();
   }

   /**
    * Ensure the TTL index of the states and the unique index that keeps one state per key of a service. They are
    * ensured one by one: an index the data does not allow, as the unique index over two states of one key written
    * before it existed, is reported and does not stop the other one from being ensured.
    */
   public void ensureIndexes() {
      ensureIndexes(mongoCollection());
   }

   /**
    * Ensure the indexes of the states on a collection of states, as {@link #ensureIndexes()} does on the real one.
    * @param states The collection of states to ensure the indexes of
    */
   static void ensureIndexes(MongoCollection<?> states) {
      ensureIndex(states, Indexes.ascending("expireAt"), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
      ensureIndex(states, Indexes.ascending("serviceId", "key"),
            new IndexOptions().unique(true).name("serviceId_key"));
   }

   private static void ensureIndex(MongoCollection<?> states, Bson keys, IndexOptions options) {
      try {
         states.createIndex(keys, options);
      } catch (MongoException me) {
         log.warn("Index {} could not be ensured: {}", keys, me.getMessage());
      }
   }

   public ServiceState findByServiceIdAndKey(String serviceId, String key) {
      return find("serviceId = ?1 and key = ?2", serviceId, key).firstResult();
   }

   /**
    * Keep a value under a key of the state of a service, atomically: the state is created when the key has none, else
    * its value and expiry are replaced. Two writes of one key at the same instant keep one state, with the last value.
    * @param serviceId The id of the service the state belongs to
    * @param key       The key of the value
    * @param value     The value
    * @param expireAt  When the state expires
    */
   public void upsert(String serviceId, String key, String value, Date expireAt) {
      Bson state = Filters.and(Filters.eq("serviceId", serviceId), Filters.eq("key", key));
      Bson write = Updates.combine(Updates.set("value", value), Updates.set("expireAt", expireAt));
      try {
         mongoCollection().updateOne(state, write, new UpdateOptions().upsert(true));
      } catch (MongoWriteException mwe) {
         if (mwe.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) {
            throw mwe;
         }
         // The unique index refused a second state for the key, created by a write at the same instant: the key now
         // has its state, which this write replaces. MongoDB retries such an upsert itself; not every compatible
         // database does.
         mongoCollection().updateOne(state, write, new UpdateOptions().upsert(true));
      }
   }
}
