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
package io.github.microcks.service;

import io.github.microcks.domain.TestConformanceMetric;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.Trend;
import io.github.microcks.repository.TestConformanceMetricRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

   @Mock
   private TestConformanceMetricRepository metricRepository;

   private MetricsService newService() {
      MetricsService service = new MetricsService();
      ReflectionTestUtils.setField(service, "metricRepository", metricRepository);
      ReflectionTestUtils.setField(service, "testConformanceTrendSize", 3);
      ReflectionTestUtils.setField(service, "testConformanceTrendHistorySize", 10);
      return service;
   }

   @Test
   void updateTestConformanceMetricOnTestResultRecordsScoreOnFirstCall() {
      TestConformanceMetric metric = new TestConformanceMetric();
      metric.setServiceId("service-1");
      metric.setMaxPossibleScore(100.0);
      when(metricRepository.findByServiceId("service-1")).thenReturn(metric);

      TestResult testResult = new TestResult();
      testResult.setServiceId("service-1");
      testResult.setSuccess(true);

      newService().updateTestConformanceMetricOnTestResult(testResult);

      assertEquals(1, metric.getLatestScores().size());
   }

   @Test
   void updateTestConformanceMetricOnTestResultComputesTrendFromExistingHistory() {
      TestConformanceMetric metric = new TestConformanceMetric();
      metric.setServiceId("service-2");
      metric.setMaxPossibleScore(100.0);
      Map<String, Double> history = new LinkedHashMap<>();
      history.put("20260101", 100.0);
      history.put("20260102", 100.0);
      metric.setLatestScores(history);
      when(metricRepository.findByServiceId("service-2")).thenReturn(metric);

      TestResult testResult = new TestResult();
      testResult.setServiceId("service-2");
      testResult.setSuccess(true);

      newService().updateTestConformanceMetricOnTestResult(testResult);

      assertEquals(Trend.STABLE, metric.getLatestTrend());
      assertEquals(3, metric.getLatestScores().size());
   }
}
