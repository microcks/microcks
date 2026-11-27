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
package io.microcks.web.arazzo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Stands for the implementation of API Pastry - 2.0 that a CI pipeline deploys: it reads Eclair Cafe at 2.5 and
 * reprices it at 2.6, unless it is told to answer the reprice with a server error, or to hold its reads until they are
 * released.
 * @author SebastienDegodez
 */
final class PastryApp {

   private static final String ECLAIR_CAFE = "/pastry/Eclair%20Cafe";

   private final HttpServer server;
   private volatile boolean repriceFails;
   private volatile CountDownLatch heldReads = new CountDownLatch(0);

   PastryApp() {
      try {
         server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      } catch (IOException ioe) {
         throw new UncheckedIOException(ioe);
      }
      server.createContext("/", this::handle);
      server.start();
   }

   /** @return The base URL the pipeline gives for its source pastryApi */
   String url() {
      return "http://localhost:" + server.getAddress().getPort();
   }

   /** The reprice of Eclair Cafe answers a server error from now on. */
   void failReprice() {
      repriceFails = true;
   }

   /** The reads of Eclair Cafe wait from now on until {@link #releaseReads()}. */
   void holdReads() {
      heldReads = new CountDownLatch(1);
   }

   /** The reads of Eclair Cafe that wait are answered. */
   void releaseReads() {
      heldReads.countDown();
   }

   /** Back to an implementation that behaves as the journey expects. */
   void reset() {
      repriceFails = false;
      releaseReads();
   }

   void stop() {
      releaseReads();
      server.stop(0);
   }

   private void handle(HttpExchange exchange) throws IOException {
      String request = exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath();
      exchange.getRequestBody().readAllBytes();
      if (("GET " + ECLAIR_CAFE).equals(request)) {
         awaitRelease();
         answer(exchange, 200, eclairCafe(2.5));
      } else if (("PATCH " + ECLAIR_CAFE).equals(request)) {
         answer(exchange, repriceFails ? 500 : 200,
               repriceFails ? "{\"message\": \"The pastry database is down\"}" : eclairCafe(2.6));
      } else {
         exchange.sendResponseHeaders(404, -1);
         exchange.close();
      }
   }

   private void awaitRelease() {
      try {
         heldReads.await(30, TimeUnit.SECONDS);
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
      }
   }

   private static String eclairCafe(double price) {
      return "{\"name\": \"Eclair Cafe\", \"description\": \"Delicieux Eclair au Cafe pas calorique du tout\", "
            + "\"size\": \"M\", \"price\": " + price + ", \"status\": \"available\"}";
   }

   private static void answer(HttpExchange exchange, int status, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
   }
}
