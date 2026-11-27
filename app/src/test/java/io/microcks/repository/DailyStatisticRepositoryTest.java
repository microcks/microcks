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

import static org.junit.jupiter.api.Assertions.*;

import io.github.microcks.domain.DailyStatistic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Test case for DailyStatisticRepository class.
 * @author laurent
 */
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class DailyStatisticRepositoryTest {

   @Autowired
   DailyStatisticRepository repository;

   @BeforeEach
   public void setUp() {
      // Create a bunch of statistics...
      DailyStatistic stat = new DailyStatistic();
      stat.setDay("20140319");
      stat.setServiceName("TestService1");
      stat.setServiceVersion("1.0");
      repository.save(stat);
      // with same name and different version ...
      stat = new DailyStatistic();
      stat.setDay("20140319");
      stat.setServiceName("TestService1");
      stat.setServiceVersion("1.2");
      repository.save(stat);
   }

   @Test
   void testFindByDayAndServiceNameAndServiceVersion() {
      // Retrieve a stat using theses 3 criteria.
      DailyStatistic stat = repository.findByDayAndServiceNameAndServiceVersion("20140319", "TestService1", "1.0")
            .get(0);
      assertNotNull(stat);
      assertNotNull(stat.getId());
      assertEquals("20140319", stat.getDay());
      assertEquals("TestService1", stat.getServiceName());
      assertEquals("1.0", stat.getServiceVersion());
      // Retrieve another stat object.
      DailyStatistic otherStat = repository.findByDayAndServiceNameAndServiceVersion("20140319", "TestService1", "1.2")
            .get(0);
      assertNotNull(otherStat);
      assertNotNull(otherStat.getId());
      assertNotEquals(stat.getId(), otherStat.getId());
   }
}
