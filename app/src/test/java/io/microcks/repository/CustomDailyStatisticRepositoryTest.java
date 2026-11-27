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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for CustomDailyStatisticRepository class.
 * @author laurent
 */
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class CustomDailyStatisticRepositoryTest {

   @Autowired
   DailyStatisticRepository repository;

   @BeforeEach
   public void setUp() {
      // Create a bunch of statistics...
      DailyStatistic stat = new DailyStatistic();
      stat.setDay("20140930");
      stat.setServiceName("TestService1");
      stat.setServiceVersion("1.0");
      stat.setDailyCount(2);
      stat.setHourlyCount(initializeHourlyMap());
      repository.save(stat);
      // with same name and different version ...
      stat = new DailyStatistic();
      stat.setDay("20140930");
      stat.setServiceName("TestService1");
      stat.setServiceVersion("1.2");
      stat.setDailyCount(2);
      stat.setHourlyCount(initializeHourlyMap());
      repository.save(stat);
   }

   @Test
   void testAggregateDailyStatistics() {
      DailyStatistic stat = repository.aggregateDailyStatistics("20140930");
      assertNotNull(stat);
      assertEquals("20140930", stat.getDay());
      // 2 documents with dailyCount=2 each → sum = 4
      assertEquals(4, stat.getDailyCount());
      // Verify hourlyCount map is populated with 24 entries
      assertNotNull(stat.getHourlyCount());
      assertEquals(24, stat.getHourlyCount().size());
      // All hourly values should be 0 (since setUp initializes them to 0)
      for (int i = 0; i < 24; i++) {
         assertEquals(0, stat.getHourlyCount().get(String.valueOf(i)));
      }
   }

   @Test
   void testAggregateDailyStatisticsWithHourlyCounts() {
      // Insert stats with non-zero hourly counts for a different day.
      DailyStatistic stat = new DailyStatistic();
      stat.setDay("20141001");
      stat.setServiceName("ServiceA");
      stat.setServiceVersion("1.0");
      stat.setDailyCount(10);
      Map<String, Integer> hourly = initializeHourlyMap();
      hourly.put("9", 5);
      hourly.put("14", 3);
      stat.setHourlyCount(hourly);
      repository.save(stat);

      stat = new DailyStatistic();
      stat.setDay("20141001");
      stat.setServiceName("ServiceB");
      stat.setServiceVersion("2.0");
      stat.setDailyCount(20);
      hourly = initializeHourlyMap();
      hourly.put("9", 7);
      hourly.put("14", 2);
      stat.setHourlyCount(hourly);
      repository.save(stat);

      // Aggregate and verify cross-service summation.
      DailyStatistic result = repository.aggregateDailyStatistics("20141001");
      assertNotNull(result);
      assertEquals("20141001", result.getDay());
      assertEquals(30, result.getDailyCount());
      assertEquals(12, result.getHourlyCount().get("9"));
      assertEquals(5, result.getHourlyCount().get("14"));
      assertEquals(0, result.getHourlyCount().get("0"));
   }

   @Test
   void testAggregateDailyStatisticsNoData() {
      DailyStatistic stat = repository.aggregateDailyStatistics("20991231");
      assertNotNull(stat);
      assertEquals("20991231", stat.getDay());
      assertEquals(0, stat.getDailyCount());
   }

   private Map<String, Integer> initializeHourlyMap() {
      Map<String, Integer> result = new HashMap<String, Integer>(24);
      for (int i = 0; i < 24; i++) {
         result.put(String.valueOf(i), 0);
      }
      return result;
   }
}
