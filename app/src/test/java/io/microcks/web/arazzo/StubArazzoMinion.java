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

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands for the Arazzo minion on its POST /api/runs endpoint: keeps the run requests it receives and answers with a
 * status code.
 * @author SebastienDegodez
 */
final class StubArazzoMinion {

   private final HttpServer server;
   private final List<String> runRequests = new CopyOnWriteArrayList<>();
   private volatile int status = 202;

   StubArazzoMinion() {
      try {
         server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      } catch (IOException ioe) {
         throw new UncheckedIOException(ioe);
      }
      server.createContext("/api/runs", exchange -> {
         runRequests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
         exchange.sendResponseHeaders(status, -1);
         exchange.close();
      });
      server.start();
   }

   String url() {
      return "http://localhost:" + server.getAddress().getPort();
   }

   List<String> runRequests() {
      return runRequests;
   }

   void answer(int status) {
      this.status = status;
   }

   void stop() {
      server.stop(0);
   }
}
