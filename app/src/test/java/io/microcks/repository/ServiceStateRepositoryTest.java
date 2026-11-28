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

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for ServiceStateRepository implementation.
 * @author laurent
 */
@QuarkusTest
class ServiceStateRepositoryTest {

   @Inject
   ServiceStateRepository repository;

   @Inject
   MongoClient mongoClient;

   @BeforeEach
   public void setUp() {
      ServiceState status = new ServiceState("azertyuiop", "foo");
      status.setValue("bar");
      repository.persist(status);
   }

   @Test
   void testFindByServiceIdAndKey() {
      ServiceState status = repository.findByServiceIdAndKey("azertyuiop", "foo");
      assertEquals("bar", status.getValue());
   }

   /** At startup, the states of the services get the unique index that keeps one state per key of a service. */
   @Test
   void theStatesGetAUniqueIndexOnTheKeyOfAServiceAtStartup() {
      assertEquals(List.of("serviceId_key"), indexesOf(true));

      ServiceState second = new ServiceState("azertyuiop", "foo");
      second.setValue("baz");
      assertThrows(MongoWriteException.class, () -> repository.persist(second));
      assertEquals("bar", repository.findByServiceIdAndKey("azertyuiop", "foo").getValue());
   }

   /** Writing a key again replaces its value and expiry: a service keeps one state per key. */
   @Test
   void upsertingAKeyReplacesItsStateInsteadOfAddingOne() {
      repository.upsert("azertyuiop", "foo", "baz", new Date(0L));
      repository.upsert("azertyuiop", "other", "1", new Date(0L));

      assertEquals(List.of("baz", "1"), List.of(repository.findByServiceIdAndKey("azertyuiop", "foo").getValue(),
            repository.findByServiceIdAndKey("azertyuiop", "other").getValue()));
      assertEquals(2, repository.count());
   }

   /**
    * The unique index of the states cannot be built over two states of one key written before it existed: the TTL index
    * is still ensured.
    */
   @Test
   void anIndexTheDataDoesNotAllowLeavesTheOtherIndexesEnsured() {
      MongoCollection<Document> legacy = mongoClient.getDatabase(repository.mongoDatabase().getName())
            .getCollection("serviceStateWithTwins");
      legacy.insertMany(List.of(new Document("serviceId", "a").append("key", "k"),
            new Document("serviceId", "a").append("key", "k")));
      try {
         ServiceStateRepository.ensureIndexes(legacy);

         assertEquals(List.of("expireAt_1"), legacy.listIndexes().into(new ArrayList<>()).stream()
               .filter(index -> index.containsKey("expireAfterSeconds")).map(index -> index.getString("name"))
               .toList());
         assertEquals(List.of("_id_", "expireAt_1"),
               legacy.listIndexes().into(new ArrayList<>()).stream().map(index -> index.getString("name")).toList());
      } finally {
         legacy.deleteMany(new Document());
      }
   }

   private List<String> indexesOf(boolean unique) {
      return repository.mongoCollection().listIndexes(Document.class).into(new ArrayList<>()).stream()
            .filter(index -> Boolean.TRUE.equals(index.getBoolean("unique", false)) == unique
                  && !"_id_".equals(index.getString("name")))
            .map(index -> index.getString("name")).toList();
   }
}
