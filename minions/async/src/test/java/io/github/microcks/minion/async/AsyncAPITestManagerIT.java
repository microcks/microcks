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
package io.github.microcks.minion.async;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.TestCaseResult;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.domain.TestRunnerType;
import io.github.microcks.minion.async.client.KeycloakConfig;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;
import io.github.microcks.minion.async.client.dto.TestCaseReturnDTO;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link AsyncAPITestManager} class.
 * @author laurent
 */
@Testcontainers
class AsyncAPITestManagerIT {

   private static final String TOPIC_NAME = "test-topic";

   private static Stream<TestConfig> testConfigs() {
      return Stream.of(new TestConfig("user-signedup-asyncapi-3.0.yaml", "SEND publishUserSignedUps", """
            {
               "fullName": "Laurent Broudoux",
               "email": "laurent@microcks.io",
               "age": 4%s
            }
            """), new TestConfig("send-message-asyncapi-3.0.yaml", "SEND sendEchoMessage", "test"));
   }

   private static final Network NETWORK = Network.newNetwork();

   @Container
   private static final KafkaContainer kafkaContainer = new KafkaContainer(
         DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withNetwork(NETWORK).withNetworkAliases("kafka")
               .withListener(() -> "kafka:19092");

   @ParameterizedTest
   @MethodSource("testConfigs")
   void testKafkaAsyncAPITestSuccess(TestConfig config) throws Exception {
      // Arrange.
      String asyncAPIContent = Files
            .readString(Paths.get("target/test-classes/io/github/microcks/minion/async", config.specificationFile));
      Map<String, TestCaseReturnDTO> reportedTestCases = new HashMap<>();
      MicrocksAPIConnector microcksAPIConnector = new MicrocksAPIConnector() {
         @Override
         public KeycloakConfig getKeycloakConfig() {
            return null;
         }

         @Override
         public List<Service> listServices(String authorization, int page, int size) {
            return null;
         }

         @Override
         public ServiceView getService(String authorization, String serviceId, boolean messages) {
            return null;
         }

         @Override
         public List<Resource> getResources(String serviceId) {
            if ("d3d5a3ed-13bf-493f-a06d-bf93392f420b".equals(serviceId)) {
               Resource resource = new Resource();
               resource.setId("578497d2-2695-4aff-b7c6-ff7b9a373aa9");
               resource.setType(ResourceType.ASYNC_API_SPEC);
               resource.setContent(asyncAPIContent);
               return List.of(resource);
            }
            return Collections.emptyList();
         }

         @Override
         public TestCaseResult reportTestCaseResult(String testResultId, TestCaseReturnDTO testCaseReturn) {
            reportedTestCases.put(testResultId, testCaseReturn);
            return null;
         }
      };
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);
      schemaRegistry.updateRegistryForService("d3d5a3ed-13bf-493f-a06d-bf93392f420b");

      AsyncTestSpecification testSpecification = new AsyncTestSpecification();
      testSpecification.setServiceId("d3d5a3ed-13bf-493f-a06d-bf93392f420b");
      testSpecification.setEndpointUrl(
            "kafka://%s/%s".formatted(kafkaContainer.getBootstrapServers().replace("PLAINTEXT://", ""), TOPIC_NAME));
      testSpecification.setRunnerType(TestRunnerType.ASYNC_API_SCHEMA);
      testSpecification.setAsyncAPISpec(asyncAPIContent);
      testSpecification.setOperationName(config.operationName);
      testSpecification.setTimeoutMS(2200L);
      testSpecification.setTestResultId("test-result-id");

      AsyncAPITestManager manager = new AsyncAPITestManager(microcksAPIConnector, schemaRegistry);
      manager.microcksUrl = "http://microcks:8080";

      // Act.
      manager.launchTest(testSpecification);

      // Wait a bit so that consumption task has actually started.
      await().during(1250, TimeUnit.MILLISECONDS).until(() -> true);
      sendTextMessagesOnTopic(5, config.message);

      // Wait a bit so that consumption task has actually finished.
      await().during(3, TimeUnit.SECONDS).until(() -> true);

      // Assert.
      Assertions.assertEquals(1, reportedTestCases.size());
      Assertions.assertTrue(reportedTestCases.containsKey("test-result-id"));
      TestCaseReturnDTO testCaseReturn = reportedTestCases.get("test-result-id");
      Assertions.assertEquals(5, testCaseReturn.getTestReturns().size());
      for (TestReturn testReturn : testCaseReturn.getTestReturns()) {
         Assertions.assertEquals(TestReturn.SUCCESS_CODE, testReturn.getCode());
      }
   }

   private static void sendTextMessagesOnTopic(int number, String message) {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-str-producer");
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

      KafkaProducer<String, String> producer = new KafkaProducer<>(props);

      for (int i = 0; i < number; i++) {
         ProducerRecord<String, String> kafkaRecord = new ProducerRecord<>(TOPIC_NAME,
               String.valueOf(System.currentTimeMillis()), message.formatted(i));
         producer.send(kafkaRecord);
      }
      producer.flush();
      producer.close();
   }

   // record to group the test config
   record TestConfig(String specificationFile, String operationName, String message) {
   }
}
