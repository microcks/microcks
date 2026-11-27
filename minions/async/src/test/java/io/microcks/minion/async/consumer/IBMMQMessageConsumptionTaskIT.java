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

import io.github.microcks.domain.Secret;
import io.github.microcks.minion.async.AsyncTestSpecification;

import com.ibm.mq.MQDestination;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.ibm.mq.testcontainers.MQContainer;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Hashtable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> and the
 * <a href="https://github.com/ibm-messaging/mq-jms-spring/tree/master/mq-java-testcontainer">IBM MQ Testcontainers
 * module</a> to test {@link IBMMQMessageConsumptionTask} class.
 * @author laurent
 */
@Testcontainers
class IBMMQMessageConsumptionTaskIT {

   private static final String QUEUE_MANAGER = "QM1";
   private static final String ADMIN_CHANNEL = "DEV.ADMIN.SVRCONN";
   private static final String ADMIN_USER = "admin";
   private static final String ADMIN_PASSWORD = "passw0rd";

   private static final String QUEUE_NAME = "MICROCKS.TEST.QUEUE";
   private static final String TOPIC_STRING = "microcks/test/topic";

   private static final String QUEUE_MESSAGE = "{\"message\": \"Hello from Microcks on a queue\"}";
   private static final String TOPIC_MESSAGE = "{\"message\": \"Hello from Microcks on a topic\"}";

   @Container
   private static final MQContainer mqContainer = new MQContainer(
         DockerImageName.parse("icr.io/ibm-messaging/mq:9.3.2.0-r2")).acceptLicense().withEnv("MQ_ADMIN_PASSWORD",
               ADMIN_PASSWORD);

   @Test
   void shouldAcceptOnlyWellFormedEndpoints() {
      assertTrue(IBMMQMessageConsumptionTask.acceptEndpoint("ibmmq://localhost:1414/QM1/queue/DEV.QUEUE.1"));
      assertTrue(IBMMQMessageConsumptionTask
            .acceptEndpoint("ibmmq://localhost:1414/QM1/topic/dev/topic?channel=DEV.APP.SVRCONN"));
      assertTrue(IBMMQMessageConsumptionTask
            .acceptEndpoint("ibmmq://localhost/QM1/queue/UsersignedupAPI_0.3.0_user/signedup"));

      // Legacy form without destination type as well as unknown types are rejected.
      assertFalse(IBMMQMessageConsumptionTask.acceptEndpoint("ibmmq://localhost:1414/QM1/DEV.QUEUE.1"));
      assertFalse(IBMMQMessageConsumptionTask.acceptEndpoint("ibmmq://localhost:1414/QM1/exchange/DEV.QUEUE.1"));
      assertFalse(IBMMQMessageConsumptionTask.acceptEndpoint("amqp://localhost:5672/q/logs"));
   }

   @Test
   void shouldReceiveMessageOnQueueCorrectly() throws Exception {
      // Arrange: the queue must exist before being consumed, so provision it through PCF.
      createLocalQueue(QUEUE_NAME);

      AsyncTestSpecification specification = buildSpecification("ibmmq://%s:%d/%s/queue/%s?channel=%s"
            .formatted(mqContainer.getHost(), mqContainer.getPort(), QUEUE_MANAGER, QUEUE_NAME, ADMIN_CHANNEL),
            Duration.ofSeconds(3).toMillis());
      IBMMQMessageConsumptionTask task = new IBMMQMessageConsumptionTask(specification);

      // A queue retains messages, so we can publish before the consumer connects.
      publishToQueue(QUEUE_NAME, QUEUE_MESSAGE);

      // Act.
      List<ConsumedMessage> messages = task.call();

      // Assert.
      assertEquals(1, messages.size());
      assertEquals(QUEUE_MESSAGE, new String(messages.get(0).getPayload(), StandardCharsets.UTF_8));
   }

