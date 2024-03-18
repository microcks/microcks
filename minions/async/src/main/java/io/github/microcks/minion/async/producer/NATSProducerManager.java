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
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.TemplateEngine;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Options;
import io.nats.client.Nats;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * NATS implementation of producer for async event messages.
 * 
 * @author laurent
 */
@ApplicationScoped
public class NATSProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private Connection client;

   @ConfigProperty(name = "nats.server")
   String natsServer;

   @ConfigProperty(name = "nats.username")
   String natsUsername;

   @ConfigProperty(name = "nats.password")
   String natsPassword;

   /**
    * Initialize the NATS client post construction.
    * 
    * @throws Exception If connection to NATS Broker cannot be done.
    */
   @PostConstruct
   public void create() throws Exception {
      try {
         client = createClient();
      } catch (Exception e) {
         logger.errorf("Cannot connect to NATS broker %s", natsServer);
         logger.errorf("Connection exception: %s", e.getMessage());
         throw e;
      }
   }

   /**
    * Create a NATS Connection and connect it to the server.
    * 
    * @return A new NATS Connection implementation initialized with configuration properties.
    * @throws Exception in case of connection failure
    */
   protected Connection createClient() throws Exception {
      Options.Builder optionsBuilder = new Options.Builder().server(natsServer).maxReconnects(10);
      // Add authentication option.
      if (natsUsername != null && natsPassword != null) {
         optionsBuilder.userInfo(natsUsername, natsPassword);
      }

      client = Nats.connect(optionsBuilder.build());
      return client;
   }

   /**
    * Publish a message on specified topic.
    * 
    * @param topic   The destination topic for message
    * @param value   The message payload
    * @param headers A set of headers if any (maybe null or empty)
    */
   public void publishMessage(String topic, String value, Headers headers) {
      logger.infof("Publishing on topic {%s}, message: %s ", topic, value);
      Message msg = NatsMessage.builder().subject(topic).data(value.getBytes(StandardCharsets.UTF_8)).headers(headers)
            .build();
      client.publish(msg);
   }

   /**
    * Transform and render Microcks headers into NATS specific headers.
    * 
    * @param engine  The template engine to reuse (because we do not want to initialize and manage a context at the
    *                NATSProducerManager level.)
    * @param headers The Microcks event message headers definition.
    * @return A set of NATS headers.
    */
   public Headers renderEventMessageHeaders(TemplateEngine engine, Set<io.github.microcks.domain.Header> headers) {
      if (headers != null && !headers.isEmpty()) {
         Headers natsHeaders = new Headers();
         for (io.github.microcks.domain.Header header : headers) {
            Optional<String> optionalValue = header.getValues().stream().findFirst();
            if (optionalValue.isPresent()) {
               String headerValue;
               String firstValue = optionalValue.get();
               if (firstValue.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
                  try {
                     headerValue = Base64.getEncoder().encodeToString(engine.getValue(firstValue).getBytes());
                  } catch (Throwable t) {
                     logger.error("Failing at evaluating template " + firstValue, t);
                     headerValue = Base64.getEncoder().encodeToString(firstValue.getBytes());
                  }
               } else {
                  headerValue = Base64.getEncoder().encodeToString(firstValue.getBytes());
               }
               natsHeaders.add(header.getName(), headerValue);
            }

         }
         return natsHeaders;
      }

      return null;
   }

   /**
    * Get the NATS topic name corresponding to a AsyncMockDefinition, sanitizing all parameters.
    * 
    * @param definition   The AsyncMockDefinition
    * @param eventMessage The message to get topic
    * @return The topic name for definition and event
    */
   public String getTopicName(AsyncMockDefinition definition, EventMessage eventMessage) {
      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");

      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" ", "");

      // Produce operation name part of topic name.
      String operationName = ProducerManager.getDestinationOperationPart(definition.getOperation(), eventMessage);

      // Aggregate the 3 parts using '_' as delimiter.
      return serviceName + "-" + versionName + "-" + operationName;
   }
}
