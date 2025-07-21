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

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;
import io.github.microcks.minion.async.client.dto.TestCaseReturnDTO;
import io.github.microcks.minion.async.consumer.AMQPMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.AmazonSNSMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.AmazonSQSMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.ConsumedMessage;
import io.github.microcks.minion.async.consumer.GooglePubSubMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.KafkaMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.MQTTMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.MessageConsumptionTask;
import io.github.microcks.minion.async.consumer.WebSocketMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.NATSMessageConsumptionTask;
import io.github.microcks.util.SchemaMap;
import io.github.microcks.util.asyncapi.AsyncAPISchemaUtil;
import io.github.microcks.util.asyncapi.AsyncAPISchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manager that takes care of launching and running an AsyncAPI test from an <code>AsyncTestSpecification</code>.
 * @author laurent
 */
@ApplicationScoped
public class AsyncAPITestManager {

   private final MicrocksAPIConnector microcksAPIConnector;
   private final SchemaRegistry schemaRegistry;

   @ConfigProperty(name = "io.github.microcks.minion.async.client.MicrocksAPIConnector/mp-rest/url")
   String microcksUrl;

   /**
    * Create a new AsyncAPITestManager.
    * @param microcksAPIConnector The MicrocksAPIConnector to use for reporting test results.
    * @param schemaRegistry       The SchemaRegistry to use for schema validation.
    */
   public AsyncAPITestManager(@RestClient MicrocksAPIConnector microcksAPIConnector, SchemaRegistry schemaRegistry) {
      this.microcksAPIConnector = microcksAPIConnector;
      this.schemaRegistry = schemaRegistry;
   }

   /**
    * Launch a new test using this specification. This is a fire and forget operation. Tests results will be reported
    * later once finished using a <code>MicrocksAPIConnector</code>.
    * @param specification The specification of Async test to launch.
    */
   public void launchTest(AsyncTestSpecification specification) {
      AsyncAPITestThread thread = new AsyncAPITestThread(specification);
      thread.start();
   }

   /**
    * Actually run the Async test by instantiating a <code>MessageConsumptionTask</code>, gathering its outputs and
    * validating them against an AsyncAPI schema.
    */
   class AsyncAPITestThread extends Thread {

      /** Get a JBoss logging logger. */
      private final Logger logger = Logger.getLogger(getClass());

      private final AsyncTestSpecification specification;

      private final ExecutorService executorService = Executors.newSingleThreadExecutor();

      private long startTime;

      /**
       * Build a new test thread from a test specification.
       * @param specification The specification for async test to run.
       */
      public AsyncAPITestThread(AsyncTestSpecification specification) {
         this.specification = specification;
      }

      @Override
      public void run() {
         logger.infof("Launching a new AsyncAPITestThread for {%s} on {%s}", specification.getTestResultId(),
               specification.getEndpointUrl());

         // Start initialising a TestCaseReturn and build the correct MessageConsumptionTasK.
         TestCaseReturnDTO testCaseReturn = new TestCaseReturnDTO(specification.getOperationName());
         MessageConsumptionTask messageConsumptionTask = buildMessageConsumptionTask(specification);

         // Actually start test counter.
         startTime = System.currentTimeMillis();
         List<ConsumedMessage> outputs = null;

         if (messageConsumptionTask != null) {
            try {
               logger.debugf("Starting consuming messages for {%d} ms", specification.getTimeoutMS());
               // Adding an extra seconds to allow to close and clean all the machinery ;-)
               outputs = executorService.invokeAny(Collections.singletonList(messageConsumptionTask),
                     specification.getTimeoutMS() + 1000L, TimeUnit.MILLISECONDS);
               logger.debugf("Consumption ends and we got {%d} messages to validate", outputs.size());
            } catch (InterruptedException e) {
               logger.infof("AsyncAPITestThread for {%s} was interrupted", specification.getTestResultId());
            } catch (ExecutionException e) {
               logger.errorf(e, "AsyncAPITestThread for {%s} raise an ExecutionException",
                     specification.getTestResultId());
               testCaseReturn.addTestReturn(new TestReturn(TestReturn.FAILURE_CODE, specification.getTimeoutMS(),
                     "ExecutionException: no message received in " + specification.getTimeoutMS() + " ms", null, null));
            } catch (TimeoutException e) {
               // Message consumption has timed-out, add an empty test return with failure and message.
               logger.infof("AsyncAPITestThread for {%s} was timed-out", specification.getTestResultId());
               testCaseReturn.addTestReturn(new TestReturn(TestReturn.FAILURE_CODE, specification.getTimeoutMS(),
                     "Timeout: no message received in " + specification.getTimeoutMS() + " ms", null, null));
            } catch (Throwable t) {
               // We faced a low-level issue... add an empty test return with failure and message.
               logger.error("Caught a low-level throwable", t);
               testCaseReturn
                     .addTestReturn(new TestReturn(TestReturn.FAILURE_CODE, System.currentTimeMillis() - startTime,
                           "Exception: low-level failure: " + t.getMessage(), null, null));
            } finally {
               try {
                  messageConsumptionTask.close();
               } catch (Throwable t) {
                  // This was best effort, just ignore the exception...
               }
               executorService.shutdown();
            }
         } else {
            logger.errorf("Found no suitable MessageConsumptionTask implementation. {%s} is not a supported endpoint",
                  specification.getEndpointUrl());
            testCaseReturn.addTestReturn(new TestReturn(TestReturn.FAILURE_CODE, System.currentTimeMillis() - startTime,
                  "Exception: found no suitable MessageConsumptionTask implementation for endpoint", null, null));
         }

         // Validate all the received outputs if any.
         if (outputs != null && !outputs.isEmpty()) {
            validateConsumedMessages(testCaseReturn, outputs);
         } else {
            logger.infof("No consumed message to validate, test {%s} will be marked as timed-out",
                  specification.getTestResultId());
         }

         // Finally, report the testCase results using Microcks API.
         microcksAPIConnector.reportTestCaseResult(specification.getTestResultId(), testCaseReturn);
      }

