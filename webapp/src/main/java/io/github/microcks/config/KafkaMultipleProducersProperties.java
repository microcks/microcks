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

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for multiple Kafka producers.
 * @author laurent
 */
@Configuration
@ConfigurationProperties(prefix = "kafka")
public class KafkaMultipleProducersProperties {

   private List<String> bootstrapServers;
   private Map<String, Object> properties = new HashMap<>();
   private Map<String, KafkaProperties.Producer> producers = new HashMap<>();


   public List<String> getBootstrapServers() {
      return bootstrapServers;
   }

   public void setBootstrapServers(List<String> bootstrapServers) {
      this.bootstrapServers = bootstrapServers;
   }

   public Map<String, Object> getProperties() {
      return properties;
   }

   public void setProperties(Map<String, Object> properties) {
      this.properties = properties;
   }

   public Map<String, KafkaProperties.Producer> getProducers() {
      return producers;
   }

   public void setProducers(Map<String, KafkaProperties.Producer> producers) {
      this.producers = producers;
   }

   public Map<String, Object> buildCommonProperties() {
      HashMap<String, Object> commonProperties = new HashMap<>();
      commonProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      commonProperties.putAll(properties);
      return commonProperties;
   }
}
