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
package io.github.microcks.domain;

import org.springframework.data.annotation.Id;

import java.util.HashMap;
import java.util.Map;

/**
 * Domain objects representing daily invocation stats of mocks served by Microcks.
 * @author laurent
 */
public class DailyStatistic {

   @Id
   private String id;
   private String day;
   private String serviceName;
   private String serviceVersion;
   private long dailyCount;

   private Map<String, Integer> hourlyCount = new HashMap<>();
   private Map<String, Integer> minuteCount = new HashMap<>();

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getDay() {
      return day;
   }

   public void setDay(String day) {
      this.day = day;
   }

   public String getServiceName() {
      return serviceName;
   }

   public void setServiceName(String serviceName) {
      this.serviceName = serviceName;
   }

   public String getServiceVersion() {
      return serviceVersion;
   }

   public void setServiceVersion(String serviceVersion) {
      this.serviceVersion = serviceVersion;
   }

   public long getDailyCount() {
      return dailyCount;
   }

   public void setDailyCount(long dailyCount) {
      this.dailyCount = dailyCount;
   }

   public Map<String, Integer> getHourlyCount() {
      return hourlyCount;
   }

   public void setHourlyCount(Map<String, Integer> hourlyCount) {
      this.hourlyCount = hourlyCount;
   }

   public Map<String, Integer> getMinuteCount() {
      return minuteCount;
   }

   public void setMinuteCount(Map<String, Integer> minuteCount) {
      this.minuteCount = minuteCount;
   }
}
