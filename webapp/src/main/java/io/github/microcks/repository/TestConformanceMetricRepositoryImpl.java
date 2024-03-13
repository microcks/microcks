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

import io.github.microcks.domain.TestConformanceMetric;
import io.github.microcks.domain.WeightedMetricValue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

import java.util.List;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Implementation for CustomTestConformanceMetricRepository.
 * @author laurent
 */
public class TestConformanceMetricRepositoryImpl implements CustomTestConformanceMetricRepository {

   @Autowired
   private MongoTemplate template;

   @Override
   public List<WeightedMetricValue> aggregateTestConformanceMetric() {
      // Match all but group by label (domain) and compute average and weight.
      Aggregation aggregation = newAggregation(
            group("aggregationLabelValue").avg("currentScore").as("value").count().as("weight"),
            project("value", "weight").and("_id").as("name"), sort(Sort.Direction.DESC, "value"));
      AggregationResults<WeightedMetricValue> results = template.aggregate(aggregation, TestConformanceMetric.class,
            WeightedMetricValue.class);
      return results.getMappedResults();
   }
}
