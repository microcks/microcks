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
import org.jboss.logging.Logger;

import com.ibm.mq.MQEnvironment;
import com.ibm.mq.MQException;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.MQTopic;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * IBM MQ implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class IBMMQProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** Destination type used in AsyncAPI IBM MQ binding to represent a publish/subscribe topic. */
   private static final String TOPIC_DESTINATION_TYPE = "topic";

   private MQQueueManager queueManager;

   @ConfigProperty(name = "ibmmq.server")
   String ibmmqServer;

   @ConfigProperty(name = "ibmmq.queue-manager")
   String queueManagerName;

   @ConfigProperty(name = "ibmmq.channel", defaultValue = "DEV.APP.SVRCONN")
   String ibmmqChannel;

   @ConfigProperty(name = "ibmmq.username")
   Optional<String> ibmmqUsername;

   @ConfigProperty(name = "ibmmq.password")
   Optional<String> ibmmqPassword;

   /**
    * Initialize the IBM MQ client post construction.
    * @throws Exception If connection to IBM MQ Broker cannot be done.
    */
   @PostConstruct
   public void create() throws Exception {
      try {
         queueManager = createClient();
      } catch (Exception e) {
         logger.errorf("Cannot connect to IBM MQ broker %s", ibmmqServer);
         logger.errorf("Connection exception: %s", e.getMessage());
         throw e;
      }
   }

   /**
    * Create a MQQueueManager and connect it to the server.
    * @return A new MQQueueManager implementation initialized with configuration properties.
    * @throws Exception in case of connection failure
    */
   protected MQQueueManager createClient() throws Exception {
      String host = ibmmqServer;
      int port = 1414;
      if (host.contains(":")) {
         String[] parts = host.split(":");
         host = parts[0];
         port = Integer.parseInt(parts[1]);
      }

      MQEnvironment.hostname = host;
      MQEnvironment.port = port;
      MQEnvironment.channel = ibmmqChannel;

      if (ibmmqUsername.isPresent() && !ibmmqUsername.get().isEmpty() && ibmmqPassword.isPresent()
            && !ibmmqPassword.get().isEmpty()) {
         logger.infof("Connecting to IBM MQ broker with user '%s'", ibmmqUsername.get());
         MQEnvironment.userID = ibmmqUsername.get();
         MQEnvironment.password = ibmmqPassword.get();
      }

      return new MQQueueManager(queueManagerName);
   }

   /**
    * Publish a message on the specified destination. IBM MQ supports both point-to-point (queue) and publish/subscribe
    * (topic) messaging: the destination type coming from the AsyncAPI IBM MQ binding tells which one to use.
    * @param destinationType The type of destination ('queue' or 'topic'); defaults to a queue when null or unknown
    * @param destinationName The name of the queue or the topic string to publish onto
    * @param value           The message payload
    */
   public void publishMessage(String destinationType, String destinationName, String value) {
      if (queueManager == null) {
         logger.warn("IBM MQ queueManager is not initialized, ignoring publish.");
         return;
      }

      if (TOPIC_DESTINATION_TYPE.equalsIgnoreCase(destinationType)) {
         publishToTopic(destinationName, value);
      } else {
         publishToQueue(destinationName, value);
      }
   }

   /**
    * Publish a message on the specified queue, creating it on the fly if it does not exist yet.
    * @param queueName The destination queue for message
    * @param value     The message payload
    */
   protected void publishToQueue(String queueName, String value) {
      logger.infof("Publishing on queue {%s}, message: %s ", queueName, value);

      MQQueue queue = null;
      try {
         int openOptions = CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING;
         queue = accessOrCreateQueue(queueName, openOptions);

         MQMessage message = new MQMessage();
         message.write(value.getBytes(StandardCharsets.UTF_8));

         queue.put(message);
      } catch (Exception e) {
         logger.warn("Exception caught while publishing message to IBM MQ queue", e);
      } finally {
         if (queue != null) {
            try {
               queue.close();
            } catch (MQException e) {
               logger.warn("Exception caught while closing IBM MQ queue", e);
            }
         }
      }
   }

   /**
    * Publish a message on the specified topic string. Unlike queues, topics do not need to be provisioned beforehand:
    * IBM MQ resolves the topic string against the topic tree and delivers the publication to matching subscribers.
    * @param topicString The destination topic string for message
    * @param value       The message payload
    */
   protected void publishToTopic(String topicString, String value) {
      logger.infof("Publishing on topic {%s}, message: %s ", topicString, value);

      MQTopic topic = null;
      try {
         int openOptions = CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING;
         topic = queueManager.accessTopic(topicString, "", CMQC.MQTOPIC_OPEN_AS_PUBLICATION, openOptions);

         MQMessage message = new MQMessage();
         message.write(value.getBytes(StandardCharsets.UTF_8));

         topic.put(message);
      } catch (Exception e) {
         logger.warn("Exception caught while publishing message to IBM MQ topic", e);
      } finally {
         if (topic != null) {
            try {
               topic.close();
            } catch (MQException e) {
               logger.warn("Exception caught while closing IBM MQ topic", e);
            }
         }
      }
   }

   /**
    * Access a queue, creating it as a local queue if it does not exist yet. IBM MQ does not create destinations on the
    * fly (unlike some other brokers), so we take care of provisioning the queue the first time it is used.
    * @param queueName   The name of the queue to access
    * @param openOptions The MQ open options to use when accessing the queue
    * @return An opened {@link MQQueue} ready for use
    * @throws MQException if the queue cannot be accessed nor created
    */
   protected MQQueue accessOrCreateQueue(String queueName, int openOptions) throws MQException {
      try {
         return queueManager.accessQueue(queueName, openOptions);
      } catch (MQException e) {
         if (e.reasonCode == CMQC.MQRC_UNKNOWN_OBJECT_NAME) {
            logger.infof("Queue {%s} does not exist yet, creating a new local queue", queueName);
            createLocalQueue(queueName);
            return queueManager.accessQueue(queueName, openOptions);
         }
         throw e;
      }
   }

   /**
    * Create a new local queue on the connected queue manager using a PCF administration command.
    * @param queueName The name of the local queue to create
    */
   protected void createLocalQueue(String queueName) {
      PCFMessageAgent agent = null;
      try {
         agent = new PCFMessageAgent(queueManager);
         PCFMessage request = new PCFMessage(CMQCFC.MQCMD_CREATE_Q);
         request.addParameter(CMQC.MQCA_Q_NAME, queueName);
         request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_LOCAL);
         agent.send(request);
      } catch (Exception e) {
         throw new IllegalStateException("Cannot create IBM MQ queue " + queueName, e);
      } finally {
         if (agent != null) {
            try {
               agent.disconnect();
            } catch (Exception e) {
               logger.warn("Exception caught while disconnecting IBM MQ PCF agent", e);
            }
         }
      }
   }

   /**
    * Get the IBM MQ destination name corresponding to a AsyncMockDefinition, sanitizing all parameters. The same
    * deterministic name is used both as a queue name and as a topic string, depending on the binding destination type.
    * @param definition   The AsyncMockDefinition
    * @param eventMessage The message to get destination name
    * @return The destination name for definition and event
    */
   public String getDestinationName(AsyncMockDefinition definition, EventMessage eventMessage) {
      // Produce service name part of destination name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");

      // Produce version name part of destination name.
      String versionName = definition.getOwnerService().getVersion().replace(" ", "");

      // Produce operation name part of destination name.
      String operationName = ProducerManager.getDestinationOperationPart(definition.getOperation(), eventMessage);

      // Aggregate the 3 parts using '_' as delimiter. IBM MQ object names do not allow the '-' character, so we rely on
      // the underscore that is part of the valid characters set (letters, digits, '.', '/', '_', '%').
      return serviceName + "_" + versionName + "_" + operationName;
   }
}
