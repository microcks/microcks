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

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.BindingType;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.TemplateEngine;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Apache Pulsar implementation of producer for async event messages.
 * @author Malay Dewangan
 */
@ApplicationScoped
public class PulsarProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private static final String NON_PERSISTENT_PREFIX = "non-persistent://public/default/";

   private PulsarClient client;

   /** Pulsar producers are costly to create, so keep one per topic. */
   private final Map<String, Producer<byte[]>> producers = new ConcurrentHashMap<>();

   @ConfigProperty(name = "pulsar.service-url")
   String serviceUrl;

   /**
    * Initialize the Pulsar client post construction.
    * @throws PulsarClientException If client cannot be created from configuration.
    */
   @PostConstruct
   public void create() throws PulsarClientException {
      try {
         client = createClient();
      } catch (PulsarClientException e) {
         logger.errorf("Cannot create Pulsar client for %s", serviceUrl);
         logger.errorf("Connection exception: %s", e.getMessage());
         throw e;
      }
   }

   /**
    * Create a Pulsar client for the configured service URL.
    * @return A new PulsarClient initialized with configuration properties.
    * @throws PulsarClientException in case of invalid configuration
    */
   protected PulsarClient createClient() throws PulsarClientException {
      return PulsarClient.builder().serviceUrl(serviceUrl).build();
   }

   /** Close cached producers and the client on shutdown. */
   @PreDestroy
   public void close() {
      producers.values().forEach(Producer::closeAsync);
      producers.clear();
      if (client != null) {
         try {
            client.close();
         } catch (PulsarClientException e) {
            logger.warnf("Error while closing Pulsar client: %s", e.getMessage());
         }
      }
   }

   /**
    * Publish a message on specified topic.
    * @param topic      The destination topic for message
    * @param value      The message payload
    * @param properties A set of message properties (maybe empty)
    */
   public void publishMessage(String topic, String value, Map<String, String> properties) {
      logger.infof("Publishing on topic {%s}, message: %s ", topic, value);
      publishBytes(topic, value.getBytes(StandardCharsets.UTF_8), properties);
   }

   /**
    * Publish a binary message on specified topic. Used for Avro binary content.
    * @param topic      The destination topic for message
    * @param value      The message payload as raw bytes
    * @param properties A set of message properties (maybe empty)
    */
   public void publishMessage(String topic, byte[] value, Map<String, String> properties) {
      logger.infof("Publishing on topic {%s}, a binary message of %d bytes", topic, value.length);
      publishBytes(topic, value, properties);
   }

   private void publishBytes(String topic, byte[] value, Map<String, String> properties) {
      try {
         // Async send so a slow or down broker doesn't block the mock scheduler thread.
         getProducer(topic).newMessage().value(value).properties(properties).sendAsync().exceptionally(e -> {
            logger.errorf("Error while publishing on Pulsar topic {%s}: %s", topic, e.getMessage());
            return null;
         });
      } catch (PulsarClientException e) {
         logger.errorf("Error while publishing on Pulsar topic {%s}: %s", topic, e.getMessage());
      }
   }

   private synchronized Producer<byte[]> getProducer(String topic) throws PulsarClientException {
      Producer<byte[]> producer = producers.get(topic);
      if (producer == null) {
         producer = client.newProducer().topic(topic).create();
         producers.put(topic, producer);
      }
      return producer;
   }

   /**
    * Transform and render Microcks headers into Pulsar message properties.
    * @param engine  The template engine to reuse (because we do not want to initialize and manage a context at the
    *                PulsarProducerManager level.)
    * @param headers The Microcks event message headers definition.
    * @return A map of Pulsar message properties.
    */
   public Map<String, String> renderEventMessageHeaders(TemplateEngine engine,
         Set<io.github.microcks.domain.Header> headers) {
      if (headers == null || headers.isEmpty()) {
         return Collections.emptyMap();
      }
      Map<String, String> properties = new HashMap<>();
      for (io.github.microcks.domain.Header header : headers) {
         // Pulsar properties are mono valued so just consider the first value.
         Optional<String> firstValueOpt = header.getValues().stream().findFirst();
         if (firstValueOpt.isPresent()) {
            String firstValue = firstValueOpt.get();
            if (firstValue.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
               try {
                  properties.put(header.getName(), engine.getValue(firstValue));
               } catch (Exception e) {
                  logger.error("Failing at evaluating template " + firstValue, e);
                  properties.put(header.getName(), firstValue);
               }
            } else {
               properties.put(header.getName(), firstValue);
            }
         }
      }
      return properties;
   }

   /**
    * Get the Pulsar topic name corresponding to a AsyncMockDefinition, sanitizing all parameters.
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
      operationName = operationName.replace('/', '-');

      String topicName = serviceName + "-" + versionName + "-" + operationName;

      // Short names resolve to persistent://public/default, so only non-persistent needs the full form.
      Map<String, Binding> bindings = definition.getOperation().getBindings();
      Binding binding = bindings != null ? bindings.get(BindingType.PULSAR.name()) : null;
      if (binding != null && !binding.isPersistent()) {
         return NON_PERSISTENT_PREFIX + topicName;
      }
      return topicName;
   }
}
