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
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;

import com.ibm.mq.MQDestination;
import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.MQTopic;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.testcontainers.MQContainer;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> and the
 * <a href="https://github.com/ibm-messaging/mq-jms-spring/tree/master/mq-java-testcontainer">IBM MQ Testcontainers
 * module</a> to test {@link IBMMQProducerManager} and {@link ProducerManager} classes.
 * @author laurent
 */
@Testcontainers
class IBMMQProducerManagerIT {

   private static final String QUEUE_MANAGER = "QM1";
   private static final String ADMIN_CHANNEL = "DEV.ADMIN.SVRCONN";
   private static final String ADMIN_USER = "admin";
   private static final String ADMIN_PASSWORD = "passw0rd";

   /** The destination name is the one deterministically produced by {@link IBMMQProducerManager#getDestinationName}. */
   private static final String DESTINATION_NAME = "UsersignedupAPI_0.3.0_user/signedup";

   private static final String ALICE_CONTENT = "{\"fullName\": \"Alice\", \"email\": \"alice@acme.com\", \"age\": 30}";
   private static final String BOB_CONTENT = "{\"fullName\": \"Bod\", \"email\": \"bod@acme.com\", \"age\": 35}";

   @Container
   private static final MQContainer mqContainer = new MQContainer(
         DockerImageName.parse("icr.io/ibm-messaging/mq:9.3.2.0-r2")).acceptLicense().withEnv("MQ_ADMIN_PASSWORD",
               ADMIN_PASSWORD);

   @Test
   void testProduceMockMessagesToQueue() throws Exception {
      // Arrange.
      TestContext context = arrange();

      // IBM MQ binding targeting a point-to-point queue.
      Binding binding = new Binding(BindingType.IBMMQ);
      binding.setDestinationType("queue");

      // Ensure the producer targets the destination name we expect (queue will be created on the fly if needed).
      assertEquals(DESTINATION_NAME,
            context.ibmmqProducerManager.getDestinationName(context.mockDefinition, context.aliceEvent));

      // Act.
      context.producerManager.produceIBMMQMockMessages(context.mockDefinition, binding);

      // Consume messages on queue during 3 seconds.
      List<String> messages = consumeMessagesFromQueue(mqContainer.getHost(), mqContainer.getPort(), DESTINATION_NAME,
            3000);

      // Assert.
      assertEquals(2, messages.size());
      for (String message : messages) {
         assertTrue(ALICE_CONTENT.equals(message) || BOB_CONTENT.equals(message));
      }
   }

   @Test
   void testProduceMockMessagesToTopic() throws Exception {
      // Arrange.
      TestContext context = arrange();

      // IBM MQ binding targeting a publish/subscribe topic.
      Binding binding = new Binding(BindingType.IBMMQ);
      binding.setDestinationType("topic");

      // A topic has no message retention: the subscription must be opened *before* publishing so that publications get
      // captured. We open a managed non-durable subscription on the topic string used by the producer.
      MQQueueManager queueManager = new MQQueueManager(QUEUE_MANAGER,
            connectionProperties(mqContainer.getHost(), mqContainer.getPort()));
      MQTopic subscriber = queueManager.accessTopic(DESTINATION_NAME, "", CMQC.MQTOPIC_OPEN_AS_SUBSCRIPTION,
            CMQC.MQSO_CREATE | CMQC.MQSO_MANAGED | CMQC.MQSO_NON_DURABLE | CMQC.MQSO_FAIL_IF_QUIESCING);

      try {
         // Act.
         context.producerManager.produceIBMMQMockMessages(context.mockDefinition, binding);

         // Consume messages on topic subscription during 3 seconds.
         List<String> messages = drainMessages(subscriber, 3000);

         // Assert.
         assertEquals(2, messages.size());
         for (String message : messages) {
            assertTrue(ALICE_CONTENT.equals(message) || BOB_CONTENT.equals(message));
         }
      } finally {
         subscriber.close();
         queueManager.disconnect();
      }
   }

