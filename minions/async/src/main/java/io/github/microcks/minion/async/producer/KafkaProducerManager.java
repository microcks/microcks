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

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;

import java.util.Properties;

/**
 * Kafka implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class KafkaProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private Producer<String, String> producer;

   @ConfigProperty(name = "kafka.bootstrap.servers")
   String bootstrapServers;

   @PostConstruct
   public void create() {
      producer = createProducer();
   }

   protected Producer<String, String> createProducer() {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion");
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      return new KafkaProducer<>(props);
   }

   /**
    * Publish a message on specified topic.
    * @param topic The destination topic for message
    * @param key The message key
    * @param value The message payload
    */
   public void publishMessage(String topic, String key, String value) {
      logger.infof("Publishing on topic {%s}, message: %s ", topic, value);
      producer.send(new ProducerRecord<String, String>(topic, key, value));
      producer.flush();
   }
}