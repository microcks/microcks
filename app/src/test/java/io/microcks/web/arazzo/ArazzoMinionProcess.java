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

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * The Arazzo minion, packaged by its module (minions/arazzo), run as a separate process for the end-to-end tests of the
 * webapp. Package it first with {@code mvn package -DskipTests -pl minions/arazzo -am}.
 * @author SebastienDegodez
 */
final class ArazzoMinionProcess {

   private static final Path QUARKUS_RUN = Path.of("..", "minions", "arazzo", "target", "quarkus-app",
         "quarkus-run.jar");
   private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

   private final int port;
   private Process process;

   ArazzoMinionProcess() {
      this.port = freePort();
   }

   String url() {
      return "http://localhost:" + port;
   }

   boolean isStarted() {
      return process != null && process.isAlive();
   }

   /**
    * Start the minion, reporting runs to and calling the mocks of the Microcks instance at microcksUrl.
    * @param microcksUrl The base URL of the webapp under test
    */
   void start(String microcksUrl) throws IOException, InterruptedException {
      if (!Files.exists(QUARKUS_RUN)) {
         throw new IllegalStateException(
               "The Arazzo minion is not packaged: run mvn package -DskipTests -pl minions/arazzo -am first");
      }
      String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
      process = new ProcessBuilder(List.of(java, "-Dquarkus.http.port=" + port, "-Dmicrocks.url=" + microcksUrl, "-jar",
            QUARKUS_RUN.toAbsolutePath().toString())).redirectErrorStream(true)
                  .redirectOutput(new File("target/arazzo-minion.log")).start();
      awaitReadiness();
   }

   void stop() {
      if (process != null) {
         process.destroy();
      }
   }

   private void awaitReadiness() throws InterruptedException {
      HttpClient client = HttpClient.newHttpClient();
      long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT.toMillis();
      while (System.currentTimeMillis() < deadline) {
         try {
            HttpResponse<Void> response = client.send(
                  HttpRequest.newBuilder(URI.create(url() + "/q/health/ready")).build(),
                  HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 200) {
               return;
            }
         } catch (IOException notYetListening) {
            // The minion is still starting.
         }
         Thread.sleep(250);
      }
      throw new IllegalStateException("The Arazzo minion did not start, see target/arazzo-minion.log");
   }

   private static int freePort() {
      try (ServerSocket socket = new ServerSocket(0)) {
         return socket.getLocalPort();
      } catch (IOException ioe) {
         throw new IllegalStateException(ioe);
      }
   }
}
