/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.listener;

import io.github.microcks.domain.Service;
import io.github.microcks.event.ChangeType;
import io.github.microcks.event.ServiceChangeEvent;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.service.MetricsService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
   private static Log log = LogFactory.getLog(TestConformanceMetricConfigurer.class);

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private MetricsService metricsService;


   @Override
   @Async
   public void onApplicationEvent(ServiceChangeEvent event) {
      log.debug("Received a ServiceChangeEvent on " + event.getServiceId());

      Service service = serviceRepository.findById(event.getServiceId()).orElse(null);

      if (event.getChangeType().equals(ChangeType.DELETED)) {
         metricsService.removeTestConformanceMetric(service);
      } else {
         metricsService.configureTestConformanceMetric(service);
      }
      log.debug("Processing of ServiceChangeEvent done !");
   }
}
