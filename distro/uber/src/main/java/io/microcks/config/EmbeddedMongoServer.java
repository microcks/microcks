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
package io.microcks.config;

import de.bwaldvogel.mongo.MongoBackend;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.h2.H2Backend;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Holder of the embedded MongoDB-protocol server (mongo-java-server) used by the uber distribution. The server is
 * started once, on first use, by {@link EmbeddedMongoConfigSourceFactory} so that it is listening before the Quarkus
 * MongoDB client reads its connection string.
 * @author laurent
 */
public final class EmbeddedMongoServer {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(EmbeddedMongoServer.class);

   private static MongoServer server;
   private static String connectionString;

   private EmbeddedMongoServer() {
   }

   /**
    * Start the embedded server if not already done and return the connection string to use with a Mongo client.
    * @param storageFile The path of the file used for persistence, null or blank for in-memory persistence
    * @return The mongodb:// connection string of the running embedded server
    */
   public static synchronized String start(String storageFile) {
      if (server == null) {
         MongoBackend backend;
         if (storageFile == null || storageFile.isBlank()) {
            log.info("Creating a new embedded Mongo Java Server with in-memory persistence");
            backend = new MemoryBackend();
         } else {
            log.info("Creating a new embedded Mongo Java Server with disk persistence at {}", storageFile);
            backend = new H2Backend(storageFile);
         }
         server = new MongoServer(backend);
         InetSocketAddress address = server.bind();
         connectionString = "mongodb://127.0.0.1:" + address.getPort();
         Runtime.getRuntime().addShutdownHook(new Thread(EmbeddedMongoServer::stop, "embedded-mongo-shutdown"));
      }
      return connectionString;
   }

   /** Stop the embedded server, flushing the file when persistent. */
   public static synchronized void stop() {
      if (server != null) {
         server.shutdownNow();
         server = null;
         connectionString = null;
      }
   }
}
