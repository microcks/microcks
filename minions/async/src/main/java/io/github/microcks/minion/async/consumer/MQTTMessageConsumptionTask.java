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
package io.github.microcks.minion.async.consumer;

import io.github.microcks.minion.async.AsyncTestSpecification;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a topic on an MQTT 3.1 Server. Endpoint URL
 * should be specified using the following form:
 * <code>mqtt://{brokerhost[:port]}/{topic}[?option1=value1&amp;option2=value2]</code>
 * @author laurent
 */
public class MQTTMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating acceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "mqtt://(?<brokerUrl>[^:]+(:\\d+)?)/(?<topic>.+)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   private File trustStore;

   private final AsyncTestSpecification specification;

   private IMqttClient subscriber;

   private String endpointTopic;

   private String options;

   /**
    * Create a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public MQTTMessageConsumptionTask(AsyncTestSpecification testSpecification) {
      this.specification = testSpecification;
   }

   /**
    * Convenient static method for checking if this implementation will accept endpoint.
    * @param endpointUrl The endpoint URL to validate
    * @return True if endpointUrl can be used for connecting and consuming on endpoint
    */
   public static boolean acceptEndpoint(String endpointUrl) {
      return endpointUrl != null && endpointUrl.matches(ENDPOINT_PATTERN_STRING);
   }

   @Override
   public List<ConsumedMessage> call() throws Exception {
      if (subscriber == null) {
         intializeMQTTClient();
      }
      List<ConsumedMessage> messages = new ArrayList<>();

      // Start subscribing to the server endpoint topic.
      subscriber.subscribe(endpointTopic, (topic, mqttMessage) -> {
         logger.info("Received a new MQTT Message: " + new String(mqttMessage.getPayload()));
         // Build a ConsumedMessage from MQTT message.
         ConsumedMessage message = new ConsumedMessage();
         message.setReceivedAt(System.currentTimeMillis());
         message.setPayload(mqttMessage.getPayload());
         messages.add(message);
      });

      Thread.sleep(specification.getTimeoutMS());

      // Disconnect the subscriber before returning results.
      subscriber.disconnect();
      return messages;
   }

   /**
    * Close the resources used by this task. Namely the MQTT subscriber and the optionally created truststore holding
    * server client SSL credentials.
    * @throws IOException should not happen.
    */
   @Override
   public void close() throws IOException {
      if (subscriber != null) {
         try {
            subscriber.close();
         } catch (MqttException e) {
            logger.warn("Closing MQTT subscriber raised an exception", e);
         }
      }
      if (trustStore != null && trustStore.exists()) {
         Files.delete(trustStore.toPath());
      }
   }

   /** */
   private void intializeMQTTClient() throws Exception {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      // Call matcher.find() to be able to use named expressions.
      matcher.find();
      String endpointBrokerUrl = matcher.group("brokerUrl");
      endpointTopic = matcher.group("topic");
      options = matcher.group("options");

      MqttConnectOptions connectOptions = new MqttConnectOptions();
      connectOptions.setAutomaticReconnect(false);
      connectOptions.setCleanSession(true);
      connectOptions.setConnectionTimeout(10);

      // Initialize default protocol pragma for connection string.
      String protocolPragma = "tcp://";

      if (specification.getSecret() != null) {
         if (specification.getSecret().getUsername() != null && specification.getSecret().getPassword() != null) {
            logger.debug("Adding username/password authentication from secret " + specification.getSecret().getName());
            connectOptions.setUserName(specification.getSecret().getUsername());
            connectOptions.setPassword(specification.getSecret().getPassword().toCharArray());
         }

         if (specification.getSecret().getCaCertPem() != null) {
            logger.debug("Installing a broker certificate from secret " + specification.getSecret().getName());
            trustStore = ConsumptionTaskCommons.installBrokerCertificate(specification);

            // Find the list of SSL properties here:
            // https://www.eclipse.org/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttConnectOptions.html#setSSLProperties-java.util.Properties-
            Properties sslProperties = new Properties();
            sslProperties.put("com.ibm.ssl.trustStore", trustStore.getAbsolutePath());
            sslProperties.put("com.ibm.ssl.trustStorePassword", ConsumptionTaskCommons.TRUSTSTORE_PASSWORD);
            sslProperties.put("com.ibm.ssl.trustStoreType", "JKS");
            connectOptions.setSSLProperties(sslProperties);

            // We also have to change the prococolPragma to ssl://
            protocolPragma = "ssl://";
         }
      }

      // Create the subscriber and connect it to server using the properties.
      // Generate a unique clientId for each publication to avoid collisions (cleanSession is true).
      subscriber = new MqttClient(protocolPragma + endpointBrokerUrl,
            "microcks-async-minion-test-" + System.currentTimeMillis());
      subscriber.connect(connectOptions);
   }
}
