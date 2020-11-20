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
package io.github.microcks.minion.async.producer;

import io.github.microcks.domain.BindingType;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import io.quarkus.arc.Unremovable;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.Arrays;
import java.util.Set;

@Unremovable
@ApplicationScoped
/**
 * ProducerManager is the responsible for emitting mock event messages when specific frequency triggered is reached.
 * Need to specify it as @Unremovable to avoid Quarkus ARC optimization removing beans that are not injected elsewhere
 * (this one is resolved using Arc.container().instance() method from ProducerScheduler).
 * @author laurent
 */
public class ProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   AsyncMockRepository mockRepository;

   @Inject
   KafkaProducerManager kafkaProducerManager;

   @Inject
   MQTTProducerManager mqttProducerManager;

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   /**
    * Produce all the async mock messages corresponding to specified frequency.
    * @param frequency The frequency to emit messages for
    */
   public void produceAsyncMockMessagesAt(Long frequency) {
      logger.info("Producing async mock messages for frequency: " + frequency);

      Set<AsyncMockDefinition> mockDefinitions = mockRepository.getMockDefinitionsByFrequency(frequency);
      for (AsyncMockDefinition definition : mockDefinitions) {

         for (String binding : definition.getOperation().getBindings().keySet()) {
            // Ensure this minion supports this binding.
            if (Arrays.asList(supportedBindings).contains(binding)) {
               switch (BindingType.valueOf(binding)) {
                  case KAFKA:
                     String topic = getKafkaTopicName(definition);
                     for (EventMessage eventMessage : definition.getEventMessages()) {
                        String key = String.valueOf(System.currentTimeMillis());
                        String message = renderEventMessageContent(eventMessage);
                        kafkaProducerManager.publishMessage(topic, key, message);
                     }
                     break;
                  case MQTT:
                     topic = getMQTTTopicName(definition);
                     for (EventMessage eventMessage : definition.getEventMessages()) {
                        String message = renderEventMessageContent(eventMessage);
                        mqttProducerManager.publishMessage(topic, message);
                     }
               }
            }
         }

      }
   }

   /** Render event message content from definition applying template rendering if required. */
   private String renderEventMessageContent(EventMessage eventMessage) {
      String content = eventMessage.getContent();
      if (content.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         logger.debug("EventMessage contains dynamic EL expression, rendering it...");
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

         try {
            content = engine.getValue(content);
         } catch (Throwable t) {
            logger.error("Failing at evaluating template " + content, t);
         }
      }
      return content;
   }

   /** Get the Kafka topic name corresponding to a AsyncMockDefinition, sanitizing all parameters. */
   private String getKafkaTopicName(AsyncMockDefinition definition) {
      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");
      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" " , "");
      // Produce operation name part of topic name.
      String operationName = definition.getOperation().getName();
      if (operationName.startsWith("SUBSCRIBE ")) {
         operationName = operationName.substring(operationName.indexOf(" ") + 1);
      }
      operationName = operationName.replace('/', '-');
      // Aggregate the 3 parts using '_' as delimiter.
      return serviceName + "_" + versionName + "_" + operationName;
   }

   /** Get the MQTT topic name corresponding to a AsyncMockDefinition, sanitizing all parameters. */
   private String getMQTTTopicName(AsyncMockDefinition definition) {
      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");
      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" " , "");
      // Produce operation name part of topic name.
      String operationName = definition.getOperation().getName();
      if (operationName.startsWith("SUBSCRIBE ")) {
         operationName = operationName.substring(operationName.indexOf(" ") + 1);
      }
      operationName = operationName.replace('/', '-');
      // Aggregate the 3 parts using '_' as delimiter.
      return serviceName + "_" + versionName + "_" + operationName;
   }
}
