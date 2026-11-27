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
package io.microcks.listener;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.DailyStatistic;
import io.microcks.event.MockInvocationEvent;
import io.microcks.repository.DailyStatisticRepository;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test case for DailyStatisticsFeeder class.
 * @author laurent
 */
@QuarkusTest
class DailyStatisticsFeederTest {

   @Inject
   DailyStatisticsFeeder feeder;

   @Inject
   DailyStatisticRepository statisticsRepository;

   @Test
   void testOnApplicationEvent() {
      Calendar today = Calendar.getInstance();
      MockInvocationEvent event = new MockInvocationEvent("TestService1", "1.0", "123456789", today.getTime(), 100);

      // Fire event a first time.
      feeder.onApplicationEvent(event);

      SimpleDateFormat formater = new SimpleDateFormat("yyyyMMdd");
      String day = formater.format(today.getTime());
      feeder.flushToDatabase();
      DailyStatistic stat = statisticsRepository.findByDayAndServiceNameAndServiceVersion(day, "TestService1", "1.0")
            .get(0);
      assertNotNull(stat);
      assertNotNull(stat.getId());
      assertEquals(day, stat.getDay());
      assertEquals("TestService1", stat.getServiceName());
      assertEquals("1.0", stat.getServiceVersion());
      assertEquals(1, stat.getDailyCount());
      assertEquals(1, stat.getHourlyCount().get(String.valueOf(today.get(Calendar.HOUR_OF_DAY))));

      // Fire event a second time.
      feeder.onApplicationEvent(event);
      feeder.flushToDatabase();
      stat = statisticsRepository.findByDayAndServiceNameAndServiceVersion(day, "TestService1", "1.0").get(0);
      assertNotNull(stat);
      assertNotNull(stat.getId());
      assertEquals(day, stat.getDay());
      assertEquals("TestService1", stat.getServiceName());
      assertEquals("1.0", stat.getServiceVersion());
      assertEquals(2, stat.getDailyCount());
      assertEquals(2, stat.getHourlyCount().get(String.valueOf(today.get(Calendar.HOUR_OF_DAY))));
   }
}
