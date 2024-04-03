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
package io.github.microcks.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.h2.H2Backend;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * A configuration for starting an embedded MongoDB server, thanks to mongo-java-server. Only activated when using the
 * "uber" Spring profile.
 * @author laurent
 */
@Configuration
@Profile("uber")
public class EmbeddedMongoConfiguration {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(EmbeddedMongoConfiguration.class);

   @Value("${mongodb.storage.file:#{null}}")
   private Optional<String> storageFileName;

   private MongoClient client;
   private MongoServer server;

   private InetSocketAddress serverAddress;

   @Bean(destroyMethod = "shutdown")
   public MongoServer mongoServer() {
      if (storageFileName.isEmpty()) {
         log.info("Creating a new embedded Mongo Java Server with in-memory persistence");
         server = new MongoServer(new MemoryBackend());
      } else {
         log.info("Creating a new embedded Mongo Java Server with disk persistence at {}", storageFileName.get());
         server = new MongoServer(new H2Backend(storageFileName.get()));
      }

      serverAddress = server.bind();
      return server;
   }

   @Bean(destroyMethod = "close")
   public MongoClient mongoClient() {
      if (server == null) {
         mongoServer();
      }
      if (client == null) {
         client = MongoClients.create("mongodb://localhost:" + serverAddress.getPort());
      }
      return client;
   }
}
