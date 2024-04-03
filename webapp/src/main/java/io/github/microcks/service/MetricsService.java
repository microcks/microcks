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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.TestCaseResult;
import io.github.microcks.domain.TestConformanceMetric;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestStepResult;
import io.github.microcks.domain.Trend;
import io.github.microcks.repository.TestConformanceMetricRepository;
import io.github.microcks.util.IdBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service bean for common processing around metrics.
 * 
 * @author laurent
 */
@org.springframework.stereotype.Service
@PropertySources({ @PropertySource("features.properties"),
      @PropertySource(value = "file:/deployments/config/features.properties", ignoreResourceNotFound = true),
      @PropertySource("application.properties") })
public class MetricsService {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(MetricsService.class);

   @Autowired
   private MessageService messageService;

   @Autowired
   private TestConformanceMetricRepository metricRepository;

   @Value("${features.feature.repository-filter.label-key}")
   private String filterLabelKey;

   @Value("#{new Integer('${test-conformance.trend-size:3}')}")
   private Integer testConformanceTrendSize;

   @Value("#{new Integer('${test-conformance.trend-history-size:10}')}")
   private Integer testConformanceTrendHistorySize;

   /**
    * Configure a TestConformanceMetric object for a given Service.
    * 
    * @param service The Service to configure coverage metrics for.
    */
   public void configureTestConformanceMetric(Service service) {
      TestConformanceMetric metric = metricRepository.findByServiceId(service.getId());
      if (metric == null) {
         log.debug("Creating a new TestCoverageMetric for Service '{}'", service.getId());
         metric = new TestConformanceMetric();
         metric.setServiceId(service.getId());
         metric.setLatestTrend(Trend.STABLE);
      }

      // Update max possible score and aggregation label on crate or change.
      metric.setMaxPossibleScore(computeMaxPossibleConformanceScore(service));
      if (filterLabelKey != null && service.getMetadata().getLabels() != null) {
         metric.setAggregationLabelValue(service.getMetadata().getLabels().get(filterLabelKey));
      }

      metricRepository.save(metric);
   }

   /**
    * Remove TestConformanceMetric associated to a given Service.
    * 
    * @param serviceId The identifier of Service to remove coverage metrics for.
    */
   public void removeTestConformanceMetric(String serviceId) {
      TestConformanceMetric metric = metricRepository.findByServiceId(serviceId);
      if (metric != null) {
         metricRepository.delete(metric);
      }
   }

   /**
    * Update the test conformance score when a test is completed.
    *
    * @param testResult The newly completed Test for a Service.
    */
   public void updateTestConformanceMetricOnTestResult(TestResult testResult) {
      TestConformanceMetric metric = metricRepository.findByServiceId(testResult.getServiceId());
      if (metric != null) {
         // Compute current score.
         double currentScore = 0;
         if (testResult.isSuccess()) {
            currentScore = metric.getMaxPossibleScore();
         } else {
            int totalSteps = 0;
            int totalSuccess = 0;
            for (TestCaseResult caseResult : testResult.getTestCaseResults()) {
               for (TestStepResult stepResult : caseResult.getTestStepResults()) {
                  if (stepResult.isSuccess()) {
                     totalSuccess++;
                  }
                  totalSteps++;
               }
            }
            currentScore = metric.getMaxPossibleScore() * (((double) totalSuccess) / ((double) totalSteps));
         }

         // Update entity with score and last update day.
         metric.setCurrentScore(currentScore);
         metric.setLastUpdateDay(getTodayAsString());

         // If we have enough history, compute a trend.
         if (metric.getLatestScores().size() >= testConformanceTrendSize - 1) {
            // Start sorting the history map in reverse order (last days measure first)
            List<Map.Entry<String, Double>> latestReversedScores = metric.getLatestScores().entrySet().stream()
                  .sorted(Map.Entry.comparingByKey(Comparator.reverseOrder())).collect(Collectors.toList());

            // Get history average and last n days average.
            double globalAvg = latestReversedScores.stream().collect(Collectors.averagingDouble(Map.Entry::getValue));

            double latestAvg = currentScore;
            for (int i = 0; i < testConformanceTrendSize - 1; i++) {
               latestAvg += latestReversedScores.get(i).getValue();
            }
            latestAvg = latestAvg / testConformanceTrendSize;

            // Compute a trend based on diff.
            double avgDiff = latestAvg - globalAvg;
            if (avgDiff >= 5) {
               metric.setLatestTrend(Trend.UP);
            } else if (avgDiff < 5 && avgDiff >= 0.2) {
               metric.setLatestTrend(Trend.LOW_UP);
            } else if (avgDiff < 0.2 && avgDiff > -0.2) {
               metric.setLatestTrend(Trend.STABLE);
            } else if (avgDiff <= -0.2 && avgDiff > -5) {
               metric.setLatestTrend(Trend.LOW_DOWN);
            } else if (avgDiff <= -5) {
               metric.setLatestTrend(Trend.DOWN);
            }

            // Remove all entries >9 to keep 10 entries.
            while (latestReversedScores.size() >= testConformanceTrendHistorySize) {
               Map.Entry<String, Double> lastEntry = latestReversedScores.get(latestReversedScores.size() - 1);
               metric.getLatestScores().remove(lastEntry.getKey());
               latestReversedScores.remove(latestReversedScores.size() - 1);
            }
            metric.getLatestScores().put(metric.getLastUpdateDay(), currentScore);
         }
         // Save updated metric.
         metricRepository.save(metric);
      }
   }

   /** Compute the max possible coverage for a Service. */
   private double computeMaxPossibleConformanceScore(Service service) {
      double maxScore = 0.00;
      double operationContrib = 100 / ((double) service.getOperations().size());

      for (Operation operation : service.getOperations()) {
         List exchanges;
         if (ServiceType.EVENT.equals(service.getType()) || ServiceType.GENERIC_EVENT.equals(service.getType())) {
            // If an event, we should explicitly retrieve event messages.
            exchanges = messageService.getEventByOperation(IdBuilder.buildOperationId(service, operation));
         } else {
            // Otherwise we have traditional request / response pairs.
            exchanges = messageService.getRequestResponseByOperation(IdBuilder.buildOperationId(service, operation));
         }
         if (exchanges != null) {
            if (exchanges.size() >= 2) {
               maxScore += operationContrib;
            } else if (exchanges.size() == 1) {
               maxScore += (operationContrib / 2);
            }
         }
      }
      return maxScore;
   }

   /** Return today's date as yyyyMMdd pattern. */
   private String getTodayAsString() {
      // Compute day string representation.
      Calendar calendar = Calendar.getInstance();
      int month = calendar.get(Calendar.MONTH) + 1;
      String monthStr = (month < 10 ? "0" : "") + String.valueOf(month);
      int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      String dayOfMonthStr = (dayOfMonth < 10 ? "0" : "") + String.valueOf(dayOfMonth);

      return calendar.get(Calendar.YEAR) + monthStr + dayOfMonthStr;
   }
}