      /**
       * Validate consumed messages and complete {@code testCaseReturn} with {@code TestReturn} objects.
       * @param testCaseReturn The TestCase to complete with schema validation results
       * @param outputs        The consumed messages from tested endpoint.
       */
      private void validateConsumedMessages(TestCaseReturnDTO testCaseReturn, List<ConsumedMessage> outputs) {
         JsonNode specificationNode = null;
         try {
            specificationNode = AsyncAPISchemaValidator.getJsonNodeForSchema(specification.getAsyncAPISpec());
         } catch (IOException e) {
            logger.errorf("Retrieval of AsyncAPI schema for validation fails for {%s}",
                  specification.getTestResultId());
         }

         // Compute message JSON pointer to navigate the spec.
         String messagePathPointer = AsyncAPISchemaUtil.findMessagePathPointer(specificationNode,
               specification.getOperationName());

         // Retrieve expected content type from specification and produce a schema registry snapshot.
         String expectedContentType = null;
         SchemaMap schemaMap = new SchemaMap();
         if (specificationNode != null) {
            expectedContentType = getExpectedContentType(specificationNode, messagePathPointer);
            if (Constants.AVRO_BINARY_CONTENT_TYPES.contains(expectedContentType)) {
               logger.debug("Expected content type is Avro so extracting service resources into a SchemaMap");
               schemaRegistry.updateRegistryForService(specification.getServiceId());
               schemaRegistry.getSchemaEntries(specification.getServiceId()).stream()
                     .forEach(schemaEntry -> schemaMap.putSchemaEntry(schemaEntry.getPath(), schemaEntry.getContent()));
            }
         }

         for (int i = 0; i < outputs.size(); i++) {
            // Treat each message and compute elapsed time of each.
            ConsumedMessage message = outputs.get(i);
            long elapsedTime = message.getReceivedAt() - startTime;

            // Initialize an EventMessage with message content.
            String responseContent = (message.getPayload() != null) ? new String(message.getPayload())
                  : message.getPayloadRecord().toString();

            EventMessage eventMessage = new EventMessage();
            eventMessage.setName(specification.getTestResultId() + "-" + specification.getOperationName() + "-" + i);
            eventMessage.setMediaType(expectedContentType);
            eventMessage.setContent(responseContent);
            eventMessage.setHeaders(message.getHeaders());

            TestReturn testReturn;

            if (specificationNode == null) {
               logger.infof("AsyncAPI specification cannot be read, so test {%s} cannot be validated",
                     specification.getTestResultId());
               testReturn = new TestReturn(TestReturn.FAILURE_CODE, elapsedTime,
                     "AsyncAPI specification cannot be read, thus message cannot be validated", eventMessage);
            } else if (expectedContentType == null) {
               logger.infof("Expected content-type cannot be determined, so test {%s} cannot be validated",
                     specification.getTestResultId());
               testReturn = new TestReturn(TestReturn.FAILURE_CODE, elapsedTime,
                     "Content-Type cannot be determined, thus message cannot be validated", eventMessage);
            } else {
               // We now should have everything to perform validation, go ahead!
               try {
                  logger.infof("Validating received message {%s} against {%s}", responseContent, messagePathPointer);
                  List<String> errors = null;
                  if (Constants.AVRO_BINARY_CONTENT_TYPES.contains(expectedContentType)) {
                     // Use the correct Avro message validation method depending on what has been read.
                     if (message.getPayloadRecord() != null) {
                        errors = AsyncAPISchemaValidator.validateAvroMessage(specificationNode,
                              message.getPayloadRecord(), messagePathPointer, schemaMap);
                     } else {
                        errors = AsyncAPISchemaValidator.validateAvroMessage(specificationNode, message.getPayload(),
                              messagePathPointer, schemaMap);
                     }
                  } else if (expectedContentType.contains("application/json")) {
                     // Now parse the payloadNode and validate it according the operation message
                     // found in specificationNode.
                     JsonNode payloadNode = AsyncAPISchemaValidator.getJsonNode(responseContent);
                     errors = AsyncAPISchemaValidator.validateJsonMessage(specificationNode, payloadNode,
                           messagePathPointer, microcksUrl + "/api/resources/");
                  }

                  if (errors == null || errors.isEmpty()) {
                     // This is a success.
                     logger.infof("No errors found while validating message payload! Reporting a success for test {%s}",
                           specification.getTestResultId());
                     testReturn = new TestReturn(TestReturn.SUCCESS_CODE, elapsedTime, eventMessage);
                  } else {
                     // This is a failure. Records all errors using \\n as delimiter.
                     logger.infof("Validation errors found... Reporting a failure for test {%s}",
                           specification.getTestResultId());
                     testReturn = new TestReturn(TestReturn.FAILURE_CODE, elapsedTime, String.join("\\n", errors),
                           eventMessage);
                  }
               } catch (IOException e) {
                  logger.error("Exception while parsing the output message", e);
                  testReturn = new TestReturn(TestReturn.FAILURE_CODE, elapsedTime,
                        "Message content cannot be parsed as JSON", eventMessage);
               }
            }
            testCaseReturn.addTestReturn(testReturn);
         }
      }

