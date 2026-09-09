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
package io.github.microcks.minion.async.producer;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.minion.async.AsyncMockDefinition;

import org.apache.kafka.common.config.SaslConfigs;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This is a test case for KafkaProducerManager.
 *
 * @author laurent
 */
class KafkaProducerManagerTest {

   @Test
   void testGetTopicName() {
      KafkaProducerManager producerManager = new KafkaProducerManager();

      Service service = new Service();
      service.setName("Streetlights API");
      service.setVersion("0.1.0");

      Operation operation = new Operation();
      operation.setName("RECEIVE receiveLightMeasurement");
      operation.setMethod("RECEIVE");
      operation.setResourcePaths(Set.of("smartylighting.streetlights.1.0.event.lighting.measured"));
      service.addOperation(operation);

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("Sample");
      List<EventMessage> eventsMessages = List.of(eventMessage);

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, eventsMessages);

      String topicName = producerManager.getTopicName(definition, eventMessage);
      assertEquals("StreetlightsAPI-0.1.0-receiveLightMeasurement", topicName);
   }

   @Test
   void testGetDynamicTopicName() {
      KafkaProducerManager producerManager = new KafkaProducerManager();

      Service service = new Service();
      service.setName("Streetlights API");
      service.setVersion("0.1.0");

      Operation operation = new Operation();
      operation.setName("RECEIVE receiveLightMeasurement");
      operation.setMethod("RECEIVE");
      operation.setDispatcher("URI_PARTS");
      operation.setDispatcherRules("/streetlightId");
      operation.setResourcePaths(Set.of("smartylighting.streetlights.1.0.event.{streetlightId}.lighting.measured",
            "smartylighting.streetlights.1.0.event.da059782-3ad0-4e45-88ce-ef3392bc7797.lighting.measured"));
      service.addOperation(operation);

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("Sample");
      eventMessage.setDispatchCriteria("streetlightId=da059782-3ad0-4e45-88ce-ef3392bc7797");
      List<EventMessage> eventsMessages = List.of(eventMessage);

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, eventsMessages);

      String topicName = producerManager.getTopicName(definition, eventMessage);

      assertEquals(
            "StreetlightsAPI-0.1.0-smartylighting.streetlights.1.0.event.da059782-3ad0-4e45-88ce-ef3392bc7797.lighting.measured",
            topicName);
   }

   @Test
   void testCompletePropertiesWithSecurityConfigSaslSslIncludesClientCallbackHandler() throws Exception {
      String clientCallbackHandler = "software.amazon.msk.auth.iam.IAMClientCallbackHandler";
      Map<String, String> configValues = Map.of("kafka.security.protocol", "SASL_SSL", "kafka.sasl.mechanism",
            "AWS_MSK_IAM", "kafka.sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;",
            "kafka.sasl.client.callback.handler.class", clientCallbackHandler);

      Properties props = invokeCompletePropertiesWithSecurityConfig(configValues);

      assertEquals("SASL_SSL", props.get("security.protocol"));
      assertEquals("AWS_MSK_IAM", props.get(SaslConfigs.SASL_MECHANISM));
      assertEquals("software.amazon.msk.auth.iam.IAMLoginModule required;", props.get(SaslConfigs.SASL_JAAS_CONFIG));
      assertEquals(clientCallbackHandler, props.get(SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS));
   }

   @Test
   void testCompletePropertiesWithSecurityConfigSaslSslWithoutClientCallbackHandler() throws Exception {
      Map<String, String> configValues = Map.of("kafka.security.protocol", "SASL_SSL", "kafka.sasl.mechanism",
            "AWS_MSK_IAM", "kafka.sasl.jaas.config", "software.amazon.msk.auth.iam.IAMLoginModule required;");

      Properties props = invokeCompletePropertiesWithSecurityConfig(configValues);

      assertNull(props.get(SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS));
   }

   private Properties invokeCompletePropertiesWithSecurityConfig(Map<String, String> configValues) throws Exception {
      KafkaProducerManager producerManager = new KafkaProducerManager();
      Properties props = new Properties();
      Method method = KafkaProducerManager.class.getDeclaredMethod("completePropertiesWithSecurityConfig",
            Properties.class, Config.class);
      method.setAccessible(true);
      method.invoke(producerManager, props, new MapConfig(configValues));
      return props;
   }

   private static class MapConfig implements Config {
      private final Map<String, String> values;

      private MapConfig(Map<String, String> values) {
         this.values = new HashMap<>(values);
      }

      @Override
      public <T> T getValue(String propertyName, Class<T> propertyType) {
         String value = values.get(propertyName);
         if (value == null) {
            throw new IllegalArgumentException("Missing config property: " + propertyName);
         }
         return propertyType.cast(value);
      }

      @Override
      public ConfigValue getConfigValue(String propertyName) {
         return null;
      }

      @Override
      public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
         return Optional.ofNullable(values.get(propertyName)).map(propertyType::cast);
      }

      @Override
      public Iterable<String> getPropertyNames() {
         return values.keySet();
      }

      @Override
      public Iterable<ConfigSource> getConfigSources() {
         return null;
      }

      @Override
      public <T> Optional<Converter<T>> getConverter(Class<T> forType) {
         return Optional.empty();
      }

      @Override
      public <T> T unwrap(Class<T> type) {
         return null;
      }
   }
}
