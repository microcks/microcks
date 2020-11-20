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
package io.github.microcks.minion.async;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;
import io.github.microcks.minion.async.client.dto.TestCaseReturnDTO;
import io.github.microcks.minion.async.consumer.ConsumedMessage;
import io.github.microcks.minion.async.consumer.KafkaMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.MQTTMessageConsumptionTask;
import io.github.microcks.minion.async.consumer.MessageConsumptionTask;
import io.github.microcks.util.asyncapi.AsyncAPISchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;

import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
/**
 * Manager that takes care of launching and running an AsyncAPI test from an
 * <code>AsyncTestSpecification</code>.
 * @author laurent
 */
public class AsyncAPITestManager {

   @Inject
   @RestClient
   MicrocksAPIConnector microcksAPIConnector;

   /**
    * Launch a new test using this specification. This is a fire and forget operation.
    * Tests results will be reported later once finished using a <code>MicrocksAPIConnector</code>.
    * @param specification The specification of Async test to launch.
    */
   public void launchTest(AsyncTestSpecification specification) {
      AsyncAPITestThread thread = new AsyncAPITestThread(specification);
      thread.start();
   }

   /**
    * Actually run the Async test by instantiating a <code>MessageConsumptionTask</code>,
    * gathering its outpurs and validating them against an AsyncAPI schema.
    */
   class AsyncAPITestThread extends Thread {

      /** Get a JBoss logging logger. */
      private final Logger logger = Logger.getLogger(getClass());

      private final AsyncTestSpecification specification;

      private final ExecutorService executorService = Executors.newSingleThreadExecutor();

      /**
       * Build a new test thread from a test specification.
       * @param specification The specification for async test to run.
       */
      public AsyncAPITestThread(AsyncTestSpecification specification) {
         this.specification = specification;
      }

      @Override
      public void run() {
         logger.infof("Launching a new AsyncAPITestThread for {%s} on {%s}",
               specification.getTestResultId(), specification.getEndpointUrl());

         // Start initialising a TestCaseReturn and build the correct MessageConsumptionTasK.
         TestCaseReturnDTO testCaseReturn = new TestCaseReturnDTO(specification.getOperationName());
         MessageConsumptionTask messageConsumptionTask = buildMessageConsumptionTask(specification);

         // Actually start test counter.
         long startTime = System.currentTimeMillis();
         List<ConsumedMessage> outputs = null;
         try {
            logger.debugf("Starting consuming messages for {%d} ms", specification.getTimeoutMS());
            outputs = executorService.invokeAny(Collections.singletonList(messageConsumptionTask),
                  specification.getTimeoutMS(), TimeUnit.MILLISECONDS);
            logger.debugf("Consumption ends and we got {%d} messages to validate", outputs.size());
         } catch (InterruptedException e) {
            logger.infof("AsyncAPITestThread for {%s} was interrupted", specification.getTestResultId());
         } catch (ExecutionException e) {
            logger.errorf(e, "AsyncAPITestThread for {%s} raise an ExecutionException", specification.getTestResultId());
         } catch (TimeoutException e) {
            // Message consumption has timed-out, add an empty test return with failure and message.
            logger.infof("AsyncAPITestThread for {%s} was timed-out", specification.getTestResultId());
            testCaseReturn.addTestReturn(
                  new TestReturn(TestReturn.FAILURE_CODE, specification.getTimeoutMS(),
                        "Timeout: no message received in " + specification.getTimeoutMS()+ " ms",
                        null, null)
            );
         } finally {
            if (messageConsumptionTask != null) {
               try {
                  messageConsumptionTask.close();
               } catch (Throwable t) {
                  // This was best effort, just ignore the exception...
               }
            }
            executorService.shutdown();
         }
         // Now it's time to get elapsed timer.
         long elapsedTime = System.currentTimeMillis() - startTime;

         // Validate all the received outputs if any.
         if (outputs != null && !outputs.isEmpty()) {
            JsonNode specificationNode = null;
            try {
               specificationNode = AsyncAPISchemaValidator.getJsonNodeForSchema(specification.getAsyncAPISpec());
            } catch (IOException e) {
               logger.errorf("Retrieval of AsyncAPI schema for validation fails for {%s}", specification.getTestResultId());
            }

            for (int i=0; specificationNode!=null && i<outputs.size(); i++) {
               ConsumedMessage message = outputs.get(i);
               // Initialize an EventMessage with message content.
               String responseContent = new String(message.getPayload());
               EventMessage eventMessage = new EventMessage();
               eventMessage.setName(specification.getTestResultId() + "-" + specification.getOperationName() + "-" + i);
               eventMessage.setMediaType("application/json");
               eventMessage.setContent(responseContent);
               eventMessage.setHeaders(message.getHeaders());

               JsonNode payloadNode;
               TestReturn testReturn;
               try {
                  payloadNode = AsyncAPISchemaValidator.getJsonNode(responseContent);

                  String[] operationElements = specification.getOperationName().split(" ");
                  String operationNamePtr = "/channels/" + operationElements[1].replaceAll("/", "~1");
                  if ("SUBSCRIBE".equals(operationElements[0])) {
                     operationNamePtr += "/subscribe";
                  } else {
                     operationNamePtr += "/publish";
                  }

                  logger.infof("Validating received message {%s} against {%s}", responseContent, operationNamePtr + "/message");
                  // Now validate the payloadNode according the operation message found in specificationNode.
                  List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(specificationNode, payloadNode, operationNamePtr + "/message");

                  if (errors == null || errors.isEmpty()) {
                     // This is a success.
                     testReturn = new TestReturn(TestReturn.SUCCESS_CODE, elapsedTime, eventMessage);
                  } else {
                     // This is a failure. Records all errors using \\n as delimiter.
                     testReturn = new TestReturn(TestReturn.FAILURE_CODE, elapsedTime,
                           String.join("\\n", errors), eventMessage);
                  }
               } catch (IOException e) {
                  logger.error("Exception while parsing the output message", e);
                  testReturn = new TestReturn(TestReturn.FAILURE_CODE, elapsedTime,
                        "Message content cannot be parsed as JSON", eventMessage);
               }
               testCaseReturn.addTestReturn(testReturn);
            }
         }

         // Finally, report the testCase results using Microcks API.
         microcksAPIConnector.reportTestCaseResult(specification.getTestResultId(), testCaseReturn);
      }

      /** Find the appropriate MessageConsumptionTask implementation depending on specification. */
      private MessageConsumptionTask buildMessageConsumptionTask(AsyncTestSpecification testSpecification) {
         if (KafkaMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl())) {
            return new KafkaMessageConsumptionTask(testSpecification);
         }
         if (MQTTMessageConsumptionTask.acceptEndpoint(testSpecification.getEndpointUrl())) {
            return new MQTTMessageConsumptionTask(testSpecification);
         }
         return null;
      }
   }
}
