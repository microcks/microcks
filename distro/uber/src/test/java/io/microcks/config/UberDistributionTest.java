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

import io.microcks.event.ServiceViewChangeEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the uber distribution and checks the embedded MongoDB server, an artifact import with its mock, and the
 * WebSocket publication of service changes.
 * @author laurent
 */
@QuarkusTest
class UberDistributionTest {

   @Inject
   MongoClient mongoClient;

   @ConfigProperty(name = "quarkus.mongodb.connection-string")
   String connectionString;

   @ConfigProperty(name = "quarkus.mongodb.database")
   String database;

   @ConfigProperty(name = "quarkus.http.test-port")
   int httpPort;

   @Test
   void testEmbeddedMongoServerIsUsed() {
      assertTrue(connectionString.startsWith("mongodb://127.0.0.1:"), connectionString);
      Document pong = mongoClient.getDatabase(database).runCommand(new Document("ping", 1));
      assertEquals(1.0, ((Number) pong.get("ok")).doubleValue());
   }

   @Test
   void testImportThenMockAndPersistInEmbeddedMongo() {
      given().multiPart("file", new File("target/test-classes/samples/APIPastry-openapi.yaml")).when()
            .post("/api/artifact/upload").then().statusCode(201);

      given().when().get("/api/services").then().statusCode(200).body("name", hasItem("API Pastry - 2.0"));
      given().when().get("/rest/API+Pastry+-+2.0/2.0.0/pastry/Millefeuille").then().statusCode(200).body("name",
            equalTo("Millefeuille"));

      long count = mongoClient.getDatabase(database).getCollection("service")
            .countDocuments(new Document("name", "API Pastry - 2.0"));
      assertTrue(count >= 1, "Service should be stored in the embedded Mongo server");
   }

   @Test
   void testServiceChangesArePublishedOnWebSocket() throws Exception {
      CompletableFuture<String> firstMessage = new CompletableFuture<>();
      WebSocket.Listener listener = new WebSocket.Listener() {
         @Override
         public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            firstMessage.complete(data.toString());
            webSocket.request(1);
            return null;
         }
      };
      WebSocket webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
            .buildAsync(URI.create("ws://localhost:" + httpPort + "/api/services-updates"), listener)
            .get(10, TimeUnit.SECONDS);
      webSocket.sendText("_ready_", true).get(10, TimeUnit.SECONDS);

      given().multiPart("file", new File("target/test-classes/samples/HelloAPI-swagger.json")).when()
            .post("/api/artifact/upload").then().statusCode(201);

      String message = firstMessage.get(20, TimeUnit.SECONDS);
      ServiceViewChangeEvent event = new ObjectMapper().readValue(message, ServiceViewChangeEvent.class);
      assertNotNull(event.getServiceId());
      assertNotNull(event.getServiceView());
      assertEquals("Hello API", event.getServiceView().getService().getName());
      webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(10, TimeUnit.SECONDS);
   }
}
