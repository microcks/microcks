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

import io.github.microcks.domain.Service;
import io.github.microcks.event.ChangeType;
import io.github.microcks.event.ServiceChangeEvent;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.service.MetricsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Event Listener that deals with TestCoverageMetric operations depending on event type.
 * @author laurent
 */
@Component
public class TestConformanceMetricConfigurer implements ApplicationListener<ServiceChangeEvent> {

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

   @Override
   @Async
   public void onApplicationEvent(ServiceChangeEvent event) {
      log.debug("Received a ServiceChangeEvent on {}", event.getServiceId());

      if (event.getChangeType().equals(ChangeType.DELETED)) {
         metricsService.removeTestConformanceMetric(event.getServiceId());
      } else {
         Service service = serviceRepository.findById(event.getServiceId()).orElse(null);
         if (service != null) {
            metricsService.configureTestConformanceMetric(service);
         } else {
            log.warn("Service with id {} not found but not a DELETED event?!", event.getServiceId());
         }
      }
      log.debug("Processing of ServiceChangeEvent done !");
   }
}
