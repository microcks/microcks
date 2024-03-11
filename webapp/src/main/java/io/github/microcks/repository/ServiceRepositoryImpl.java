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

import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
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

   @Autowired
   private MongoTemplate template;

   @Override
   public List<Service> findByIdIn(List<String> ids) {
      // Convert ids into BSON ObjectId.
      List<ObjectId> objIds = new ArrayList<ObjectId>();
      for (String id : ids) {
         objIds.add(new ObjectId(id));
      }

      List<Service> results = template.find(new Query(Criteria.where("_id").in(objIds)), Service.class);

      return results;
   }

   @Override
   public List<Service> findByLabels(Map<String, String> labels) {
      Query query = new Query();
      for (String labelKey : labels.keySet()) {
         query.addCriteria(Criteria.where("metadata.labels." + labelKey).is(labels.get(labelKey)));
      }
      List<Service> results = template.find(query, Service.class);
      return results;
   }

   @Override
   public List<Service> findByLabelsAndNameLike(Map<String, String> labels, String name) {
      Query query = new Query(Criteria.where("name").regex(name, "i"));
      for (String labelKey : labels.keySet()) {
         query.addCriteria(Criteria.where("metadata.labels." + labelKey).is(labels.get(labelKey)));
      }
      List<Service> results = template.find(query, Service.class);
      return results;
   }

   public List<ServiceCount> countServicesByType() {
      Aggregation aggregation = newAggregation(project("type"), group("type").count().as("number"),
            project("number").and("type").previousOperation(), sort(DESC, "number"));
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
