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

import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaSerializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.util.AvroUtil;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

/**
 * Kafka implementation of producer for async event messages.
 * 
 * @author laurent
 */
@ApplicationScoped
public class KafkaProducerManager extends BindingProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   SchemaRegistry schemaRegistry;

   @ConfigProperty(name = "minion.default-avro-encoding", defaultValue = "RAW")
   String defaultAvroEncoding;

   private Producer<String, String> producer;

   private Producer<String, byte[]> bytesProducer;

   private Producer<String, GenericRecord> registryProducer;

   @ConfigProperty(name = "kafka.bootstrap.servers")
   String bootstrapServers;

   @ConfigProperty(name = "kafka.schema.registry.url", defaultValue = "")
   Optional<String> schemaRegistryUrl;

   @ConfigProperty(name = "kafka.schema.registry.confluent", defaultValue = "false")
   boolean schemaRegistryConfluent = false;

   @ConfigProperty(name = "kafka.schema.registry.username", defaultValue = "")
   Optional<String> schemaRegistryUsername;

   @ConfigProperty(name = "kafka.schema.registry.credentials.source", defaultValue = "USER_INFO")
   String schemaRegistryCredentialsSource = null;

   /**
    * Tells if producer is connected to a Schema registry and thus able to send Avro GenericRecord.
    * 
    * @return True if connected to a Schema registry, false otherwise.
    */
   public boolean isRegistryEnabled() {
      return registryProducer != null;
   }

   @PostConstruct
   public void create() {
      producer = createProducer();
      bytesProducer = createBytesProducer();
      registryProducer = createRegistryProducer();
   }

   protected Producer<String, String> createProducer() {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-str-producer");
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      return new KafkaProducer<>(props);
   }

   protected Producer<String, byte[]> createBytesProducer() {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-bytes-producer");
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
      return new KafkaProducer<>(props);
   }

   protected Producer<String, GenericRecord> createRegistryProducer() {
      if (schemaRegistryUrl.isPresent() && !schemaRegistryUrl.isEmpty()) {
         Properties props = new Properties();
         props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
         props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-registry-producer");
         props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

         // Configure Schema registry access.
         if (schemaRegistryConfluent) {
            // Put Confluent Registry specific SerDes class and registry properties.
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());

            props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl.get());
            // If authentication turned on (see
            // https://docs.confluent.io/platform/current/security/basic-auth.html#basic-auth-sr)
            if (schemaRegistryUsername.isPresent() && !schemaRegistryUsername.isEmpty()) {
               props.put(AbstractKafkaAvroSerDeConfig.USER_INFO_CONFIG, schemaRegistryUsername.get());
               props.put(AbstractKafkaAvroSerDeConfig.BASIC_AUTH_CREDENTIALS_SOURCE, schemaRegistryCredentialsSource);
            }
            props.put(AbstractKafkaAvroSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
            // Map the topic name to the artifactId in the registry
            props.put(AbstractKafkaAvroSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
                  io.confluent.kafka.serializers.subject.TopicRecordNameStrategy.class.getName());
         } else {
            // Put Apicurio Registry specific SerDes class and registry properties.
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());

            props.put(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, schemaRegistryUrl.get());
            props.put(AbstractKafkaSerDe.REGISTRY_CONFLUENT_ID_HANDLER_CONFIG_PARAM, true);
            // Get an existing schema or auto-register if not found.
            props.put(AbstractKafkaSerializer.REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM,
                  io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy.class.getName());
            // Set artifact strategy as the same as Confluent default subject strategy.
            props.put(AbstractKafkaSerializer.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM,
                  io.apicurio.registry.utils.serde.strategy.TopicIdStrategy.class.getName());
         }

         return new KafkaProducer<>(props);
      }
      return null;
   }

   /**
    * Publish a message on specified topic.
    * 
    * @param topic   The destination topic for message
    * @param key     The message key
    * @param value   The message payload
    * @param headers A set of headers if any (maybe null or empty)
    */
   public void publishMessage(String topic, String key, String value, Set<Header> headers) {
      logger.infof("Publishing on topic {%s}, message: %s ", topic, value);
      ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
      addHeadersToRecord(record, headers);
      producer.send(record);
      producer.flush();
   }

   /**
    * Publish a raw byte array message on specified topic.
    * 
    * @param topic   The destination topic for message
    * @param key     The message key
    * @param value   The message payload
    * @param headers A set of headers if any (maybe null or empty)
    */
   private void publishMessage(String topic, String key, byte[] value, Set<Header> headers) {
      logger.infof("Publishing on topic {%s}, bytes: %s ", topic, new String(value));
      ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, key, value);
      addHeadersToRecord(record, headers);
      bytesProducer.send(record);
      bytesProducer.flush();
   }

   /**
    * Publish an Avro GenericRecord built with Schema onto specified topic and using underlying schema
    * registry.
    * 
    * @param topic   The destination topic for message
    * @param key     The message key
    * @param value   The message payload
    * @param headers A set of headers if any (maybe null or empty)
    */
   public void publishMessage(String topic, String key, GenericRecord value, Set<Header> headers) {
      logger.infof("Publishing on topic {%s}, record: %s ", topic, value.toString());
      ProducerRecord<String, GenericRecord> record = new ProducerRecord<>(topic, key, value);
      addHeadersToRecord(record, headers);
      registryProducer.send(record);
      registryProducer.flush();
   }

   /**
    * Transform and render Microcks headers into Kafka specific headers.
    * 
    * @param engine  The template engine to reuse (because we do not want to initialize and manage a
    *                context at the this level.)
    * @param headers The Microcks event message headers definition.
    * @return A set of Kafka headers.
    */
   public Set<Header> renderEventMessageHeaders(TemplateEngine engine, Set<io.github.microcks.domain.Header> headers) {
      if (headers != null && !headers.isEmpty()) {
         Set<Header> renderedHeaders = new HashSet<>(headers.size());

         for (io.github.microcks.domain.Header header : headers) {
            // For Kafka, header is mono valued so just consider the first value.
            String firstValue = header.getValues().stream().findFirst().get();
            if (firstValue.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
               try {
                  renderedHeaders.add(new RecordHeader(header.getName(), engine.getValue(firstValue).getBytes()));
               } catch (Throwable t) {
                  logger.error("Failing at evaluating template " + firstValue, t);
                  renderedHeaders.add(new RecordHeader(header.getName(), firstValue.getBytes()));
               }
            } else {
               renderedHeaders.add(new RecordHeader(header.getName(), firstValue.getBytes()));
            }
         }
         return renderedHeaders;
      }
      return null;
   }

   /**
    * Get the Kafka topic name corresponding to a AsyncMockDefinition, sanitizing all parameters.
    */
   @Override
   public String transformToBindingSpecificTopicName(String topic) {
      return topic.replace('/', '-');
   }

   /** Completing the ProducerRecord with the set of provided headers. */
   protected void addHeadersToRecord(@SuppressWarnings("rawtypes") ProducerRecord record, Set<Header> headers) {
      if (headers != null) {
         for (Header header : headers) {
            record.headers().add(header);
         }
      }
   }

   @Override
   protected void publishOne(AsyncMockDefinition definition, EventMessage eventMessage, String topic) {
      String key = String.valueOf(System.currentTimeMillis());
      String message = renderEventMessageContent(eventMessage);

      // Check it Avro binary is expected, we should convert to bytes.
      if ("avro/binary".equals(eventMessage.getMediaType())) {
         // Build the name of expected schema.
         String schemaName = IdBuilder.buildResourceFullName(definition.getOwnerService(), definition.getOperation());
         String schemaContent = schemaRegistry.getSchemaEntryContent(definition.getOwnerService(), schemaName);

         try {
            if ("REGISTRY".equals(defaultAvroEncoding) && this.isRegistryEnabled()) {
               GenericRecord avroRecord = AvroUtil.jsonToAvroRecord(message, schemaContent);
               this.publishMessage(topic, key, avroRecord,
                     this.renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
            } else {
               byte[] avroBinary = AvroUtil.jsonToAvro(message, schemaContent);
               this.publishMessage(topic, key, avroBinary,
                     this.renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
            }
         } catch (Exception e) {
            logger.errorf("Exception while converting {%s} to Avro using schema {%s}", message, schemaContent, e);
         }
      } else {
         this.publishMessage(topic, key, message,
               this.renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
      }
   }

}
