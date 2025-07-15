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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.mapreduce.MapReduceResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Implementation of CustomDailyStatisticRepository.
 * @author laurent
 */
public class DailyStatisticRepositoryImpl implements CustomDailyStatisticRepository {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(DailyStatisticRepositoryImpl.class);

   @Autowired
   private MongoTemplate template;

   @Override
   public void incrementDailyStatistic(String day, String serviceName, String serviceVersion, String hourKey,
         String minuteKey, int count) {

      // Build a query to select specific object within collection.
      Query query = new Query(
            Criteria.where("day").is(day).and("serviceName").is(serviceName).and("serviceVersion").is(serviceVersion));

      // Other way to build a query using statically imported methods.
      //Query queryShort = query(where("day").is(day).and("serviceName").is(serviceName).and("serviceVersion").is(serviceVersion));

      // Build update to increment the 3 fields.
      Update update = new Update().inc("dailyCount", count).inc("hourlyCount." + hourKey, count)
            .inc("minuteCount." + minuteKey, count);

      // Do an upsert with find and modify.
      template.findAndModify(query, update, DailyStatistic.class);
   }

   @Override
   public DailyStatistic aggregateDailyStatistics(String day) {

      // Build a query to pre-select the statistics that will be aggregated.
      Query query = new Query(Criteria.where("day").is(day));

      // Execute a MapReduce command.
      MapReduceResults<WrappedDailyStatistic> results = template.mapReduce(query, "dailyStatistic",
            "classpath:mapDailyStatisticForADay.js", "classpath:reduceDailyStatisticForADay.js",
            WrappedDailyStatistic.class);

      // Output some debug messages.
      if (log.isDebugEnabled()) {
         log.debug("aggregateDailyStatistics mapReduce for day " + day);
         log.debug("aggregateDailyStatistics mapReduce result counts: " + results.getCounts());
         for (WrappedDailyStatistic wdt : results) {
            log.debug("aggregateDailyStatistics mapReduce result value: " + wdt.getValue());
         }
      }

      // We've got a result if we've got an output.
      if (results.getCounts().getOutputCount() > 0) {
         return results.iterator().next().getValue();
      }

      // Build and return an empty object otherwise?
      DailyStatistic statistic = new DailyStatistic();
      statistic.setDay(day);
      statistic.setDailyCount(0);
      return statistic;
   }

   @Override
   public List<InvocationCount> aggregateDailyStatistics(String afterday, String beforeday) {
      // Build a query to pre-select the statistics that will be aggregated.
      Aggregation aggregation = newAggregation(match(Criteria.where("day").gte(afterday).lte(beforeday)),
            group("day").sum("dailyCount").as("number"), project("number").and("day").previousOperation(),
            sort(ASC, "day"));
      AggregationResults<InvocationCount> results = template.aggregate(aggregation, DailyStatistic.class,
            InvocationCount.class);
      return results.getMappedResults();
   }

   @Override
   public List<DailyStatistic> findTopStatistics(String day, int limit) {
      // Build a query selecting and sorting / limiting.
      Query query = new Query(Criteria.where("day").is(day)).with(Sort.by(new Order(Direction.DESC, "dailyCount")))
            .limit(limit);

      return template.find(query, DailyStatistic.class);
   }


   /** Utility class used for wrapping a DailyStatistic object within MapReduce command results. */
   public class WrappedDailyStatistic {
      private String id;
      private DailyStatistic value;

      public String getId() {
         return id;
      }

      public void setId(String id) {
         this.id = id;
      }

      public DailyStatistic getValue() {
         return value;
      }

      public void setValue(DailyStatistic value) {
         this.value = value;
      }
   }
}
