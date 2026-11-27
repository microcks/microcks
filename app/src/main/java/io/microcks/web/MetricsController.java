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
package io.github.microcks.web;

import io.github.microcks.domain.DailyStatistic;
import io.github.microcks.domain.TestConformanceMetric;
import io.github.microcks.domain.WeightedMetricValue;
import io.github.microcks.listener.DailyStatisticsFeeder;
import io.github.microcks.listener.StatisticsFlusher;
import io.github.microcks.repository.CustomDailyStatisticRepository;
import io.github.microcks.repository.DailyStatisticRepository;
import io.github.microcks.repository.TestConformanceMetricRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.util.SafeLogger;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A REST controller for managing metrics consultation API endpoints.
 * @author laurent
 */
@RestController
@RequestMapping("/api")
public class MetricsController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(MetricsController.class);

   private final DailyStatisticRepository invocationsRepository;
   private final TestConformanceMetricRepository metricRepository;
   private final TestResultRepository testResultRepository;
   private final StatisticsFlusher statisticsFlusher;

   /**
    * Build a new MetricsController with its required dependencies.
    * @param invocationsRepository The repository for daily statistics.
    * @param metricRepository      The repository for test conformance metrics.
    * @param testResultRepository  The repository for test results.
    * @param statisticsFlusher     The statistics flusher.
    */
   public MetricsController(DailyStatisticRepository invocationsRepository,
         TestConformanceMetricRepository metricRepository, TestResultRepository testResultRepository,
         StatisticsFlusher statisticsFlusher) {
      this.invocationsRepository = invocationsRepository;
      this.metricRepository = metricRepository;
      this.testResultRepository = testResultRepository;
      this.statisticsFlusher = statisticsFlusher;
   }

   @GetMapping(value = "/metrics/invocations/global")
   public DailyStatistic getInvocationStatGlobal(@RequestParam(value = "day", required = false) String day) {
      // Ensure we got accurate statistics by flushing the feeder to database.
      statisticsFlusher.flushToDatabase();
      // Now process the request.
      log.debug("Getting invocations stats for day {}", day);
      if (day == null) {
         day = getTodaysDate();
      }
      return invocationsRepository.aggregateDailyStatistics(day);
   }

   @GetMapping(value = "/metrics/invocations/top")
   public List<DailyStatistic> getInvocationTopStats(@RequestParam(value = "day", required = false) String day,
         @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
      // Ensure we got accurate statistics by flushing the feeder to database.
      statisticsFlusher.flushToDatabase();
      // Now process the request.
      log.debug("Getting top {} invocations stats for day {}", limit, day);
      if (day == null) {
         day = getTodaysDate();
      }
      return invocationsRepository.findTopStatistics(day, limit);
   }

   @GetMapping(value = "/metrics/invocations/{service}/{version}")
   public DailyStatistic getInvocationStatForService(@PathVariable("service") String serviceName,
         @PathVariable("version") String serviceVersion, @RequestParam(value = "day", required = false) String day) {
      // Ensure we got accurate statistics by flushing the feeder to database.
      statisticsFlusher.flushToDatabase();
      // Now process the request.
      log.debug("Getting invocations stats for service [{}, {}] and day {}", serviceName, serviceVersion, day);
      if (day == null) {
         day = getTodaysDate();
      }
      List<DailyStatistic> statistics = invocationsRepository.findByDayAndServiceNameAndServiceVersion(day, serviceName,
            serviceVersion);
      if (!statistics.isEmpty()) {
         return statistics.get(0);
      }
      return null;
   }

   @GetMapping(value = "/metrics/invocations/global/latest")
   public Map<String, Long> getLatestInvocationStatGlobal(
         @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
      // Ensure we got accurate statistics by flushing the feeder to database.
      statisticsFlusher.flushToDatabase();
      // Now process the request.
      log.debug("Getting invocations stats for last {} days", limit);

      String day = getTodaysDate();
      String dayBefore = getPastDateAsString(limit);
      Map<String, Long> invocations = new TreeMap<>();
      List<CustomDailyStatisticRepository.InvocationCount> results = invocationsRepository
            .aggregateDailyStatistics(dayBefore, day);
      for (CustomDailyStatisticRepository.InvocationCount count : results) {
         invocations.put(count.getDay(), count.getNumber());
      }
      return invocations;
   }

   @GetMapping(value = "/metrics/conformance/aggregate")
   public List<WeightedMetricValue> getAggregatedTestCoverageMetrics() {
      log.debug("Computing TestConformanceMetric aggregates");

      return metricRepository.aggregateTestConformanceMetric();
   }

   @GetMapping(value = "/metrics/conformance/service/{serviceId:.+}")
   public TestConformanceMetric getTestConformanceMetric(@PathVariable("serviceId") String serviceId) {
      log.debug("Retrieving TestConformanceMetric for service with id {}", serviceId);

      return metricRepository.findByServiceId(serviceId);
   }

   @GetMapping(value = "/metrics/tests/latest")
   public List<TestResultSummary> getLatestTestResults(
         @RequestParam(value = "limit", required = false, defaultValue = "7") Integer limit) {
      log.debug("Getting tests trend for last {} days", limit);

      // Compute last date and retrieve test results.
      Date lastDate = getPastDate(limit);
      return testResultRepository.findAllWithTestDateAfter(lastDate).stream()
            .map(res -> new TestResultSummary(res.getId(), res.getTestDate(), res.getServiceId(), res.isSuccess()))
            .collect(Collectors.toList());
   }

   private String getTodaysDate() {
      Calendar calendar = Calendar.getInstance();
      int month = calendar.get(Calendar.MONTH) + 1;
      String monthStr = (month < 10 ? "0" : "") + month;
      int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      String dayOfMonthStr = (dayOfMonth < 10 ? "0" : "") + dayOfMonth;
      return calendar.get(Calendar.YEAR) + monthStr + dayOfMonthStr;
   }

   private Date getPastDate(Integer daysBack) {
      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.DAY_OF_YEAR, -daysBack);
      return calendar.getTime();
   }

   private String getPastDateAsString(Integer daysBack) {
      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.DAY_OF_YEAR, -daysBack);
      int month = calendar.get(Calendar.MONTH) + 1;
      String monthStr = (month < 10 ? "0" : "") + (month);
      int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      String dayOfMonthStr = (dayOfMonth < 10 ? "0" : "") + (dayOfMonth);
      return calendar.get(Calendar.YEAR) + monthStr + dayOfMonthStr;
   }

   public static class TestResultSummary {
      String id;
      Date testDate;
      String serviceId;
      boolean success;

      public TestResultSummary(String id, Date testDate, String serviceId, boolean success) {
         this.id = id;
         this.testDate = testDate;
         this.serviceId = serviceId;
         this.success = success;
      }

      public String getId() {
         return id;
      }

      public Date getTestDate() {
         return testDate;
      }

      public String getServiceId() {
         return serviceId;
      }

      public boolean isSuccess() {
         return success;
      }
   }
}