   @Test
   void shouldReceiveMessageOnTopicCorrectly() throws Exception {
      // Arrange.
      long secondsToWait = Duration.ofSeconds(3).toMillis();
      AsyncTestSpecification specification = buildSpecification("ibmmq://%s:%d/%s/topic/%s?channel=%s"
            .formatted(mqContainer.getHost(), mqContainer.getPort(), QUEUE_MANAGER, TOPIC_STRING, ADMIN_CHANNEL),
            secondsToWait + 2000);
      IBMMQMessageConsumptionTask task = new IBMMQMessageConsumptionTask(specification);

      // A topic does not retain publications: wait for the task to subscribe before publishing.
      new Thread(() -> {
         try {
            Thread.sleep(secondsToWait);
            publishToTopic(TOPIC_STRING, TOPIC_MESSAGE);
         } catch (Exception e) {
            throw new RuntimeException("Error while publishing message to topic", e);
         }
      }).start();

      // Act.
      List<ConsumedMessage> messages = task.call();

      // Assert.
      assertEquals(1, messages.size());
      assertEquals(TOPIC_MESSAGE, new String(messages.get(0).getPayload(), StandardCharsets.UTF_8));
   }

   private static AsyncTestSpecification buildSpecification(String endpointUrl, long timeoutMS) {
      Secret secret = new Secret();
      secret.setName("ibmmq-admin");
      secret.setUsername(ADMIN_USER);
      secret.setPassword(ADMIN_PASSWORD);

      AsyncTestSpecification specification = new AsyncTestSpecification();
      specification.setEndpointUrl(endpointUrl);
      specification.setTimeoutMS(timeoutMS);
      specification.setSecret(secret);
      return specification;
   }

   private static MQQueueManager connect() throws Exception {
      Hashtable<String, Object> connProperties = new Hashtable<>();
      connProperties.put(CMQC.HOST_NAME_PROPERTY, mqContainer.getHost());
      connProperties.put(CMQC.PORT_PROPERTY, mqContainer.getPort());
      connProperties.put(CMQC.CHANNEL_PROPERTY, ADMIN_CHANNEL);
      connProperties.put(CMQC.TRANSPORT_PROPERTY, CMQC.TRANSPORT_MQSERIES_CLIENT);
      connProperties.put(CMQC.USER_ID_PROPERTY, ADMIN_USER);
      connProperties.put(CMQC.PASSWORD_PROPERTY, ADMIN_PASSWORD);
      return new MQQueueManager(QUEUE_MANAGER, connProperties);
   }

   private static void createLocalQueue(String queueName) throws Exception {
      MQQueueManager queueManager = connect();
      PCFMessageAgent agent = new PCFMessageAgent(queueManager);
      try {
         PCFMessage request = new PCFMessage(CMQCFC.MQCMD_CREATE_Q);
         request.addParameter(CMQC.MQCA_Q_NAME, queueName);
         request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_LOCAL);
         agent.send(request);
      } finally {
         agent.disconnect();
         queueManager.disconnect();
      }
   }

   private static void publishToQueue(String queueName, String payload) throws Exception {
      MQQueueManager queueManager = connect();
      MQDestination queue = queueManager.accessQueue(queueName, CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING);
      try {
         put(queue, payload);
      } finally {
         queue.close();
         queueManager.disconnect();
      }
   }

   private static void publishToTopic(String topicString, String payload) throws Exception {
      MQQueueManager queueManager = connect();
      MQDestination topic = queueManager.accessTopic(topicString, "", CMQC.MQTOPIC_OPEN_AS_PUBLICATION,
            CMQC.MQOO_OUTPUT | CMQC.MQOO_FAIL_IF_QUIESCING);
      try {
         put(topic, payload);
      } finally {
         topic.close();
         queueManager.disconnect();
      }
   }

   private static void put(MQDestination destination, String payload) throws Exception {
      MQMessage message = new MQMessage();
      message.write(payload.getBytes(StandardCharsets.UTF_8));
      destination.put(message);
   }
}
