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
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.util.AvroUtil;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import io.quarkus.arc.Unremovable;
import org.apache.avro.generic.GenericRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Set;

@Unremovable
@ApplicationScoped
/**
 * ProducerManager is the responsible for emitting mock event messages when specific frequency
 * triggered is reached. Need to specify it as @Unremovable to avoid Quarkus ARC optimization
 * removing beans that are not injected elsewhere (this one is resolved using
 * Arc.container().instance() method from ProducerScheduler).
 * 
 * @author laurent
 */
public class ProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   AsyncMockRepository mockRepository;

   @Inject
   SchemaRegistry schemaRegistry;

   @Inject
   KafkaProducerManager kafkaProducerManager;

   @Inject
   MQTTProducerManager mqttProducerManager;

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   @ConfigProperty(name = "minion.default-avro-encoding", defaultValue = "RAW")
   String defaultAvroEncoding;

   /**
    * Produce all the async mock messages corresponding to specified frequency.
    * 
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
                     for (EventMessage eventMessage : definition.getEventMessages()) {
                        String topic = kafkaProducerManager.getTopicName(definition, eventMessage);
                        String key = String.valueOf(System.currentTimeMillis());
                        String message = renderEventMessageContent(eventMessage);

                        // Check it Avro binary is expected, we should convert to bytes.
                        if ("avro/binary".equals(eventMessage.getMediaType())) {
                           // Build the name of expected schema.
                           String schemaName = IdBuilder.buildResourceFullName(definition.getOwnerService(), definition.getOperation());
                           String schemaContent = schemaRegistry.getSchemaEntryContent(definition.getOwnerService(), schemaName);

                           try {
                              if ("REGISTRY".equals(defaultAvroEncoding) && kafkaProducerManager.isRegistryEnabled()) {
                                 GenericRecord avroRecord = AvroUtil.jsonToAvroRecord(message, schemaContent);
                                 kafkaProducerManager.publishMessage(topic, key, avroRecord, kafkaProducerManager
                                       .renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
                              } else {
                                 byte[] avroBinary = AvroUtil.jsonToAvro(message, schemaContent);
                                 kafkaProducerManager.publishMessage(topic, key, avroBinary, kafkaProducerManager
                                       .renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
                              }
                           } catch (Exception e) {
                              logger.errorf("Exception while converting {%s} to Avro using schema {%s}", message, schemaContent, e);
                           }
                        } else {
                           kafkaProducerManager.publishMessage(topic, key, message, kafkaProducerManager
                                 .renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
                        }
                     }
                     break;
                  case MQTT:
                     for (EventMessage eventMessage : definition.getEventMessages()) {
                        String topic = mqttProducerManager.getTopicName(definition, eventMessage);
                        String message = renderEventMessageContent(eventMessage);
                        mqttProducerManager.publishMessage(topic, message);
                     }
                     break;
                  default:
                     break;
                }
            }
         }
      }
   }

   /**
    * Render event message content from definition applying template rendering if required.
    */
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

   public static String replacePartPlaceholders(EventMessage eventMessage, String operationName) {
      String partsCriteria = eventMessage.getDispatchCriteria();
      if (partsCriteria != null && !partsCriteria.isBlank()) {
         String[] criterion = partsCriteria.split("/");
         for (String criteria : criterion) {
            if (criteria != null && !criteria.isBlank()) {
               String[] element = criteria.split("=");
               String key = String.format("\\{%s\\}", element[0]);
               operationName = operationName.replaceAll(key, URLEncoder.encode(element[1], Charset.defaultCharset()));
            }
         }
      }
      return operationName;
   }

}
