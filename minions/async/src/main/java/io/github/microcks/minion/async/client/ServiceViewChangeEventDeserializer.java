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
package io.github.microcks.minion.async.client;

import io.github.microcks.event.ServiceViewChangeEvent;

import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;

/**
 * A Kafka deserializer for ServiceViewChangeEvent using Jackson ObjectMapper.
 * @author laurent
 */
public class ServiceViewChangeEventDeserializer extends ObjectMapperDeserializer<ServiceViewChangeEvent> {

   public ServiceViewChangeEventDeserializer() {
      // Pass the class to the parent.
      super(ServiceViewChangeEvent.class);
   }
}
