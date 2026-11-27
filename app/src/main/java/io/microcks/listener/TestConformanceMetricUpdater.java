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

import io.microcks.event.TestCompletionEvent;
import io.microcks.service.MetricsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

/**
 * Application event listener that updates TestCoverageMetric on test completion.
 * @author laurent
 */
@ApplicationScoped
public class TestConformanceMetricUpdater {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(TestConformanceMetricUpdater.class);

   private final MetricsService metricsService;

   /**
    * Create a new instance of TestConformanceMetricUpdater with required dependencies.
    * @param metricsService The service for metrics operations
    */
   public TestConformanceMetricUpdater(MetricsService metricsService) {
      this.metricsService = metricsService;
   }

   public void onApplicationEvent(@ObservesAsync TestCompletionEvent event) {
      log.debug("Received a TestCompletionEvent on {}", event.getResult().getId());
      metricsService.updateTestConformanceMetricOnTestResult(event.getResult());
      log.debug("Processing of TestCompletionEvent done !");
   }
}
