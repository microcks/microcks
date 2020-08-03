/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.web;

import io.github.microcks.domain.DailyStatistic;
import io.github.microcks.repository.CustomDailyStatisticRepository;
import io.github.microcks.repository.DailyStatisticRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A Rest controller for API defined on invocation stats.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class InvocationController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(InvocationController.class);

   @Autowired
   private DailyStatisticRepository repository;

   @RequestMapping(value = "/invocations/global", method = RequestMethod.GET)
   public DailyStatistic getInvocationStatGlobal(@RequestParam(value="day", required=false) String day) {
      log.debug("Getting invocations stats for day {}", day);
      if (day == null) {
         day = getTodaysDate();
      }
      return repository.aggregateDailyStatistics(day);
   }

   @RequestMapping(value = "/invocations/top", method = RequestMethod.GET)
   public List<DailyStatistic> getInvocationTopStats(
         @RequestParam(value="day", required=false) String day,
         @RequestParam(value="limit", required=false, defaultValue="20") Integer limit
      ) {
      log.debug("Getting top {} invocations stats for day {}", limit, day);
      if (day == null) {
         day = getTodaysDate();
      }
      return repository.findTopStatistics(day, limit);
   }

   @RequestMapping(value = "/invocations/{service}/{version}", method = RequestMethod.GET)
   public DailyStatistic getInvocationStatForService(
        @PathVariable("service") String serviceName,
        @PathVariable("version") String serviceVersion,
        @RequestParam(value="day", required=false) String day
      ) {
      log.debug("Getting invocations stats for service [{}, {}] and day {}", serviceName, serviceVersion, day);
      if (day == null) {
         day = getTodaysDate();
      }
      return repository.findByDayAndServiceNameAndServiceVersion(day, serviceName, serviceVersion);
   }

   @RequestMapping(value = "/invocations/global/last", method = RequestMethod.GET)
   public Map<String, Long> getLastInvocationStatGlobal(@RequestParam(value="limit", required=false, defaultValue="20") Integer limit){
      log.debug("Getting invocations stats for last {} days", limit);

      String day = getTodaysDate();
      String dayBefore = getPastDate(limit);
      Map<String, Long> invocations = new TreeMap<>();
      List<CustomDailyStatisticRepository.InvocationCount> results = repository.aggregateDailyStatistics(dayBefore, day);
      for (CustomDailyStatisticRepository.InvocationCount count : results) {
         invocations.put(count.getDay(), count.getNumber());
      }
      return invocations;
   }

   private String getTodaysDate(){
      Calendar calendar = Calendar.getInstance();
      int month = calendar.get(Calendar.MONTH) + 1;
      String monthStr = (month<10 ? "0" : "") + String.valueOf(month);
      int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      String dayOfMonthStr = (dayOfMonth<10 ? "0" : "") + String.valueOf(dayOfMonth);
      return String.valueOf(calendar.get(Calendar.YEAR)) + monthStr + dayOfMonthStr;
   }

   private String getPastDate(Integer daysback) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DAY_OF_YEAR, -daysback);
    int month = calendar.get(Calendar.MONTH) + 1;
    String monthStr = (month<10 ? "0" : "") + String.valueOf(month);
    int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
    String dayOfMonthStr = (dayOfMonth<10 ? "0" : "") + String.valueOf(dayOfMonth);
    return String.valueOf(calendar.get(Calendar.YEAR)) + monthStr + dayOfMonthStr;
 }
}
