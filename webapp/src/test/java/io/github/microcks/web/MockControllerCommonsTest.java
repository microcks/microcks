package io.github.microcks.web;

import io.github.microcks.util.delay.DelaySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.*;

class MockControllerCommonsTest {

   @Test
   void shouldUseDelayAndStrategyFromHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "500");
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER, "random-10");

      DelaySpec spec = MockControllerCommons.getDelay(headers, null, "fixed");
      assertNotNull(spec);
      assertEquals(500L, spec.getBaseValue());
      assertEquals("random-10", spec.getStrategyName());
   }

   @Test
   void shouldBeCaseInsensitiveForStrategyHeader() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "250");
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER, "RaNdOm-20");

      DelaySpec spec = MockControllerCommons.getDelay(headers, null, "fixed");
      assertNotNull(spec);
      assertEquals(250L, spec.getBaseValue());
      assertEquals("RaNdOm-20", spec.getStrategyName()); // keep header value as provided
   }

   @Test
   void shouldFallBackToParameterWhenHeaderValueInvalid() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "not-a-number");

      DelaySpec spec = MockControllerCommons.getDelay(headers, 100L, "random");
      assertNotNull(spec);
      assertEquals(100L, spec.getBaseValue());
      assertEquals("random", spec.getStrategyName());
   }
}
