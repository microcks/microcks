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
package io.github.microcks.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

/**
 * A Kafka serializer for ServiceView using Jackson ObjectMapper serialization.
 * @author laurent
 */
public class ServiceViewChangeEventSerializer implements Serializer<ServiceViewChangeEvent> {

   private ObjectMapper mapper = new ObjectMapper();

   @Override
   public byte[] serialize(String topic, ServiceViewChangeEvent serviceViewChangeEvent) {
      try {
         return mapper.writeValueAsBytes(serviceViewChangeEvent);
      } catch (JsonProcessingException e) {
         throw new SerializationException("Error serializing serviceViewChangeEvent", e);
      }
   }
}
