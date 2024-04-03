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

import io.github.microcks.event.ServiceViewChangeEvent;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * This is an implementation of {@code ServiceChangeEventChannel} that uses a Kafka topic as a destination recipient for
 * {@code ServiceViewChangeEvent}.
 * @author laurent
 */
@Component
@Profile({ "default", "prod" })
@ConditionalOnProperty(value = "async-api.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaServiceChangeEventChannel implements ServiceChangeEventChannel {

   private final KafkaTemplate<String, ServiceViewChangeEvent> kafkaTemplate;

   /**
    * Build a new KafkaServiceChangeEventChannel from a KafkaTemplate.
    * @param kafkaTemplate THe template used for sending Kafka messages.
    */
   public KafkaServiceChangeEventChannel(KafkaTemplate<String, ServiceViewChangeEvent> kafkaTemplate) {
      this.kafkaTemplate = kafkaTemplate;
   }

   @Override
   public void sendServiceViewChangeEvent(ServiceViewChangeEvent event) throws Exception {
      kafkaTemplate.send("microcks-services-updates", event.getServiceId(), event);
   }
}
