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

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Optional;

/**
 * Thin extension of the Panache repository contract for entities exposing their {@code _id} as a hex String
 * ({@code @BsonRepresentation(OBJECT_ID)}). Panache builds its id filters from the raw value, so a String would never
 * match the stored ObjectId: this interface only converts the id used in lookups and deletes. It holds no state and no
 * persistence logic, everything else is stock Panache.
 * @param <E> The entity type
 * @author laurent
 */
public interface ObjectIdRepository<E> extends PanacheMongoRepositoryBase<E, String> {

   /** Convert a hex String into an ObjectId when valid, keep the raw value otherwise (legacy non-ObjectId ids). */
   static Object toStoredId(String id) {
      return ObjectId.isValid(id) ? new ObjectId(id) : id;
   }

   @Override
   default E findById(String id) {
      return find(new Document("_id", toStoredId(id))).firstResult();
   }

   @Override
   default Optional<E> findByIdOptional(String id) {
      return Optional.ofNullable(findById(id));
   }

   @Override
   default boolean deleteById(String id) {
      return delete(new Document("_id", toStoredId(id))) > 0;
   }

   /** Insert or update an entity and return it, with its generated id (Spring Data {@code save} equivalent). */
   default E save(E entity) {
      persistOrUpdate(entity);
      return entity;
   }

   /** Delete a collection of entities (Spring Data {@code deleteAll(Iterable)} equivalent). */
   default void deleteAll(Iterable<? extends E> entities) {
      for (E entity : entities) {
         delete(entity);
      }
   }

   /** Find entities by a list of hex String identifiers. */
   default List<E> findByIdIn(List<String> ids) {
      return list(new Document("_id", new Document("$in", ids.stream().map(ObjectIdRepository::toStoredId).toList())));
   }
}
