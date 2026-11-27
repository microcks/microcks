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

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.TestConformanceMetric;
import io.microcks.domain.WeightedMetricValue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test case for CustomTestConformanceMetricRepository class.
 * @author laurent
 */
@QuarkusTest
class CustomTestConformanceMetricRepositoryTest {

   @Inject
   TestConformanceMetricRepository repository;

   @Test
   void testAggregateTestMetricCoverage() {
      // Save a bunch of coverage metrics.
      TestConformanceMetric m1 = new TestConformanceMetric();
      m1.setAggregationLabelValue("domain1");
      m1.setCurrentScore(90);

      TestConformanceMetric m2 = new TestConformanceMetric();
      m2.setAggregationLabelValue("domain1");
      m2.setCurrentScore(70);

      TestConformanceMetric m3 = new TestConformanceMetric();
      m3.setAggregationLabelValue("domain2");
      m3.setCurrentScore(85);

      repository.persist(m1);
      repository.persist(m2);
      repository.persist(m3);

      // Query domain aggregates and assert.
      List<WeightedMetricValue> domainValues = repository.aggregateTestConformanceMetric();

      assertEquals(2, domainValues.size());
      assertEquals("domain2", domainValues.get(0).getName());
      assertEquals(1, domainValues.get(0).getWeight());
      assertEquals(85, domainValues.get(0).getValue(), 0);
      assertEquals("domain1", domainValues.get(1).getName());
      assertEquals(2, domainValues.get(1).getWeight());
      assertEquals(80, domainValues.get(1).getValue(), 0);
   }
}
