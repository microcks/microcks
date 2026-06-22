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

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Manager responsible for lifecycle management of request-reply handlers. Creates, starts, and stops handlers for
 * request-reply operations based on their protocol bindings. Uses CDI-injected RequestReplyHandlerFactory instances to
 * create protocol-specific handlers.
 *
 * @author adamhicks
 */
@ApplicationScoped
public class RequestReplyHandlerManager {

   private final Logger logger = Logger.getLogger(getClass());

   private final Map<String, RequestReplyHandler> handlers = new ConcurrentHashMap<>();

   final AsyncMockRepository mockRepository;

   @Inject
   Instance<RequestReplyHandlerFactory> factoryInstances;

   private List<RequestReplyHandlerFactory> resolvedFactories;

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   /**
    * Create a new RequestReplyHandlerManager with required dependencies.
    *
    * @param mockRepository The repository for mock definitions
    */
   public RequestReplyHandlerManager(AsyncMockRepository mockRepository) {
      this.mockRepository = mockRepository;
   }

   /**
    * Initialize the resolved factories list from CDI-injected instances after construction.
    */
   @PostConstruct
   void init() {
      if (factoryInstances != null) {
         resolvedFactories = StreamSupport.stream(factoryInstances.spliterator(), false).collect(Collectors.toList());
      }
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

      if (handlers.containsKey(handlerKey)) {
         logger.debugf("Handler for %s already exists, skipping", handlerKey);
         return;
      }

      boolean handlerStarted = false;
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
                  handlerStarted = true;
                  break;
               } catch (Exception e) {
                  logger.errorf(e, "Failed to start request-reply handler for %s", handlerKey);
               }
            }
         }
      }

      if (!handlerStarted) {
         logger.warnf("Could not start any request-reply handler for %s - no supported binding factory found",
               definition.getOwnerService().getName() + ":" + definition.getOwnerService().getVersion() + " - "
                     + definition.getOperation().getName());
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

   /** Create a handler for a specific binding type by resolving the appropriate factory. */
   private RequestReplyHandler createHandlerForBinding(AsyncMockDefinition definition, String binding) {
      BindingType bindingType;
      try {
         bindingType = BindingType.valueOf(binding.toUpperCase());
      } catch (IllegalArgumentException e) {
         logger.warnf("Unknown binding type '%s', skipping handler creation", binding);
         return null;
      }

      Binding bindingDef = definition.getOperation().getBindings().get(binding);
      RequestReplyHandlerFactory factory = resolveFactory(bindingType);
      if (factory != null) {
         try {
            return factory.createHandler(definition, bindingDef);
         } catch (Exception e) {
            logger.errorf(e, "Error creating handler for binding %s", binding);
            return null;
         }
      }

      logger.warnf("Request-reply handler factory not found for binding type: %s", binding);
      return null;
   }

   /** Resolve the RequestReplyHandlerFactory for the given binding type. */
   private RequestReplyHandlerFactory resolveFactory(BindingType bindingType) {
      if (resolvedFactories == null) {
         return null;
      }

      for (RequestReplyHandlerFactory factory : resolvedFactories) {
         RequestReplyHandlerFactoryQualifier qualifier = factory.getClass()
               .getAnnotation(RequestReplyHandlerFactoryQualifier.class);
         if (qualifier != null && qualifier.value() == bindingType) {
            return factory;
         }
      }

      return null;
   }

   /** Build a unique key for a handler. */
   private String buildHandlerKey(AsyncMockDefinition definition) {
      return definition.getOwnerService().getId() + ":" + definition.getOperation().getName();
   }
}
