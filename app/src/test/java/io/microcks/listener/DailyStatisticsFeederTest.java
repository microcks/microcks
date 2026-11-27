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
import io.github.microcks.repository.RepositoryTestsConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test case for DailyStatisticsFeeder class.
 * @author laurent
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringJUnitConfig(classes = { RepositoryTestsConfiguration.class, ListenerTestsConfiguration.class })
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class DailyStatisticsFeederTest {

   @Autowired
   DailyStatisticsFeeder feeder;

   @Autowired
   DailyStatisticRepository statisticsRepository;

   @Test
   void testOnApplicationEvent() {
      Calendar today = Calendar.getInstance();
      MockInvocationEvent event = new MockInvocationEvent(this, "TestService1", "1.0", "123456789", today.getTime(),
            100);

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
