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

import io.github.microcks.domain.TestCasePhase;
import io.github.microcks.minion.async.AsyncTestSpecification;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This is an integration test case for {@link WebSocketMessageConsumptionTask} class. It uses an embedded Vert.x
 * WebSocket server (in-process, no Docker required) to exercise the real consumption task and assert it reports the
 * {@link TestCasePhase#WAITING_FOR_MESSAGE} phase once its session is established.
 * @author sebastien
 */
class WebSocketMessageConsumptionTaskIT {

   private static final String CHANNEL = "websocket";
   private static final String TEXT_MESSAGE = "{\"greeting\": \"Hello World!\", \"number\": 0}";

   private static Vertx vertx;
   private static HttpServer server;
   private static int port;

   @BeforeAll
   static void beforeAll() throws Exception {
      vertx = Vertx.vertx();
      // On every client connection, push a text message after a short delay so that the consumer message handler is
      // guaranteed to be registered before the message is sent.
      server = vertx.createHttpServer()
            .webSocketHandler(ws -> vertx.setTimer(250, id -> ws.writeTextMessage(TEXT_MESSAGE)));
      server.listen(0, "localhost").toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
      port = server.actualPort();
   }

   @AfterAll
   static void afterAll() throws Exception {
      if (server != null) {
         server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
      }
      if (vertx != null) {
         vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
      }
   }

   @Test
   void shouldReceiveMessageOnChannelCorrectly() throws Exception {
      // Arrange.
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(1500L);
      asyncTestSpecification.setEndpointUrl("ws://localhost:" + port + "/" + CHANNEL);

      List<TestCasePhase> reportedPhases = Collections.synchronizedList(new ArrayList<>());
      List<ConsumedMessage> messages;

      // Act.
      try (WebSocketMessageConsumptionTask wsConsumptionTask = new WebSocketMessageConsumptionTask(
            asyncTestSpecification); ExecutorService executorService = Executors.newSingleThreadExecutor()) {
         wsConsumptionTask.setPhaseListener(reportedPhases::add);
         Future<List<ConsumedMessage>> output = executorService
               .submit((Callable<List<ConsumedMessage>>) wsConsumptionTask);
         messages = output.get(asyncTestSpecification.getTimeoutMS() + 2000L, TimeUnit.MILLISECONDS);
      }

      // Assert.
      Assertions.assertFalse(messages.isEmpty());
      Assertions.assertEquals(1, messages.size());
      Assertions.assertEquals(TEXT_MESSAGE, new String(messages.get(0).getPayload(), StandardCharsets.UTF_8));
      // The real consumer should have reported it was connected and waiting for messages.
      Assertions.assertTrue(reportedPhases.contains(TestCasePhase.WAITING_FOR_MESSAGE),
            "The WebSocket consumer should have reported the WAITING_FOR_MESSAGE phase.");
   }
}
