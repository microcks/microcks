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
package io.github.microcks.listener;

import io.github.microcks.domain.DailyStatistic;
import io.github.microcks.event.MockInvocationEvent;
import io.github.microcks.repository.DailyStatisticRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application event listener that updates daily statistics on incoming event.
 * @author laurent
 */
@Component
public class DailyStatisticsFeeder implements ApplicationListener<MockInvocationEvent> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(DailyStatisticsFeeder.class);

   private final DailyStatisticRepository statisticsRepository;

   /**
    * Build a DailyStatisticsFeeder with mandatory dependencies.
    * @param statisticsRepository The repository to access statictics.
    */
   public DailyStatisticsFeeder(DailyStatisticRepository statisticsRepository) {
      this.statisticsRepository = statisticsRepository;
   }

   @Override
   @Async
   public void onApplicationEvent(MockInvocationEvent event) {
      log.debug("Received a MockInvocationEvent on {} - v{}", event.getServiceName(), event.getServiceVersion());

      // Compute day string representation.
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(event.getInvocationTimestamp());

      // Computing keys based on invocation date.
      int month = calendar.get(Calendar.MONTH) + 1;
      String monthStr = (month < 10 ? "0" : "") + month;
      int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
      String dayOfMonthStr = (dayOfMonth < 10 ? "0" : "") + dayOfMonth;

      String day = calendar.get(Calendar.YEAR) + monthStr + dayOfMonthStr;
      String hourKey = String.valueOf(calendar.get(Calendar.HOUR_OF_DAY));
      String minuteKey = String.valueOf((60 * calendar.get(Calendar.HOUR_OF_DAY)) + calendar.get(Calendar.MINUTE));
      if (log.isDebugEnabled()) {
         log.debug("hourKey for statistic is {}", hourKey);
         log.debug("minuteKey for statistic is {}", minuteKey);
      }

      // First check if there's a statistic document for invocation day.
      DailyStatistic statistic = null;
      List<DailyStatistic> statistics = statisticsRepository.findByDayAndServiceNameAndServiceVersion(day,
            event.getServiceName(), event.getServiceVersion());
      if (!statistics.isEmpty()) {
         statistic = statistics.get(0);
      }

      if (statistic == null) {
         // No statistic's yet...
         log.debug("There's no statistics for {} yet. Create one.", day);
         // Initialize a new 0 filled structure.
         statistic = new DailyStatistic();
         statistic.setDay(day);
         statistic.setServiceName(event.getServiceName());
         statistic.setServiceVersion(event.getServiceVersion());
         statistic.setHourlyCount(initializeHourlyMap());
         statistic.setMinuteCount(initializeMinuteMap());
         // Now set first values before saving.
         statistic.setDailyCount(1);
         statistic.getHourlyCount().put(hourKey, 1);
         statistic.getMinuteCount().put(minuteKey, 1);
         statisticsRepository.save(statistic);
      } else {
         // Already a statistic document for this day, increment fields.
         log.debug("Found an existing statistic document for {}", day);
         statisticsRepository.incrementDailyStatistic(day, event.getServiceName(), event.getServiceVersion(), hourKey,
               minuteKey);
      }

      log.debug("Processing of MockInvocationEvent done !");
   }

   private Map<String, Integer> initializeHourlyMap() {
      Map<String, Integer> result = new HashMap<>(24);
      for (int i = 0; i < 24; i++) {
         result.put(String.valueOf(i), 0);
      }
      return result;
   }

   private Map<String, Integer> initializeMinuteMap() {
      Map<String, Integer> result = new HashMap<>(24 * 60);
      for (int i = 0; i < 24 * 60; i++) {
         result.put(String.valueOf(i), 0);
      }
      return result;
   }
}
