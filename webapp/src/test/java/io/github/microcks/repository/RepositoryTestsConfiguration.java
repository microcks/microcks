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
package io.github.microcks.repository;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import javax.annotation.PreDestroy;
import java.net.InetSocketAddress;

/**
 * Spring configuration for repositories tests.
 * @author laurent
 */
@TestConfiguration
@ComponentScan(basePackages = { "io.github.microcks.security", "io.github.microcks.service" })
@EnableMongoRepositories(basePackages = { "io.github.microcks.repository" })
public class RepositoryTestsConfiguration extends AbstractMongoClientConfiguration {

   private MongoClient client;
   private MongoServer server;

   @Override
   protected String getDatabaseName() {
      return "demo-test";
   }

   @Override
   public MongoClient mongoClient() {
      // Replacing fongo by mongo-java-server, according to
      // https://github.com/fakemongo/fongo/issues/337
      //return new Fongo("mongo-test").getMongo();

      // Create a server and bind on a random local port
      server = new MongoServer(new MemoryBackend());
      InetSocketAddress serverAddress = server.bind();
      //client = new MongoClient(new ServerAddress(serverAddress));
      client = MongoClients.create("mongodb://localhost:" + serverAddress.getPort());
      return client;
   }

   @PreDestroy
   public void shutdown() {
      client.close();
      server.shutdown();
   }
}
