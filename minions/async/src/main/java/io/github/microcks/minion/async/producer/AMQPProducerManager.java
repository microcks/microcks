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
import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.TemplateEngine;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * AMQP 0.9.1 (ie. RabbitMQ) implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class AMQPProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private Connection amqpConnection;

   @ConfigProperty(name = "amqp.server")
   String amqpServer;

   @ConfigProperty(name = "amqp.clientid", defaultValue = "microcks-async-minion")
   String amqpClientId;

   @ConfigProperty(name = "amqp.username")
   String amqpUsername;

   @ConfigProperty(name = "amqp.password")
   String amqpPassword;

   /**
    * Initialize the AMQP connection post construction.
    * 
    * @throws Exception If connection to AMQP Broker cannot be done.
    */
   @PostConstruct
   public void create() throws Exception {
      try {
         amqpConnection = createConnection();
      } catch (Exception e) {
         logger.errorf("Cannot connect to AMQP broker %s", amqpServer);
         logger.errorf("Connection exception: %s", e.getMessage());
         throw e;
      }
   }

   /**
    * @return A newly created connection to configured broker
    * @throws Exception in case of connection failure
    */
   protected Connection createConnection() throws Exception {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setUri("amqp://" + amqpServer);

      if (amqpUsername != null && !amqpUsername.isEmpty() && amqpPassword != null && !amqpPassword.isEmpty()) {
         logger.infof("Connecting to AMQP broker with user '%s'", amqpUsername);
         factory.setUsername(amqpUsername);
         factory.setPassword(amqpPassword);
      }
      factory.setAutomaticRecoveryEnabled(true);
      return factory.newConnection(amqpClientId);
   }

   /**
    * Publish a message on specified destination.
    * 
    * @param destinationType The type of destination (queue, topic, fanout, ...)
    * @param destinationName The name of destination
    * @param value           The message payload
    * @param headers         A set of headers if any (maybe null or empty)
    */
   public void publishMessage(String destinationType, String destinationName, String value, Set<Header> headers) {
      logger.infof("Publishing on destination {%s}, message: %s ", destinationName, value);
      try (Channel channel = amqpConnection.createChannel()) {
         channel.exchangeDeclare(destinationName, destinationType);

         AMQP.BasicProperties properties = null;
         // Adding headers to properties if provided.
         if (headers != null && !headers.isEmpty()) {
            Map<String, Object> amqpHeaders = new HashMap<>();
            for (Header header : headers) {
               amqpHeaders.put(header.getName(), header.getValues().toArray()[0]);
            }
            properties = new AMQP.BasicProperties.Builder().headers(amqpHeaders).build();
         }
         channel.basicPublish(destinationName, "", properties, value.getBytes(StandardCharsets.UTF_8));
      } catch (IOException | TimeoutException ioe) {
         logger.warnf("Message %s sending has thrown an exception", ioe);
      }
   }

   public String getDestinationName(AsyncMockDefinition definition, EventMessage eventMessage) {
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

   /**
    * Render Microcks headers using the template engine.
    * 
    * @param engine  The template engine to reuse (because we do not want to initialize and manage a context at the
    *                KafkaProducerManager level.)
    * @param headers The Microcks event message headers definition.
    * @return A set of rendered Microcks headers.
    */
   public Set<Header> renderEventMessageHeaders(TemplateEngine engine, Set<Header> headers) {
      if (headers != null && !headers.isEmpty()) {
         Set<Header> renderedHeaders = HashSet.newHashSet(headers.size());

         for (Header header : headers) {
            Optional<String> optionalValue = header.getValues().stream().findFirst();
            if (optionalValue.isPresent()) {
               String firstValue = optionalValue.get();
               if (firstValue.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
                  try {
                     Header renderedHeader = new Header();
                     renderedHeader.setName(header.getName());
                     renderedHeader.setValues(Set.of(engine.getValue(firstValue)));
                     renderedHeaders.add(renderedHeader);
                  } catch (Throwable t) {
                     logger.error("Failing at evaluating template " + firstValue, t);
                     Header renderedHeader = new Header();
                     renderedHeader.setName(header.getName());
                     renderedHeader.setValues(Set.of(firstValue));
                     renderedHeaders.add(renderedHeader);
                  }
               } else {
                  Header renderedHeader = new Header();
                  renderedHeader.setName(header.getName());
                  renderedHeader.setValues(Set.of(firstValue));
                  renderedHeaders.add(renderedHeader);
               }
            }
         }
         return renderedHeaders;
      }

      return Collections.emptySet();
   }
}
