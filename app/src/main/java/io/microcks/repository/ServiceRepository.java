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

import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;

import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Repository for {@code Service} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class ServiceRepository implements ObjectIdRepository<Service>, CustomServiceRepository {

   private static final String NUMBER_FIELD = "number";

   public Service findByNameAndVersion(String name, String version) {
      return find("name = ?1 and version = ?2", name, version).firstResult();
   }

   public List<Service> findByType(ServiceType type) {
      return list("type", type.name());
   }

   public List<Service> findByNameLike(String name) {
      return list(RepositoryQueries.nameLike(name));
   }

   @Override
   public List<Service> findByLabels(Map<String, String> labels) {
      return list(RepositoryQueries.labels(labels, null));
   }

   @Override
   public List<Service> findByLabelsAndNameLike(Map<String, String> labels, String name) {
      return list(RepositoryQueries.labels(labels, name));
   }

   @Override
   public List<ServiceCount> countServicesByType() {
      List<Document> pipeline = List.of(new Document("$project", new Document("type", 1)),
            new Document("$group", new Document("_id", "$type").append(NUMBER_FIELD, new Document("$sum", 1))),
            new Document("$sort", new Document(NUMBER_FIELD, -1)));
      List<ServiceCount> results = new ArrayList<>();
      for (Document doc : mongoCollection().aggregate(pipeline, Document.class)) {
         ServiceCount count = new ServiceCount();
         count.setType(doc.getString("_id"));
         count.setNumber(doc.get(NUMBER_FIELD, Number.class).intValue());
         results.add(count);
      }
      return results;
   }

   @Override
   public List<LabelValues> listLabels() {
      List<Document> pipeline = List.of(
            new Document("$project", new Document("labels", new Document("$objectToArray", "$metadata.labels"))),
            new Document("$unwind", "$labels"), new Document("$sort", new Document("labels.v", -1)), new Document(
                  "$group", new Document("_id", "$labels.k").append("values", new Document("$addToSet", "$labels.v"))));
      List<LabelValues> results = new ArrayList<>();
      for (Document doc : mongoCollection().aggregate(pipeline, Document.class)) {
         LabelValues labelValues = new LabelValues();
         labelValues.setKey(doc.getString("_id"));
         labelValues.setValues(doc.getList("values", String.class).toArray(new String[0]));
         results.add(labelValues);
      }
      return results;
   }
}
