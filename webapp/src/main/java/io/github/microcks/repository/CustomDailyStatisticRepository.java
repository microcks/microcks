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

import io.github.microcks.domain.DailyStatistic;

import java.util.List;

/**
 * Custom repository interface for DailyStatistic domain objects.
 * @author laurent
 */
public interface CustomDailyStatisticRepository {

   void incrementDailyStatistic(String day, String serviceName, String serviceVersion, String hourKey, String minuteKey,
         int count);

   DailyStatistic aggregateDailyStatistics(String day);

   List<InvocationCount> aggregateDailyStatistics(String afterday, String beforeday);

   List<DailyStatistic> findTopStatistics(String day, int limit);

   class InvocationCount {
      String day;
      Long number;

      public String getDay() {
         return day;
      }

      public void setDay(String day) {
         this.day = day;
      }

      public Long getNumber() {
         return number;
      }

      public void setNumber(Long number) {
         this.number = number;
      }
   }
}
