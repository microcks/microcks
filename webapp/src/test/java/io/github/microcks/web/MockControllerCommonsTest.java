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
package io.github.microcks.web;

import io.github.microcks.util.delay.DelaySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for the MockControllerCommons class.
 * @author laurent
 */
class MockControllerCommonsTest {

   @Test
   void shouldUseDelayAndStrategyFromHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "500");
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER, "random-10");

      DelaySpec spec = MockControllerCommons.getDelay(headers, null, "fixed");
      assertNotNull(spec);
      assertEquals(500L, spec.baseValue());
      assertEquals("random-10", spec.strategyName());
   }

   @Test
   void shouldBeCaseInsensitiveForStrategyHeader() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "250");
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER, "RaNdOm-20");

      DelaySpec spec = MockControllerCommons.getDelay(headers, null, "fixed");
      assertNotNull(spec);
      assertEquals(250L, spec.baseValue());
      assertEquals("RaNdOm-20", spec.strategyName()); // keep header value as provided
   }

   @Test
   void shouldFallBackToParameterWhenHeaderValueInvalid() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "not-a-number");

      DelaySpec spec = MockControllerCommons.getDelay(headers, 100L, "random");
      assertNotNull(spec);
      assertEquals(100L, spec.baseValue());
      assertEquals("random", spec.strategyName());
   }
}
