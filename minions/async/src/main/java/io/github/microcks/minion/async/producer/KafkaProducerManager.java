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

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.TemplateEngine;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

/**
 * Kafka implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class KafkaProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private Producer<String, String> producer;

   private Producer<String, byte[]> bytesProducer;

   private Producer<String, GenericRecord> registryProducer;

   @Inject
   Config config;

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
      // Add security related properties.
      completePropertiesWithSecurityConfig(props, config);
      return new KafkaProducer<>(props);
   }

   protected Producer<String, byte[]> createBytesProducer() {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-bytes-producer");
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
      // Add security related properties.
      completePropertiesWithSecurityConfig(props, config);
      return new KafkaProducer<>(props);
   }

   protected Producer<String, GenericRecord> createRegistryProducer() {
      if (schemaRegistryUrl.isPresent()) {
         Properties props = new Properties();
         props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
         props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-registry-producer");
         props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

         // Configure Schema registry access.
         if (schemaRegistryConfluent) {
            // Put Confluent Registry specific SerDes class and registry properties.
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());

            props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl.get());
            // If authentication turned on (see https://docs.confluent.io/platform/current/security/basic-auth.html#basic-auth-sr)
            if (schemaRegistryUsername.isPresent()) {
               props.put(AbstractKafkaSchemaSerDeConfig.USER_INFO_CONFIG, schemaRegistryUsername.get());
               props.put(AbstractKafkaSchemaSerDeConfig.BASIC_AUTH_CREDENTIALS_SOURCE, schemaRegistryCredentialsSource);
            }
            props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
            // Map the topic name to the artifactId in the registry
            props.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
                  io.confluent.kafka.serializers.subject.TopicRecordNameStrategy.class.getName());
         } else {
            // Put Apicurio Registry specific SerDes class and registry properties.
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());

            props.put(SerdeConfig.REGISTRY_URL, schemaRegistryUrl.get());
            props.put(SerdeConfig.ENABLE_CONFLUENT_ID_HANDLER, true);
            // Get an existing schema or auto-register if not found.
            props.put(SerdeConfig.SCHEMA_RESOLVER, io.apicurio.registry.resolver.DefaultSchemaResolver.class.getName());
            props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, true);
            // Set artifact strategy as the same as Confluent default subject strategy.
            props.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY,
                  io.apicurio.registry.serde.strategy.TopicIdStrategy.class.getName());
         }

         // Add security related properties.
         completePropertiesWithSecurityConfig(props, config);
         return new KafkaProducer<>(props);
      }
      return null;
   }

   /**
    * Publish a message on specified topic.
    * @param topic   The destination topic for message
    * @param key     The message key
    * @param value   The message payload
    * @param headers A set of headers if any (maybe null or empty)
    */
   public void publishMessage(String topic, String key, String value, Set<Header> headers) {
      logger.infof("Publishing on topic {%s}, message: %s ", topic, value);
      ProducerRecord<String, String> kafkaRecord = new ProducerRecord<>(topic, key, value);
      addHeadersToRecord(kafkaRecord, headers);
      producer.send(kafkaRecord);
      producer.flush();
   }

   /**
    * Publish a raw byte array message on specified topic.
    * @param topic   The destination topic for message
    * @param key     The message key
    * @param value   The message payload
    * @param headers A set of headers if any (maybe null or empty)
    */
   public void publishMessage(String topic, String key, byte[] value, Set<Header> headers) {
      logger.infof("Publishing on topic {%s}, bytes: %s ", topic, new String(value));
      ProducerRecord<String, byte[]> kafkaRecord = new ProducerRecord<>(topic, key, value);
      addHeadersToRecord(kafkaRecord, headers);
      bytesProducer.send(kafkaRecord);
      bytesProducer.flush();
   }

   /**
    * Publish an Avro GenericRecord built with Schema onto specified topic and using underlying schema registry.
    * @param topic   The destination topic for message
    * @param key     The message key
    * @param value   The message payload
    * @param headers A set of headers if any (maybe null or empty)
    */
   public void publishMessage(String topic, String key, GenericRecord value, Set<Header> headers) {
      logger.infof("Publishing on topic {%s}, record: %s ", topic, value.toString());
      ProducerRecord<String, GenericRecord> kafkaRecord = new ProducerRecord<>(topic, key, value);
      addHeadersToRecord(kafkaRecord, headers);
      registryProducer.send(kafkaRecord);
      registryProducer.flush();
   }

   /**
    * Transform and render Microcks headers into Kafka specific headers.
    * @param engine  The template engine to reuse (because we do not want to initialize and manage a context at the
    *                KafkaProducerManager level.)
    * @param headers The Microcks event message headers definition.
    * @return A set of Kafka headers.
    */
   public Set<Header> renderEventMessageHeaders(TemplateEngine engine, Set<io.github.microcks.domain.Header> headers) {
      if (headers != null && !headers.isEmpty()) {
         Set<Header> renderedHeaders = HashSet.newHashSet(headers.size());

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

      // Aggregate the 3 parts using '_' as delimiter.
      return serviceName + "-" + versionName + "-" + operationName;
   }

   /**
    * Completing the ProducerRecord with the set of provided headers.
    * @param kafkaRecord The record to complete
    * @param headers     The set of headers
    */
   protected void addHeadersToRecord(ProducerRecord<?, ?> kafkaRecord, Set<Header> headers) {
      if (headers != null) {
         for (Header header : headers) {
            kafkaRecord.headers().add(header);
         }
      }
   }

   private void completePropertiesWithSecurityConfig(Properties props, Config config) {
      Optional<String> securityProtocol = config.getOptionalValue("kafka.security.protocol", String.class);
      if (securityProtocol.isPresent()) {
         String securityProtocolValue = securityProtocol.get();
         props.put("security.protocol", securityProtocolValue);

         if (config.getOptionalValue("kafka.ssl.truststore.location", String.class).isPresent()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                  config.getValue("kafka.ssl.truststore.location", String.class));
         }
         if (config.getOptionalValue("kafka.ssl.truststore.password", String.class).isPresent()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
                  config.getValue("kafka.ssl.truststore.password", String.class));
         }
         if (config.getOptionalValue("kafka.ssl.truststore.type", String.class).isPresent()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,
                  config.getValue("kafka.ssl.truststore.type", String.class));
         }

         switch (securityProtocolValue) {
            case "SASL_SSL":
               logger.debug("Adding SASL_SSL specific connection properties");
               props.put(SaslConfigs.SASL_MECHANISM, config.getValue("kafka.sasl.mechanism", String.class));
               props.put(SaslConfigs.SASL_JAAS_CONFIG, config.getValue("kafka.sasl.jaas.config", String.class));
               if (config.getOptionalValue("kafka.sasl.login.callback.handler.class", String.class).isPresent()) {
                  props.put(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS,
                        config.getValue("kafka.sasl.login.callback.handler.class", String.class));
               }
               break;
            case "SSL":
               logger.debug("Adding SSL specific connection properties");
               props.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
                     config.getValue("kafka.ssl.keystore.location", String.class));
               props.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
                     config.getValue("kafka.ssl.keystore.password", String.class));
               props.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, config.getValue("kafka.ssl.keystore.type", String.class));
               break;
            default:
               break;
         }
      }
   }
}
