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

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;

/**
 * MQTT implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class MQTTProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private IMqttClient client;

   @ConfigProperty(name = "mqtt.server")
   String mqttServer;

   @ConfigProperty(name = "mqtt.clientid", defaultValue = "microcks-async-minion")
   String mqttClientId;

   @ConfigProperty(name = "mqtt.username")
   String mqttUsername;

   @ConfigProperty(name = "mqtt.password")
   String mqttPassword;

   /**
    * Initialize the MQTT client post construction.
    * @throws Exception If connection to MQTT Broker cannot be done.
    */
   @PostConstruct
   public void create() throws Exception {
      try {
         client = createClient();
      } catch (Exception e) {
         logger.errorf("Cannot connect to MQTT broker %s", mqttServer);
         logger.errorf("Connection exception: %s", e.getMessage());
         throw e;
      }
   }

   /**
    * Create a IMqttClient and connect it to the server.
    * @return A new IMqttClient implementation initialized with configuration properties.
    * @throws Exception in case of connection failure
    */
   protected IMqttClient createClient() throws Exception {
      MqttConnectOptions options = new MqttConnectOptions();
      if (mqttUsername != null && !mqttUsername.isEmpty() && mqttPassword != null && !mqttPassword.isEmpty()) {
         logger.infof("Connecting to MQTT broker with user '%s'", mqttUsername);
         options.setUserName(mqttUsername);
         options.setPassword(mqttPassword.toCharArray());
      }
      options.setAutomaticReconnect(true);
      // Set clean session to false as we're reusing the same clientId.
      options.setCleanSession(false);
      options.setConnectionTimeout(20);
      options.setKeepAliveInterval(10);
      IMqttClient publisher = new MqttClient("tcp://" + mqttServer, mqttClientId);
      publisher.connect(options);
      return publisher;
   }

   /**
    * Publish a message on specified topic.
    * @param topic The destination topic for message
    * @param value The message payload
    */
   public void publishMessage(String topic, String value) {
      logger.infof("Publishing on topic {%s}, message: %s ", topic, value);
      try {
         client.publish(topic, value.getBytes(StandardCharsets.UTF_8), 0, false);
      } catch (MqttPersistenceException mpe) {
         logger.warnf("MqttPersistenceException caught while publishing message, ignoring it", mpe);
      } catch (MqttException me) {
         logger.warnf("MqttException caught while publishing messageMqttException", me);
      }
   }

   /**
    * Get the MQTT topic name corresponding to a AsyncMockDefinition, sanitizing all parameters.
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
