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
package io.github.microcks.minion.async.handler;

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.BindingType;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.producer.KafkaProducerManager;

import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Factory for creating Kafka request-reply handlers.
 *
 * @author adamhicks
 */
@ApplicationScoped
@RequestReplyHandlerFactoryQualifier(BindingType.KAFKA)
public class KafkaRequestReplyHandlerFactory implements RequestReplyHandlerFactory {

   private final Logger logger = Logger.getLogger(getClass());

   final KafkaProducerManager producerManager;
   final SchemaRegistry schemaRegistry;
   final Config config;

   /**
    * Create a new factory with required dependencies.
    *
    * @param producerManager The Kafka producer manager for sending replies
    * @param schemaRegistry  The schema registry for message schemas
    * @param config          The application configuration
    */
   public KafkaRequestReplyHandlerFactory(KafkaProducerManager producerManager, SchemaRegistry schemaRegistry,
         Config config) {
      this.producerManager = producerManager;
      this.schemaRegistry = schemaRegistry;
      this.config = config;
   }

   @Override
   public KafkaRequestReplyHandler createHandler(AsyncMockDefinition definition, Binding binding) {
      logger.debugf("Creating Kafka request-reply handler for %s - %s",
            definition.getOwnerService().getName() + ":" + definition.getOwnerService().getVersion(),
            definition.getOperation().getName());

      return new KafkaRequestReplyHandler(definition, binding, producerManager, schemaRegistry, config);
   }
}
