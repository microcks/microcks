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

import io.microcks.domain.Header;
import io.microcks.domain.ServiceState;
import io.microcks.domain.arazzo.ArazzoSession;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.repository.RequestRepository;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.ServiceStateRepository;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSessionCallRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.service.ServiceStateStore;
import io.microcks.service.arazzo.ArazzoDocumentService;
import io.microcks.service.arazzo.ArazzoSessionAnswerService;
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.HttpMethod;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;
import io.microcks.web.AbstractBaseIT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoWriteException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * Two calls of the same step of an Arazzo session landing at the same instant, as when two sagas wrongly share one
 * session id or an application retries in parallel. The race is made deterministic: the two writers of the state of the
 * step read it in lockstep, so that neither sees what the other writes, then both write it. The session must then keep
 * one state for the step, and its later calls must still be answered.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSessionStateRaceIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoMetadataRepository arazzoMetadataRepository;

   @Inject
   private ArazzoSessionRepository arazzoSessionRepository;

   @Inject
   private ArazzoSessionCallRepository arazzoSessionCallRepository;

   @Inject
   private RequestRepository requestRepository;

   @Inject
   private ResponseRepository responseRepository;

   @Inject
   private ServiceStateRepository serviceStateRepository;

   @TempDir
   private Path uploads;

   private final ExecutorService writers = Executors.newFixedThreadPool(2);

   /** Marks the two writers, whose reads of the state go in lockstep. */
   private final ThreadLocal<Boolean> racing = ThreadLocal.withInitial(() -> false);

   @BeforeEach
   void orderSagaIsUploadedAndLinked() throws IOException {
      for (String api : List.of("order-api", "stock-api", "payment-api")) {
         uploadArtifactFile(example("stateful-saga.contracts.yaml", api), true);
      }
      uploadArtifactFile(example("stateful-saga.arazzo.yaml", "order-saga"), true);
      uploadArtifactFile(example("stateful-saga.arazzo.yaml", "order-saga-metadata"), false);
   }

   @AfterEach
   void deleteDocumentsSessionsAndState() {
      writers.shutdownNow();
      arazzoDocumentRepository.deleteAll();
      arazzoMetadataRepository.deleteAll();
      arazzoSessionRepository.deleteAll();
      arazzoSessionCallRepository.deleteAll();
      serviceStateRepository.deleteAll();
   }

   /**
    * createOrder is answered twice in session A at the same instant: A keeps one state for it, and its next call,
    * reserveStock, is answered with the order createOrder placed.
    */
   @Test
   void twoCallsOfOneStepAtTheSameInstantKeepOneStateAndTheSessionGoesOn() throws Exception {
      String sessionId = openSession();
      ArazzoSession session = arazzoSessionRepository.findByIdOptional(sessionId).orElseThrow();
      ArazzoSessionAnswerService answers = new ArazzoSessionAnswerService(arazzoDocumentService,
            arazzoSessionRepository, requestRepository, responseRepository, readingInLockstep());

      bothAtTheSameInstant(() -> answers.remember(session, createOrderAnswered()));

      ResponseEntity<String> reservation = callMocks(sessionId, "Stock+API", "/reservations",
            "{\"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}]}");
      assertEquals(201, reservation.getStatusCode().value(), reservation::toString);
      assertEquals("o-42", JSON.readTree(reservation.getBody()).get("orderId").asText(), reservation.getBody());
      assertEquals(1, statesOf("arazzo-session:" + sessionId + ":createOrder"));
   }

   /**
    * The same race in the state store the SCRIPT, GROOVY and JS dispatchers use: two puts of one key at the same
    * instant keep one value, which a script reads.
    */
   @Test
   void twoPutsOfOneKeyAtTheSameInstantKeepOneValue() throws Exception {
      ServiceStateStore store = new ServiceStateStore(readingInLockstep(), "a-service");

      bothAtTheSameInstant(() -> store.put("counter", "1"));

      assertEquals("1", new ServiceStateStore(serviceStateRepository, "a-service").get("counter"));
      assertEquals(1, statesOf("counter"));
   }

   /**
    * The database itself keeps one state per key of a service: the index ensured at startup refuses a second one, so
    * that two writes the database runs at the very same instant cannot create two.
    */
   @Test
   void theDatabaseKeepsOneStatePerKeyOfAService() {
      ServiceState first = new ServiceState("a-service", "counter");
      first.setValue("1");
      serviceStateRepository.save(first);

      ServiceState second = new ServiceState("a-service", "counter");
      second.setValue("2");
      assertThrows(MongoWriteException.class, () -> serviceStateRepository.save(second));
      assertEquals("1", new ServiceStateStore(serviceStateRepository, "a-service").get("counter"));
   }

   /**
    * The repository of the state stores, as it is, but each read of a writer waits for the same read of the other one.
    */
   private ServiceStateRepository readingInLockstep() {
      CyclicBarrier lockstep = new CyclicBarrier(2);
      ServiceStateRepository repository = mock(ServiceStateRepository.class, delegatesTo(serviceStateRepository));
      doAnswer(read -> {
         ServiceState state = serviceStateRepository.findByServiceIdAndKey(read.getArgument(0), read.getArgument(1));
         if (racing.get()) {
            lockstep.await(10, TimeUnit.SECONDS);
         }
         return state;
      }).when(repository).findByServiceIdAndKey(any(), any());
      return repository;
   }

   /** Two writers run the same write at the same instant. */
   private void bothAtTheSameInstant(Runnable write) throws Exception {
      List<Future<?>> both = List.of(writers.submit(() -> racingWrite(write)),
            writers.submit(() -> racingWrite(write)));
      for (Future<?> writer : both) {
         writer.get(30, TimeUnit.SECONDS);
      }
   }

   private void racingWrite(Runnable write) {
      racing.set(true);
      try {
         write.run();
      } finally {
         racing.set(false);
      }
   }

   private long statesOf(String key) {
      return serviceStateRepository.count(new Document("key", key));
   }

   /** The createOrder call of the saga, as the mocks of Order API answered it with order o-42. */
   private static ArazzoSessionCall createOrderAnswered() {
      ArazzoSessionCall call = new ArazzoSessionCall();
      call.setServiceName("Order API");
      call.setServiceVersion("1.0.0");
      call.setOperationName("POST /orders");
      call.setMethod("POST");
      call.setPath("/orders");
      call.setRequestUrl("http://localhost/rest/Order+API/1.0.0/orders");
      call.setRequestHeaders(Set.of(new Header("Content-Type", Set.of("application/json"))));
      call.setRequestBody("{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], "
            + "\"amount\": 25.0, \"currency\": \"EUR\"}");
      call.setResponseStatus(201);
      call.setResponseHeaders(Set.of(new Header("Content-Type", Set.of("application/json"))));
      call.setResponseBody("{\"id\": \"o-42\", \"customerId\": \"c-7\", \"amount\": 25.0, \"status\": \"PLACED\"}");
      return call;
   }

   private String openSession() throws Exception {
      ResponseEntity<String> opened = restTemplate.exchange("/api/arazzo/sessions", HttpMethod.POST,
            new HttpEntity<>("{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"placeOrderSaga\", "
                  + "\"inputs\": {\"customerId\": \"c-7\"}}", jsonHeaders()),
            String.class);
      assertEquals(201, opened.getStatusCode().value(), opened.getBody());
      return JSON.readTree(opened.getBody()).get("id").asText();
   }

   /** The saga service calls the mocks of an API 1.0.0, naming its session in the x-microcks-session header. */
   private ResponseEntity<String> callMocks(String sessionId, String api, String path, String body) {
      HttpHeaders headers = jsonHeaders();
      headers.set("x-microcks-session", sessionId);
      return restTemplate.exchange("/rest/" + api + "/1.0.0" + path, HttpMethod.POST, new HttpEntity<>(body, headers),
            String.class);
   }

   private static HttpHeaders jsonHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));
      return headers;
   }

   /** Writes the example of a multi-document fixture to its own file, as the API developer would upload it. */
   private String example(String fixture, String example) throws IOException {
      String marker = "# example: " + example + " ";
      String document = Arrays.stream(Files.readString(Path.of(FIXTURES + fixture)).split("\n---\n"))
            .filter(candidate -> candidate.contains(marker)).findFirst().orElseThrow();
      Path file = uploads.resolve(example + (fixture.contains("arazzo") ? ".arazzo.yaml" : ".yaml"));
      Files.writeString(file, document, StandardCharsets.UTF_8);
      return file.toString();
   }
}
