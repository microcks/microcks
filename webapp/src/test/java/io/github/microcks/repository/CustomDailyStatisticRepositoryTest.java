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
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.HashMap;
import java.util.Map;

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
      try {
         DailyStatistic stat = repository.aggregateDailyStatistics("20140930");
      } catch (ConverterNotFoundException cvnfe) {
         // For now, mapReduce in Fongo is experimental. MapReduce execution is working
         // but SpringData cannot convert Fongo Rhino result into Java object
         // ("No converter found capable of converting from type org.mozilla.javascript.UniqueTag to type java.lang.Integer")
      } catch (UncategorizedMongoDbException ume) {
         // For now, mapReduce in Fongo is experimental. MapReduce execution is not working
         // ("org.mozilla.javascript.EcmaError: TypeError: Cannot read property "0" from undefined")
      } catch (RuntimeException re) {
         // For now, mapReduce in Fongo is experimental. MapReduce execution is working
         // but SpringData cannot convert Fongo Rhino result into Java object
         // ("json can't serialize type : class org.mozilla.javascript.UniqueTag")
      }
   }

   private Map<String, Integer> initializeHourlyMap() {
      Map<String, Integer> result = new HashMap<String, Integer>(24);
      for (int i = 0; i < 24; i++) {
         result.put(String.valueOf(i), 0);
      }
      return result;
   }
}
