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
 * Domain object representing the test conformance metrics (current and configurable depth history) for a Service
 * objects. If classification is allowed then aggregation can be realized using the {@code aggregationLabelValue} field.
 * @author laurent
 */
public class TestConformanceMetric {

   @Id
   private String id;
   private String serviceId;
   private String aggregationLabelValue;

   private double maxPossibleScore;
   private double currentScore;
   private String lastUpdateDay;

   private Trend latestTrend = Trend.STABLE;
   private Map<String, Double> latestScores = new HashMap<>();

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getServiceId() {
      return serviceId;
   }

   public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
   }

   public String getAggregationLabelValue() {
      return aggregationLabelValue;
   }

   public void setAggregationLabelValue(String aggregationLabelValue) {
      this.aggregationLabelValue = aggregationLabelValue;
   }

   public double getMaxPossibleScore() {
      return maxPossibleScore;
   }

   public void setMaxPossibleScore(double maxPossibleScore) {
      this.maxPossibleScore = maxPossibleScore;
   }

   public double getCurrentScore() {
      return currentScore;
   }

   public void setCurrentScore(double currentScore) {
      this.currentScore = currentScore;
   }

   public String getLastUpdateDay() {
      return lastUpdateDay;
   }

   public void setLastUpdateDay(String lastUpdateDay) {
      this.lastUpdateDay = lastUpdateDay;
   }

   public Trend getLatestTrend() {
      return latestTrend;
   }

   public void setLatestTrend(Trend latestTrend) {
      this.latestTrend = latestTrend;
   }

   public Map<String, Double> getLatestScores() {
      return latestScores;
   }

   public void setLatestScores(Map<String, Double> latestScores) {
      this.latestScores = latestScores;
   }
}