   /** Build the mock definition, repository, schema registry and wired producer manager shared by the tests. */
   private TestContext arrange() throws Exception {
      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0.yaml"));

      // Prepare some event messages.
      EventMessage aliceEvent = new EventMessage();
      aliceEvent.setName("Alice");
      aliceEvent.setMediaType("application/json");
      aliceEvent.setContent(ALICE_CONTENT);
      EventMessage bobEvent = new EventMessage();
      bobEvent.setName("Bob");
      bobEvent.setMediaType("application/json");
      bobEvent.setContent(BOB_CONTENT);

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("d3d5a3ed-13bf-493f-a06d-bf93392f420b");
      service.setName("User signed-up API");
      service.setVersion("0.3.0");
      service.setType(ServiceType.EVENT);

      Operation signedupOperation = new Operation();
      signedupOperation.setName("SUBSCRIBE user/signedup");
      service.setOperations(List.of(signedupOperation));

      // Assemble them into a repository.
      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signedupOperation,
            List.of(aliceEvent, bobEvent));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("d3d5a3ed-13bf-493f-a06d-bf93392f420b",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);
      schemaRegistry.updateRegistryForService("d3d5a3ed-13bf-493f-a06d-bf93392f420b");

      // Finally, arrange the objects under test.
      IBMMQProducerManager ibmmqProducerManager = new IBMMQProducerManager();
      ibmmqProducerManager.ibmmqServer = mqContainer.getHost() + ":" + mqContainer.getPort();
      ibmmqProducerManager.queueManagerName = QUEUE_MANAGER;
      ibmmqProducerManager.ibmmqChannel = ADMIN_CHANNEL;
      ibmmqProducerManager.ibmmqUsername = Optional.of(ADMIN_USER);
      ibmmqProducerManager.ibmmqPassword = Optional.of(ADMIN_PASSWORD);
      ibmmqProducerManager.create();

      ProducerManager producerManager = new ProducerManager(mockRepository, null,
            new ProducerManager.ProducerDependencies(null, null, null, null, null, null, null, ibmmqProducerManager),
            null);

      return new TestContext(producerManager, ibmmqProducerManager, mockDefinition, aliceEvent, bobEvent);
   }

   private static Hashtable<String, Object> connectionProperties(String host, int port) {
      Hashtable<String, Object> connProperties = new Hashtable<>();
      connProperties.put(CMQC.HOST_NAME_PROPERTY, host);
      connProperties.put(CMQC.PORT_PROPERTY, port);
      connProperties.put(CMQC.CHANNEL_PROPERTY, ADMIN_CHANNEL);
      connProperties.put(CMQC.TRANSPORT_PROPERTY, CMQC.TRANSPORT_MQSERIES_CLIENT);
      connProperties.put(CMQC.USER_ID_PROPERTY, ADMIN_USER);
      connProperties.put(CMQC.PASSWORD_PROPERTY, ADMIN_PASSWORD);
      return connProperties;
   }

   private static List<String> consumeMessagesFromQueue(String host, int port, String queueName, long timeout)
         throws Exception {
      MQQueueManager queueManager = new MQQueueManager(QUEUE_MANAGER, connectionProperties(host, port));
      MQQueue queue = queueManager.accessQueue(queueName, CMQC.MQOO_INPUT_AS_Q_DEF | CMQC.MQOO_FAIL_IF_QUIESCING);
      try {
         return drainMessages(queue, timeout);
      } finally {
         queue.close();
         queueManager.disconnect();
      }
   }

   private static List<String> drainMessages(MQDestination destination, long timeout) throws Exception {
      List<String> messages = new ArrayList<>();
      long timeoutTime = System.currentTimeMillis() + timeout;
      while (System.currentTimeMillis() < timeoutTime) {
         MQMessage message = new MQMessage();
         MQGetMessageOptions getOptions = new MQGetMessageOptions();
         getOptions.options = CMQC.MQGMO_WAIT | CMQC.MQGMO_FAIL_IF_QUIESCING;
         getOptions.waitInterval = (int) (timeoutTime - System.currentTimeMillis());

         try {
            destination.get(message, getOptions);
            byte[] payload = new byte[message.getMessageLength()];
            message.readFully(payload);
            messages.add(new String(payload, StandardCharsets.UTF_8));
         } catch (MQException e) {
            if (e.reasonCode == CMQC.MQRC_NO_MSG_AVAILABLE) {
               break;
            }
            throw e;
         }
      }
      return messages;
   }

   /** Simple holder for the objects assembled by {@link #arrange()}. */
   private record TestContext(ProducerManager producerManager, IBMMQProducerManager ibmmqProducerManager,
         AsyncMockDefinition mockDefinition, EventMessage aliceEvent, EventMessage bobEvent) {
   }
}
