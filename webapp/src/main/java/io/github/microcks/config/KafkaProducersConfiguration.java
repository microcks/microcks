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
package io.github.microcks.config;

import io.github.microcks.event.AsyncAPITriggerCommand;
import io.github.microcks.event.ServiceViewChangeEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Allows the configuration of the different Kafka producer for service change and asyncapi trigger events. Each
 * producer can be configured with specific properties, but they will all share the common properties defined in the
 * "kafka" configuration.
 * @author laurent
 */
@Configuration
@ConditionalOnProperty(value = "async-api.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaProducersConfiguration {

   private final KafkaMultipleProducersProperties kafkaMultipleProducersProperties;

   public KafkaProducersConfiguration(KafkaMultipleProducersProperties kafkaMultipleProducersProperties) {
      this.kafkaMultipleProducersProperties = kafkaMultipleProducersProperties;
   }

   @Bean("serviceViewChangesKafkaTemplate")
   public KafkaTemplate<String, ServiceViewChangeEvent> serviceViewChangesKafkaTemplate() {
      return new KafkaTemplate<>(producerFactory("service-changes", ServiceViewChangeEvent.class));
   }

   @Bean("asyncAPITriggerCommandKafkaTemplate")
   public KafkaTemplate<String, AsyncAPITriggerCommand> asyncAPITriggerCommandKafkaTemplate() {
      return new KafkaTemplate<>(producerFactory("asyncapi-triggers", AsyncAPITriggerCommand.class));
   }

   private <T> ProducerFactory<String, T> producerFactory(String producerName, Class<T> eventTypeClazz) {
      Map<String, Object> properties = new HashMap<>(kafkaMultipleProducersProperties.buildCommonProperties());
      KafkaProperties.Producer producerProps = kafkaMultipleProducersProperties.getProducers().get(producerName);
      if (producerProps != null) {
         properties.putAll(producerProps.buildProperties(null));
      }
      return new DefaultKafkaProducerFactory<>(properties);
   }
}
