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
package io.github.microcks.event;

import io.github.microcks.domain.ServiceView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;

/**
 * A Kafka serializer for ServiceView using Jackson ObjectMapper serialization.
 * @author laurent
 */
public class ServiceViewSerializer implements Serializer<ServiceView> {

   private ObjectMapper mapper = new ObjectMapper();

   @Override
   public byte[] serialize(String topic, ServiceView serviceView) {
      try {
         return mapper.writeValueAsBytes(serviceView);
      } catch (JsonProcessingException e) {
         throw new SerializationException("Error serializing serviceView", e);
      }
   }
}
