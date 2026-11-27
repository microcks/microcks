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
import io.github.microcks.minion.async.AsyncMockRepository;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager responsible for lifecycle management of request-reply handlers. Creates, starts, and stops handlers for
 * request-reply operations based on their protocol bindings.
 *
 * @author adamhicks
 */
@ApplicationScoped
public class RequestReplyHandlerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private final Map<String, RequestReplyHandler> handlers = new ConcurrentHashMap<>();

   final AsyncMockRepository mockRepository;
   final KafkaRequestReplyHandlerFactory kafkaHandlerFactory;

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   /**
    * Create a new RequestReplyHandlerManager with required dependencies.
    *
    * @param mockRepository      The repository for mock definitions
    * @param kafkaHandlerFactory Factory for creating Kafka handlers
    */
   public RequestReplyHandlerManager(AsyncMockRepository mockRepository,
         KafkaRequestReplyHandlerFactory kafkaHandlerFactory) {
      this.mockRepository = mockRepository;
      this.kafkaHandlerFactory = kafkaHandlerFactory;
   }

   /**
    * Start handlers for all request-reply mock definitions.
    */
   public void startAllHandlers() {
      logger.info("Starting all request-reply handlers...");
      Set<AsyncMockDefinition> requestReplyDefinitions = mockRepository.getRequestReplyMockDefinitions();

      for (AsyncMockDefinition definition : requestReplyDefinitions) {
         startHandlerForDefinition(definition);
      }

      logger.infof("Started %d request-reply handler(s)", handlers.size());
   }

   /**
    * Start a handler for a specific mock definition.
    *
    * @param definition The mock definition to start a handler for
    */
   public void startHandlerForDefinition(AsyncMockDefinition definition) {
      String handlerKey = buildHandlerKey(definition);

      // Check if handler already exists
      if (handlers.containsKey(handlerKey)) {
         logger.debugf("Handler for %s already exists, skipping", handlerKey);
         return;
      }

      // Determine which binding to use (first supported binding)
      for (String binding : definition.getOperation().getBindings().keySet()) {
         if (Arrays.asList(supportedBindings).contains(binding)) {
            RequestReplyHandler handler = createHandlerForBinding(definition, binding);
            if (handler != null) {
               try {
                  handler.start();
                  handlers.put(handlerKey, handler);
                  logger.infof("Started request-reply handler for %s using %s binding",
                        definition.getOwnerService().getName() + ":" + definition.getOwnerService().getVersion() + " - "
                              + definition.getOperation().getName(),
                        binding);
                  break; // Only start one handler per definition
               } catch (Exception e) {
                  logger.errorf(e, "Failed to start request-reply handler for %s", handlerKey);
               }
            }
         }
      }
   }

   /**
    * Stop and remove a handler for a specific service.
    *
    * @param serviceId The service ID to stop handlers for
    */
   public void stopHandlersForService(String serviceId) {
      logger.infof("Stopping request-reply handlers for service %s", serviceId);

      handlers.entrySet().removeIf(entry -> {
         if (entry.getKey().startsWith(serviceId + ":")) {
            entry.getValue().stop();
            logger.infof("Stopped handler for %s", entry.getKey());
            return true;
         }
         return false;
      });
   }

   /**
    * Stop all handlers.
    */
   public void stopAllHandlers() {
      logger.info("Stopping all request-reply handlers...");

      for (Map.Entry<String, RequestReplyHandler> entry : handlers.entrySet()) {
         try {
            entry.getValue().stop();
            logger.debugf("Stopped handler for %s", entry.getKey());
         } catch (Exception e) {
            logger.errorf(e, "Error stopping handler for %s", entry.getKey());
         }
      }

      handlers.clear();
      logger.info("All request-reply handlers stopped");
   }

   /**
    * Get the number of active handlers.
    *
    * @return the count of active handlers
    */
   public int getHandlerCount() {
      return handlers.size();
   }

   /** Create a handler for a specific binding type. */
   private RequestReplyHandler createHandlerForBinding(AsyncMockDefinition definition, String binding) {
      try {
         BindingType bindingType = BindingType.valueOf(binding);
         Binding bindingDef = definition.getOperation().getBindings().get(binding);

         return switch (bindingType) {
            case KAFKA -> kafkaHandlerFactory.createHandler(definition, bindingDef);
            // TODO: Add other binding types as they are implemented
            // case MQTT -> mqttHandlerFactory.createHandler(definition, bindingDef);
            // case AMQP -> amqpHandlerFactory.createHandler(definition, bindingDef);
            default -> {
               logger.warnf("Request-reply handler not implemented for binding type: %s", binding);
               yield null;
            }
         };
      } catch (Exception e) {
         logger.errorf(e, "Error creating handler for binding %s", binding);
         return null;
      }
   }

   /** Build a unique key for a handler. */
   private String buildHandlerKey(AsyncMockDefinition definition) {
      return definition.getOwnerService().getId() + ":" + definition.getOperation().getName();
   }
}
