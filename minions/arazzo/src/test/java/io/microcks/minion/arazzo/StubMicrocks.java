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
package io.microcks.minion.arazzo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands for Microcks around the minion: it serves the mocks of API Pastry for Eclair Cafe, and keeps the reports the
 * minion posts to it, by path.
 * @author SebastienDegodez
 */
public class StubMicrocks implements QuarkusTestResourceLifecycleManager {

   static final List<String> REPORT_PATHS = new CopyOnWriteArrayList<>();
   static final List<String> REPORTS = new CopyOnWriteArrayList<>();
   static String url;

   private static final String MOCKS = "/rest/API+Pastry+-+2.0/2.0.0";

   private HttpServer server;

   @Override
   public Map<String, String> start() {
      try {
         server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      } catch (IOException ioe) {
         throw new UncheckedIOException(ioe);
      }
      server.createContext("/", StubMicrocks::handle);
      server.start();
      url = "http://localhost:" + server.getAddress().getPort();
      return Map.of("microcks.url", url);
   }

   @Override
   public void stop() {
      server.stop(0);
   }

   private static void handle(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String path = exchange.getRequestURI().getRawPath();
      if (path.startsWith("/api/arazzo/runs/")) {
         REPORT_PATHS.add(path);
         REPORTS.add(body);
         exchange.sendResponseHeaders(204, -1);
      } else if (path.equals(MOCKS + "/pastry/Eclair%20Cafe")) {
         String price = exchange.getRequestMethod().equals("PATCH") ? "2.6" : "2.5";
         answer(exchange, "{\"name\":\"Eclair Cafe\",\"price\":" + price + ",\"status\":\"available\"}");
      } else {
         exchange.sendResponseHeaders(404, -1);
      }
      exchange.close();
   }

   private static void answer(HttpExchange exchange, String json) throws IOException {
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
   }
}
