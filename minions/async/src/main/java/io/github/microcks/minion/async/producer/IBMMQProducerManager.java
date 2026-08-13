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
import com.ibm.mq.constants.CMQC;

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

   private MQQueueManager queueManager;

   @ConfigProperty(name = "ibmmq.server")
   Optional<String> ibmmqServer;

   @ConfigProperty(name = "ibmmq.queue-manager")
   Optional<String> queueManagerName;

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
         if (ibmmqServer.isPresent() && !ibmmqServer.get().isEmpty()) {
            queueManager = createClient();
         }
      } catch (Exception e) {
         logger.errorf("Cannot connect to IBM MQ broker %s", ibmmqServer.orElse(""));
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
      String host = ibmmqServer.get();
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

      return new MQQueueManager(queueManagerName.orElse("QM1"));
   }

   /**
    * Publish a message on specified queue.
    * @param queueName The destination queue for message
    * @param value     The message payload
    */
   public void publishMessage(String queueName, String value) {
      logger.infof("Publishing on queue {%s}, message: %s ", queueName, value);

      if (queueManager == null) {
         logger.warn("IBM MQ queueManager is not initialized, ignoring publish.");
         return;
      }

      MQQueue queue = null;
      try {
         int openOptions = CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING;
         queue = queueManager.accessQueue(queueName, openOptions);

         MQMessage message = new MQMessage();
         message.write(value.getBytes(StandardCharsets.UTF_8));

         queue.put(message);
      } catch (MQException | java.io.IOException e) {
         logger.warnf("Exception caught while publishing message to IBM MQ", e);
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
    * Get the IBM MQ queue name corresponding to a AsyncMockDefinition, sanitizing all parameters.
    * @param definition   The AsyncMockDefinition
    * @param eventMessage The message to get queue name
    * @return The queue name for definition and event
    */
   public String getTopicName(AsyncMockDefinition definition, EventMessage eventMessage) {
      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");

      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" ", "");

      // Produce operation name part of topic name.
      String operationName = ProducerManager.getDestinationOperationPart(definition.getOperation(), eventMessage);

      // Aggregate the 3 parts using '-' as delimiter.
      return serviceName + "-" + versionName + "-" + operationName;
   }
}
