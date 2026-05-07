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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.springframework.data.domain.Sort.Direction.ASC;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * Implementation of CustomDailyStatisticRepository.
 * @author laurent
 */
public class DailyStatisticRepositoryImpl implements CustomDailyStatisticRepository {

   private static final String DAILY_COUNT_FIELD = "dailyCount";

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(DailyStatisticRepositoryImpl.class);

   private MongoTemplate template;

   /**
    * @param template The MongoTemplate
    */
   public DailyStatisticRepositoryImpl(MongoTemplate template) {
      this.template = template;
   }

   @Override
   public void incrementDailyStatistic(String day, String serviceName, String serviceVersion, String hourKey,
         String minuteKey, int count) {
      // Build a query to select specific object within collection.
      Query query = new Query(
            Criteria.where("day").is(day).and("serviceName").is(serviceName).and("serviceVersion").is(serviceVersion));

      // Build update to increment the 3 fields.
      Update update = new Update().inc(DAILY_COUNT_FIELD, count).inc("hourlyCount." + hourKey, count)
            .inc("minuteCount." + minuteKey, count);

      // Do an upsert with find and modify.
      template.findAndModify(query, update, DailyStatistic.class);
   }

   @Override
   public DailyStatistic aggregateDailyStatistics(String day) {
      // Build aggregation pipeline to sum dailyCount and hourlyCount across all services.
      // The $group stage sums each hourly bucket into temporary aliases (h0..h23).
      GroupOperation groupOp = group("day").sum(DAILY_COUNT_FIELD).as(DAILY_COUNT_FIELD);
      for (int i = 0; i < 24; i++) {
         groupOp = groupOp.sum("hourlyCount." + i).as("h" + i);
      }

      Aggregation aggregation = newAggregation(match(Criteria.where("day").is(day)), groupOp);

      // Deserialize to Document and build the DailyStatistic
      // reconstruct the hourlyCount Map<String, Integer> from the h0..h23 aliases.
      AggregationResults<Document> results = template.aggregate(aggregation, DailyStatistic.class, Document.class);
      Document doc = results.getUniqueMappedResult();

      if (doc != null) {
         DailyStatistic result = new DailyStatistic();
         result.setDay(doc.getString("_id"));
         result.setDailyCount(doc.get(DAILY_COUNT_FIELD, Number.class).longValue());

         Map<String, Integer> hourlyCount = new HashMap<>(24);
         for (int i = 0; i < 24; i++) {
            Number val = doc.get("h" + i, Number.class);
            hourlyCount.put(String.valueOf(i), val != null ? val.intValue() : 0);
         }
         result.setHourlyCount(hourlyCount);

         if (log.isDebugEnabled()) {
            log.debug("aggregateDailyStatistics aggregation for day {}", day);
            log.debug("aggregateDailyStatistics aggregation result dailyCount: {}", result.getDailyCount());
         }

         return result;
      }

      // Build and return an empty object otherwise.
      DailyStatistic statistic = new DailyStatistic();
      statistic.setDay(day);
      statistic.setDailyCount(0);
      return statistic;
   }

   @Override
   public List<InvocationCount> aggregateDailyStatistics(String afterday, String beforeday) {
      // Build a query to pre-select the statistics that will be aggregated.
      Aggregation aggregation = newAggregation(match(Criteria.where("day").gte(afterday).lte(beforeday)),
            group("day").sum(DAILY_COUNT_FIELD).as("number"), project("number").and("day").previousOperation(),
            sort(ASC, "day"));
      AggregationResults<InvocationCount> results = template.aggregate(aggregation, DailyStatistic.class,
            InvocationCount.class);
      return results.getMappedResults();
   }

   @Override
   public List<DailyStatistic> findTopStatistics(String day, int limit) {
      // Build a query selecting and sorting / limiting.
      Query query = new Query(Criteria.where("day").is(day)).with(Sort.by(new Order(Direction.DESC, DAILY_COUNT_FIELD)))
            .limit(limit);

      return template.find(query, DailyStatistic.class);
   }
}
