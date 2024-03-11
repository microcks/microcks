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
import io.github.microcks.repository.CustomDailyStatisticRepository;
import io.github.microcks.repository.DailyStatisticRepository;
import io.github.microcks.repository.TestConformanceMetricRepository;
import io.github.microcks.repository.TestResultRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A REST controller for managing metrics consultation API endpoints.
 * @author laurent
 */
@RestController
@RequestMapping("/api")
public class MetricsController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(MetricsController.class);

   @Autowired
   private DailyStatisticRepository invocationsRepository;

   @Autowired
   TestConformanceMetricRepository metricRepository;

   @Autowired
   TestResultRepository testResultRepository;

   @RequestMapping(value = "/metrics/invocations/global", method = RequestMethod.GET)
   public DailyStatistic getInvocationStatGlobal(@RequestParam(value = "day", required = false) String day) {
      log.debug("Getting invocations stats for day {}", day);
      if (day == null) {
         day = getTodaysDate();
      }
      return invocationsRepository.aggregateDailyStatistics(day);
   }

   @RequestMapping(value = "/metrics/invocations/top", method = RequestMethod.GET)
   public List<DailyStatistic> getInvocationTopStats(@RequestParam(value = "day", required = false) String day,
         @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
      log.debug("Getting top {} invocations stats for day {}", limit, day);
      if (day == null) {
         day = getTodaysDate();
      }
      return invocationsRepository.findTopStatistics(day, limit);
   }

   @RequestMapping(value = "/metrics/invocations/{service}/{version}", method = RequestMethod.GET)
   public DailyStatistic getInvocationStatForService(@PathVariable("service") String serviceName,
         @PathVariable("version") String serviceVersion, @RequestParam(value = "day", required = false) String day) {
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

   @RequestMapping(value = "/metrics/invocations/global/latest", method = RequestMethod.GET)
   public Map<String, Long> getLatestInvocationStatGlobal(
         @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
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


   @RequestMapping(value = "/metrics/conformance/aggregate", method = RequestMethod.GET)
   public List<WeightedMetricValue> getAggregatedTestCoverageMetrics() {
      log.debug("Computing TestConformanceMetric aggregates");

      return metricRepository.aggregateTestConformanceMetric();
   }

   @RequestMapping(value = "/metrics/conformance/service/{serviceId:.+}", method = RequestMethod.GET)
   public TestConformanceMetric getTestConformanceMetric(@PathVariable("serviceId") String serviceId) {
      log.debug("Retrieving TestConformanceMetric for service with id {}", serviceId);

      return metricRepository.findByServiceId(serviceId);
   }

   @RequestMapping(value = "/metrics/tests/latest", method = RequestMethod.GET)
   public List<TestResultSummary> getLatestTestResults(
         @RequestParam(value = "limit", required = false, defaultValue = "7") Integer limit) {
      log.debug("Getting tests trend for last {} days", limit);

      // Compute last date and retrieve test results.
      Date lastDate = getPastDate(limit);
      List<TestResultSummary> summaries = testResultRepository.findAllWithTestDateAfter(lastDate).stream()
            .map(res -> new TestResultSummary(res.getId(), res.getTestDate(), res.getServiceId(), res.isSuccess()))
            .collect(Collectors.toList());
      return summaries;
   }

   private String getTodaysDate() {
      Calendar calendar = Calendar.getInstance();
      int month = calendar.get(Calendar.MONTH) + 1;
      String monthStr = (month < 10 ? "0" : "") + String.valueOf(month);
      int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      String dayOfMonthStr = (dayOfMonth < 10 ? "0" : "") + String.valueOf(dayOfMonth);
      return String.valueOf(calendar.get(Calendar.YEAR)) + monthStr + dayOfMonthStr;
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

   class TestResultSummary {
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
