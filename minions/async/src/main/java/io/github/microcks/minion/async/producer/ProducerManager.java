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

import java.util.Arrays;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.github.microcks.domain.BindingType;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.quarkus.arc.Unremovable;

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

   @ConfigProperty(name = "minion.default-avro-encoding", defaultValue = "RAW")
   String defaultAvroEncoding;

   /**
    * Produce all the async mock messages corresponding to specified frequency.
    * @param frequency The frequency to emit messages for
    */
   public void produceAsyncMockMessagesAt(Long frequency) {
      logger.info("Producing async mock messages for frequency: " + frequency);

      Set<AsyncMockDefinition> mockDefinitions = mockRepository.getMockDefinitionsByFrequency(frequency);
      for (AsyncMockDefinition definition : mockDefinitions) {
         logger.debugf("Processing definition of service {%s}", definition.getOwnerService().getName() + ':' + definition.getOwnerService().getVersion());

         for (String binding : definition.getOperation().getBindings().keySet()) {
            // Ensure this minion supports this binding.
            if (Arrays.asList(supportedBindings).contains(binding)) {
               BindingProducerManager publisher = null;
               switch (BindingType.valueOf(binding)) {
               case KAFKA:
                  publisher = this.kafkaProducerManager;
                  break;
               case MQTT:
                  publisher = this.mqttProducerManager;
                  break;
               default:
                  break;
               }
               if (publisher != null) {
                  publisher.publishMessages(definition);
               }
            }
         }
      }
   }

}
