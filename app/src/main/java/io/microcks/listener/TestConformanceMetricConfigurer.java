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

import io.microcks.domain.Service;
import io.microcks.event.ChangeType;
import io.microcks.event.ServiceChangeEvent;
import io.microcks.repository.ServiceRepository;
import io.microcks.service.MetricsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;

/**
 * Event Listener that deals with TestCoverageMetric operations depending on event type.
 * @author laurent
 */
@ApplicationScoped
public class TestConformanceMetricConfigurer {

   /** A commons logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(TestConformanceMetricConfigurer.class);

   private final ServiceRepository serviceRepository;
   private final MetricsService metricsService;

   /**
    * Create a new instance of TestConformanceMetricConfigurer with required dependencies.
    * @param serviceRepository The repository for Service entities
    * @param metricsService    The service for metrics operations
    */
   public TestConformanceMetricConfigurer(ServiceRepository serviceRepository, MetricsService metricsService) {
      this.serviceRepository = serviceRepository;
      this.metricsService = metricsService;
   }

   public void onApplicationEvent(@ObservesAsync ServiceChangeEvent event) {
      log.debug("Received a ServiceChangeEvent on {}", event.getServiceId());

      if (event.getChangeType().equals(ChangeType.DELETED)) {
         metricsService.removeTestConformanceMetric(event.getServiceId());
      } else {
         Service service = serviceRepository.findById(event.getServiceId());
         if (service != null) {
            metricsService.configureTestConformanceMetric(service);
         } else {
            log.warn("Service with id {} not found but not a DELETED event?!", event.getServiceId());
         }
      }
      log.debug("Processing of ServiceChangeEvent done !");
   }
}