      /** Retrieve the expected content type for an AsyncAPI message. */
      private String getExpectedContentType(JsonNode specificationNode, String messagePathPointer) {
         // Retrieve default content type, defaulting to application/json.
         String defaultContentType = specificationNode.path("defaultContentType").asText("application/json");

         // Get message real content type if defined.
         String contentType = defaultContentType;
         JsonNode messageNode = specificationNode.at(messagePathPointer);

         // messageNode will be an array of messages
         if (messageNode.isArray() && messageNode.size() > 0) {
            messageNode = messageNode.get(0);
         }

         // If it's a $ref, then navigate to it.
         while (messageNode.has("$ref")) {
            // $ref: '#/components/messages/lightMeasured'
            String ref = messageNode.path("$ref").asText();
            messageNode = specificationNode.at(ref.substring(1));
         }
         if (messageNode.has("contentType")) {
            contentType = messageNode.path("contentType").asText();
         }
         return contentType;
      }

      /** Find the appropriate MessageConsumptionTask implementation depending on specification. */
      private MessageConsumptionTask buildMessageConsumptionTask(AsyncTestSpecification testSpecification) {
         if (KafkaMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl().trim())) {
            return new KafkaMessageConsumptionTask(testSpecification);
         }
         if (MQTTMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl().trim())) {
            return new MQTTMessageConsumptionTask(testSpecification);
         }
         if (NATSMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl().trim())) {
            return new NATSMessageConsumptionTask(testSpecification);
         }
         if (WebSocketMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl().trim())) {
            return new WebSocketMessageConsumptionTask(testSpecification);
         }
         if (AMQPMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl().trim())) {
            return new AMQPMessageConsumptionTask(testSpecification);
         }
         if (GooglePubSubMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl().trim())) {
            return new GooglePubSubMessageConsumptionTask(testSpecification);
         }
         if (AmazonSQSMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl().trim())) {
            return new AmazonSQSMessageConsumptionTask(testSpecification);
         }
         if (AmazonSNSMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl().trim())) {
            return new AmazonSNSMessageConsumptionTask(testSpecification);
         }
         return null;
      }
   }
}
