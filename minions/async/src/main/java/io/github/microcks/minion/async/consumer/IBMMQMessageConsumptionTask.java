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

import com.ibm.mq.MQDestination;
import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a queue or a topic on an IBM MQ broker.
 * Endpoint URL should be specified using the following form:
 * <code>ibmmq://{brokerhost[:port]}/{queueManager}/{type}/{destination}[?channel=channelName]</code> where
 * <code>type</code> is either <code>queue</code> (point-to-point consumption) or <code>topic</code> (publish/subscribe
 * consumption through a managed, non-durable subscription).
 * @author laurent
 */
public class IBMMQMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating acceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "ibmmq://(?<brokerUrl>[^/]+)/(?<queueManager>[^/]+)/(?<type>queue|topic)/(?<destination>[^?]+)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   /** Constant representing a queue destination type in endpoint URL. */
   public static final String QUEUE_TYPE = "queue";
   /** Constant representing a topic destination type in endpoint URL. */
   public static final String TOPIC_TYPE = "topic";

   /** The endpoint URL option representing the server connection channel to use. */
   public static final String CHANNEL_OPTION = "channel";
   /** The default server connection channel when none is specified in endpoint URL. */
   public static final String DEFAULT_CHANNEL = "DEV.APP.SVRCONN";

   private File trustStore;

   private final AsyncTestSpecification specification;

   private MQQueueManager queueManager;
   private MQDestination destination;

   /**
    * Create a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public IBMMQMessageConsumptionTask(AsyncTestSpecification testSpecification) {
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
      if (queueManager == null) {
         initializeIBMMQClient();
      }
      List<ConsumedMessage> messages = new ArrayList<>();

      long timeoutTime = System.currentTimeMillis() + specification.getTimeoutMS();

      // Poll messages until timeout
      while (System.currentTimeMillis() < timeoutTime) {
         try {
            MQMessage message = new MQMessage();
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = CMQC.MQGMO_WAIT | CMQC.MQGMO_FAIL_IF_QUIESCING;
            // Only wait for the remaining time so that the last poll never overshoots the configured timeout.
            gmo.waitInterval = (int) (timeoutTime - System.currentTimeMillis());

            destination.get(message, gmo);

            byte[] b = new byte[message.getMessageLength()];
            message.readFully(b);

            logger.info("Received a new IBM MQ Message: " + new String(b));

            ConsumedMessage consumedMessage = new ConsumedMessage();
            consumedMessage.setReceivedAt(System.currentTimeMillis());
            consumedMessage.setPayload(b);
            messages.add(consumedMessage);

         } catch (MQException e) {
            if (e.reasonCode == CMQC.MQRC_NO_MSG_AVAILABLE) {
               // No message available, continue polling
               continue;
            }
            logger.error("Error receiving message from IBM MQ", e);
            break;
         }
      }

      // Disconnect
      close();
      return messages;
   }

   /**
    * Close the resources used by this task. Namely the IBM MQ destination (queue or topic subscription), the queue
    * manager connection and the optionally created truststore holding server client SSL credentials.
    * @throws IOException should not happen.
    */
   @Override
   public void close() throws IOException {
      if (destination != null) {
         try {
            destination.close();
         } catch (MQException e) {
            logger.warn("Closing IBM MQ destination raised an exception", e);
         }
      }
      if (queueManager != null) {
         try {
            queueManager.disconnect();
         } catch (MQException e) {
            logger.warn("Disconnecting IBM MQ QueueManager raised an exception", e);
         }
      }
      if (trustStore != null && trustStore.exists()) {
         Files.delete(trustStore.toPath());
      }
   }

   /** Initialize the IBM MQ client from endpoint URL and open the destination to consume. */
   private void initializeIBMMQClient() throws Exception {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      // Call matcher.find() to be able to use named expressions.
      matcher.find();
      String endpointBrokerUrl = matcher.group("brokerUrl");
      String queueManagerName = matcher.group("queueManager");
      String destinationType = matcher.group("type");
      String destinationName = matcher.group("destination");
      String options = matcher.group("options");

      String host = endpointBrokerUrl;
      int port = 1414;
      if (endpointBrokerUrl.contains(":")) {
         String[] parts = endpointBrokerUrl.split(":");
         host = parts[0];
         port = Integer.parseInt(parts[1]);
      }

      String channel = DEFAULT_CHANNEL;
      if (options != null) {
         for (String option : options.split("&")) {
            if (option.startsWith(CHANNEL_OPTION + "=")) {
               channel = option.substring(CHANNEL_OPTION.length() + 1);
            }
         }
      }

      MQEnvironment.hostname = host;
      MQEnvironment.port = port;
      MQEnvironment.channel = channel;

      if (specification.getSecret() != null) {
         if (specification.getSecret().getUsername() != null && specification.getSecret().getPassword() != null) {
            logger.debug("Adding username/password authentication from secret " + specification.getSecret().getName());
            MQEnvironment.userID = specification.getSecret().getUsername();
            MQEnvironment.password = specification.getSecret().getPassword();
         }

         if (specification.getSecret().getCaCertPem() != null) {
            logger.debug("Installing a broker certificate from secret " + specification.getSecret().getName());
            trustStore = ConsumptionTaskCommons.installBrokerCertificate(specification);

            System.setProperty("javax.net.ssl.trustStore", trustStore.getAbsolutePath());
            System.setProperty("javax.net.ssl.trustStorePassword", ConsumptionTaskCommons.TRUSTSTORE_PASSWORD);
            MQEnvironment.sslCipherSuite = "TLS_RSA_WITH_AES_128_CBC_SHA256"; // Default or needs to be provided
         }
      }

      queueManager = new MQQueueManager(queueManagerName);

      if (TOPIC_TYPE.equals(destinationType)) {
         logger.infof("Subscribing to IBM MQ topic {%s} on queue manager {%s}", destinationName, queueManagerName);
         // Open a managed, non-durable subscription: the queue manager provisions the subscription queue for us and
         // discards it once closed, so that the test does not leave anything behind on the broker.
         int subscriptionOptions = CMQC.MQSO_CREATE | CMQC.MQSO_MANAGED | CMQC.MQSO_NON_DURABLE
               | CMQC.MQSO_FAIL_IF_QUIESCING;
         destination = queueManager.accessTopic(destinationName, "", CMQC.MQTOPIC_OPEN_AS_SUBSCRIPTION,
               subscriptionOptions);
      } else {
         logger.infof("Opening IBM MQ queue {%s} on queue manager {%s}", destinationName, queueManagerName);
         int openOptions = CMQC.MQOO_INPUT_AS_Q_DEF | CMQC.MQOO_FAIL_IF_QUIESCING;
         destination = queueManager.accessQueue(destinationName, openOptions);
      }
   }
}
