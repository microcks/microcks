/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.service;

import io.microcks.domain.TestConformanceMetric;
import io.microcks.domain.TestResult;
import io.microcks.domain.Trend;
import io.microcks.repository.TestConformanceMetricRepository;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test case for MetricsService test conformance trend recording.
 * @author laurent
 */
class MetricsServiceTest {

   private final TestConformanceMetricRepository metricRepository = Mockito.mock(TestConformanceMetricRepository.class);

   private MetricsService newService() throws Exception {
      MetricsService service = new MetricsService();
      setField(service, "metricRepository", metricRepository);
      setField(service, "testConformanceTrendSize", 3);
      setField(service, "testConformanceTrendHistorySize", 10);
      return service;
   }

   private static void setField(Object target, String name, Object value) throws Exception {
      Field field = target.getClass().getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   @Test
   void updateTestConformanceMetricOnTestResultRecordsScoreOnFirstCall() throws Exception {
      TestConformanceMetric metric = new TestConformanceMetric();
      metric.setServiceId("service-1");
      metric.setMaxPossibleScore(100.0);
      Mockito.when(metricRepository.findByServiceId("service-1")).thenReturn(metric);

      TestResult testResult = new TestResult();
      testResult.setServiceId("service-1");
      testResult.setSuccess(true);

      newService().updateTestConformanceMetricOnTestResult(testResult);

      assertEquals(1, metric.getLatestScores().size());
   }

   @Test
   void updateTestConformanceMetricOnTestResultComputesTrendFromExistingHistory() throws Exception {
      TestConformanceMetric metric = new TestConformanceMetric();
      metric.setServiceId("service-2");
      metric.setMaxPossibleScore(100.0);
      Map<String, Double> history = new LinkedHashMap<>();
      history.put("20260101", 100.0);
      history.put("20260102", 100.0);
      metric.setLatestScores(history);
      Mockito.when(metricRepository.findByServiceId("service-2")).thenReturn(metric);

      TestResult testResult = new TestResult();
      testResult.setServiceId("service-2");
      testResult.setSuccess(true);

      newService().updateTestConformanceMetricOnTestResult(testResult);

      assertEquals(Trend.STABLE, metric.getLatestTrend());
      assertEquals(3, metric.getLatestScores().size());
   }
}
