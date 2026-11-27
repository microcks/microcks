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

import io.microcks.domain.TestConformanceMetric;
import io.microcks.domain.WeightedMetricValue;

import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for {@code TestConformanceMetric} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class TestConformanceMetricRepository implements ObjectIdRepository<TestConformanceMetric> {

   private static final String VALUE_FIELD = "value";

   public TestConformanceMetric findByServiceId(String serviceId) {
      return find("serviceId", serviceId).firstResult();
   }

   /** Average conformance score and weight (number of services) per aggregation label value. */
   public List<WeightedMetricValue> aggregateTestConformanceMetric() {
      List<Document> pipeline = List.of(
            new Document("$group",
                  new Document("_id", "$aggregationLabelValue")
                        .append(VALUE_FIELD, new Document("$avg", "$currentScore"))
                        .append("weight", new Document("$sum", 1))),
            new Document("$sort", new Document(VALUE_FIELD, -1)));
      List<WeightedMetricValue> results = new ArrayList<>();
      for (Document doc : mongoCollection().aggregate(pipeline, Document.class)) {
         WeightedMetricValue metric = new WeightedMetricValue();
         metric.setName(doc.getString("_id"));
         metric.setValue(doc.get(VALUE_FIELD, Number.class).doubleValue());
         metric.setWeight(doc.get("weight", Number.class).intValue());
         results.add(metric);
      }
      return results;
   }
}
