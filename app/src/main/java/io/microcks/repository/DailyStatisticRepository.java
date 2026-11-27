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
package io.microcks.repository;

import io.microcks.domain.DailyStatistic;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for {@code DailyStatistic} domain objects.
 * @author laurent
 */
@ApplicationScoped
public class DailyStatisticRepository implements ObjectIdRepository<DailyStatistic>, CustomDailyStatisticRepository {

   private static final Logger log = LoggerFactory.getLogger(DailyStatisticRepository.class);

   private static final String DAILY_COUNT_FIELD = "dailyCount";

   public List<DailyStatistic> findByDayAndServiceNameAndServiceVersion(String day, String serviceName,
         String serviceVersion) {
      return list("day = ?1 and serviceName = ?2 and serviceVersion = ?3", day, serviceName, serviceVersion);
   }

   @Override
   public void incrementDailyStatistic(String day, String serviceName, String serviceVersion, String hourKey,
         String minuteKey, int count) {
      // Atomic $inc operators are not expressible through Panache entity updates: go through the driver collection.
      mongoCollection().findOneAndUpdate(
            Filters.and(Filters.eq("day", day), Filters.eq("serviceName", serviceName),
                  Filters.eq("serviceVersion", serviceVersion)),
            Updates.combine(Updates.inc(DAILY_COUNT_FIELD, count), Updates.inc("hourlyCount." + hourKey, count),
                  Updates.inc("minuteCount." + minuteKey, count)));
   }

   @Override
   public DailyStatistic aggregateDailyStatistics(String day) {
      Document group = new Document("_id", "$day").append(DAILY_COUNT_FIELD,
            new Document("$sum", "$" + DAILY_COUNT_FIELD));
      for (int i = 0; i < 24; i++) {
         group.append("h" + i, new Document("$sum", "$hourlyCount." + i));
      }
      List<Document> pipeline = List.of(new Document("$match", new Document("day", day)),
            new Document("$group", group));
      Document doc = mongoCollection().aggregate(pipeline, Document.class).first();

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
      DailyStatistic statistic = new DailyStatistic();
      statistic.setDay(day);
      statistic.setDailyCount(0);
      return statistic;
   }

   @Override
   public List<InvocationCount> aggregateDailyStatistics(String afterday, String beforeday) {
      List<Document> pipeline = List.of(
            new Document("$match", new Document("day", new Document("$gte", afterday).append("$lte", beforeday))),
            new Document("$group",
                  new Document("_id", "$day").append("number", new Document("$sum", "$" + DAILY_COUNT_FIELD))),
            new Document("$sort", new Document("_id", 1)));
      List<InvocationCount> results = new ArrayList<>();
      for (Document doc : mongoCollection().aggregate(pipeline, Document.class)) {
         InvocationCount count = new InvocationCount();
         count.setDay(doc.getString("_id"));
         count.setNumber(doc.get("number", Number.class).longValue());
         results.add(count);
      }
      return results;
   }

   @Override
   public List<DailyStatistic> findTopStatistics(String day, int limit) {
      return find("day", Sort.descending(DAILY_COUNT_FIELD), day).page(0, limit).list();
   }
}
