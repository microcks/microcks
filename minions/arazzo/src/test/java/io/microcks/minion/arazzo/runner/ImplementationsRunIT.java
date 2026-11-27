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
package io.microcks.minion.arazzo.runner;

import io.microcks.minion.arazzo.client.dto.OperationDTO;
import io.microcks.minion.arazzo.client.dto.RunRequestDTO;
import io.microcks.minion.arazzo.client.dto.SecretDTO;
import io.microcks.minion.arazzo.client.dto.SourceDTO;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The runner of the minion against real HTTP implementations of the Customer and Order APIs, each served on its own
 * port: the run request of Microcks gives one base URL per source description and a secret, and each implementation
 * sees the calls of its own steps, authenticated by that secret.
 * @author SebastienDegodez
 */
class ImplementationsRunIT {

   private final Implementation customers = new Implementation("GET /customers/c-42", 200,
         "{\"id\":\"c-42\",\"name\":\"Ada\"}");
   private final Implementation orders = new Implementation("POST /orders", 201, "{\"id\":\"o-7\"}");
   private final RecordingReporter reporter = new RecordingReporter();
   private final WorkflowRunner runner = new WorkflowRunner(new JdkHttpTransport(), Clock.systemUTC());

   @AfterEach
   void stopTheImplementations() {
      customers.stop();
      orders.stop();
   }

   @Test
   void eachStepCallsTheImplementationOfItsSourceWithTheAuthorizationOfTheSecret() throws IOException {
      RunRequestDTO request = new RunRequestDTO("run-1", "placeOrder", placeOrder(), Map.of("customerId", "c-42"),
            Map.of("customerApi",
                  new SourceDTO(customers.url(), Map.of("GetCustomer", new OperationDTO("GET", "/customers/{id}"))),
                  "orderApi", new SourceDTO(orders.url(), Map.of("CreateOrder", new OperationDTO("POST", "/orders")))),
            10000, new SecretDTO("pastry-staging-token", null, null, "s3cr3t-t0k3n", "X-Pastry-Token"));

      runner.run(request.toSpecification("http://microcks:8080"), reporter);

      assertEquals(List.of("GET /customers/c-42 X-Pastry-Token=s3cr3t-t0k3n"), customers.received());
      assertEquals(List.of("POST /orders X-Pastry-Token=s3cr3t-t0k3n"), orders.received());
      assertEquals(List.of("getCustomer PASSED 200", "createOrder PASSED 201"), reporter.steps().stream()
            .map(step -> step.stepId() + " " + step.status() + " " + step.statusCode()).toList());
      assertEquals(Map.of("orderId", "o-7"), reporter.outcome().outputs());
      assertEquals("<value-from-secret>", reporter.step("createOrder").request().headers().get("X-Pastry-Token"));
   }

   private static String placeOrder() throws IOException {
      try (InputStream stream = ImplementationsRunIT.class
            .getResourceAsStream("/io/microcks/minion/arazzo/runner/implementation-runs.arazzo.yaml")) {
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.contains("# example: order-fulfilment ")).findFirst().orElseThrow();
      }
   }

   /** An implementation answering one operation, keeping the method, path and token header of each call. */
   private static final class Implementation {

      private final HttpServer server;
      private final List<String> received = new CopyOnWriteArrayList<>();

      Implementation(String operation, int status, String body) {
         try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
         } catch (IOException ioe) {
            throw new IllegalStateException(ioe);
         }
         server.createContext("/", exchange -> answer(exchange, operation, status, body));
         server.start();
      }

      private void answer(HttpExchange exchange, String operation, int status, String body) throws IOException {
         String called = exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath();
         received.add(called + " X-Pastry-Token=" + exchange.getRequestHeaders().getFirst("X-Pastry-Token"));
         byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(called.equals(operation) ? status : 404, bytes.length);
         exchange.getResponseBody().write(bytes);
         exchange.close();
      }

      String url() {
         return "http://localhost:" + server.getAddress().getPort();
      }

      List<String> received() {
         return received;
      }

      void stop() {
         server.stop(0);
      }
   }
}
