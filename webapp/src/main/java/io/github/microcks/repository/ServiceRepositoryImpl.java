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
package io.github.microcks.repository;

import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ObjectOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.data.domain.Sort.Direction.DESC;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Implementation for CustomServiceRepository.
 * @author laurent
 */
public class ServiceRepositoryImpl implements CustomServiceRepository {

   private static final String NUMBER_FIELD = "number";

   private MongoTemplate template;

   public ServiceRepositoryImpl(MongoTemplate template) {
      this.template = template;
   }

   @Override
   public List<Service> findByIdIn(List<String> ids) {
      // Convert ids into BSON ObjectId.
      List<ObjectId> objIds = new ArrayList<>();
      for (String id : ids) {
         objIds.add(new ObjectId(id));
      }

      return template.find(new Query(Criteria.where("_id").in(objIds)), Service.class);
   }

   @Override
   public List<Service> findByLabels(Map<String, String> labels) {
      Query query = new Query();
      for (Map.Entry<String, String> entry : labels.entrySet()) {
         query.addCriteria(Criteria.where("metadata.labels." + entry.getKey()).is(entry.getValue()));
      }
      return template.find(query, Service.class);
   }

   @Override
   public List<Service> findByLabelsAndNameLike(Map<String, String> labels, String name) {
      Query query = new Query(Criteria.where("name").regex(name, "i"));
      for (Map.Entry<String, String> entry : labels.entrySet()) {
         query.addCriteria(Criteria.where("metadata.labels." + entry.getKey()).is(entry.getValue()));
      }
      return template.find(query, Service.class);
   }

   @Override
   public List<Service> search(String name, Map<String, String> labels, String version, ServiceType type,
         Pageable pageable) {
      Query query = buildSearchQuery(name, labels, version, type);
      if (pageable != null) {
         query.with(pageable);
      }
      return template.find(query, Service.class);
   }

   @Override
   public List<Service> search(String name, Map<String, String> labels, String version, ServiceType type, Sort sort) {
      Query query = buildSearchQuery(name, labels, version, type);
      if (sort != null) {
         query.with(sort);
      }
      return template.find(query, Service.class);
   }

   @Override
   public long countSearch(String name, Map<String, String> labels, String version, ServiceType type) {
      Query query = buildSearchQuery(name, labels, version, type);
      return template.count(query, Service.class);
   }

   private Query buildSearchQuery(String name, Map<String, String> labels, String version, ServiceType type) {
      Query query = new Query();
      if (name != null && !name.trim().isEmpty()) {
         query.addCriteria(Criteria.where("name").regex(name.trim(), "i"));
      }
      if (labels != null && !labels.isEmpty()) {
         for (Map.Entry<String, String> entry : labels.entrySet()) {
            query.addCriteria(Criteria.where("metadata.labels." + entry.getKey()).is(entry.getValue()));
         }
      }
      if (version != null && !version.trim().isEmpty()) {
         query.addCriteria(Criteria.where("version").is(version.trim()));
      }
      if (type != null) {
         query.addCriteria(Criteria.where("type").is(type));
      }
      return query;
   }

   public List<ServiceCount> countServicesByType() {
      Aggregation aggregation = newAggregation(project("type"), group("type").count().as(NUMBER_FIELD),
            project(NUMBER_FIELD).and("type").previousOperation(), sort(DESC, NUMBER_FIELD));
      AggregationResults<ServiceCount> results = template.aggregate(aggregation, Service.class, ServiceCount.class);
      return results.getMappedResults();
   }

   public List<LabelValues> listLabels() {
      ObjectOperators.ObjectToArray labels = ObjectOperators.ObjectToArray.valueOfToArray("metadata.labels");
      Aggregation aggregation = newAggregation(project().and(labels).as("labels"), unwind("labels"),
            sort(DESC, "labels.v"), group("labels.k").addToSet("labels.v").as("values").first("labels.k").as("key"));
      AggregationResults<LabelValues> results = template.aggregate(aggregation, Service.class, LabelValues.class);
      return results.getMappedResults();
   }
}
