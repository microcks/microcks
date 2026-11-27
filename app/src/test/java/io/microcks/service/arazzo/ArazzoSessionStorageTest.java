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
package io.microcks.service.arazzo;

import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoSession;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSessionCallRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;

import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests of how long the sessions and their calls are kept, and how many calls a session records: an open session
 * expires after its maximum duration, a closed one after the retention delay, with its calls; a session records its
 * calls up to its maximum.
 * @author SebastienDegodez
 */
@QuarkusTest
@TestProfile(ArazzoSessionStorageTest.ShortSessions.class)
class ArazzoSessionStorageTest {

   /** The sessions expire after 120 s open or 60 s closed, and record 2 calls at most. */
   public static class ShortSessions implements QuarkusTestProfile {

      @Override
      public Map<String, String> getConfigOverrides() {
         return Map.of("arazzo.sessions.max-open-duration", "120", "arazzo.sessions.retention", "60",
               "arazzo.sessions.max-calls", "2");
      }
   }

   @Inject
   private ArazzoSessionService arazzoSessionService;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoSessionRepository arazzoSessionRepository;

   @Inject
   private ArazzoSessionCallRepository arazzoSessionCallRepository;

   private ArazzoDocument saga;

   @BeforeEach
   void anArazzoDocumentIsImported() {
      saga = new ArazzoDocument();
      saga.setTitle("Order saga");
      saga.setVersion("1.0.0");
      saga.setWorkflowIds(List.of("placeOrderSaga"));
      saga.setSourceNames(List.of());
      saga.setWorkflowCalls(Map.of("placeOrderSaga", List.of()));
      saga = arazzoDocumentRepository.save(saga);
   }

   @Test
   void anOpenSessionAndItsCallsExpireAfterTheMaximumDuration() throws Exception {
      ArazzoSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();

      arazzoSessionService.recordCall(session.getId(), new ArazzoSessionCall());

      ArazzoSession stored = arazzoSessionRepository.findByIdOptional(session.getId()).orElseThrow();
      assertNull(stored.getClosedDate());
      assertEquals(120_000L, stored.getExpireAt().getTime() - stored.getOpenedDate().getTime());
      assertEquals(List.of(stored.getExpireAt()), expiriesOfTheCallsOf(session));
   }

   @Test
   void aClosedSessionAndItsCallsExpireAfterTheRetentionDelay() throws Exception {
      ArazzoSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();
      arazzoSessionService.recordCall(session.getId(), new ArazzoSessionCall());
      arazzoSessionService.recordCall(session.getId(), new ArazzoSessionCall());

      arazzoSessionService.closeSession(session.getId());

      ArazzoSession closed = arazzoSessionRepository.findByIdOptional(session.getId()).orElseThrow();
      assertNotNull(closed.getClosedDate());
      assertEquals(60_000L, closed.getExpireAt().getTime() - closed.getClosedDate().getTime());
      assertEquals(List.of(closed.getExpireAt(), closed.getExpireAt()), expiriesOfTheCallsOf(session));
   }

   @Test
   void closingASessionAgainKeepsWhenItWasClosed() throws Exception {
      ArazzoSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();
      arazzoSessionService.closeSession(session.getId());
      Date closedDate = arazzoSessionRepository.findByIdOptional(session.getId()).orElseThrow().getClosedDate();

      arazzoSessionService.closeSession(session.getId());

      assertEquals(closedDate, arazzoSessionRepository.findByIdOptional(session.getId()).orElseThrow().getClosedDate());
   }

   @Test
   void aSessionRecordsItsCallsUpToItsMaximum() throws Exception {
      ArazzoSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();

      for (int call = 0; call < 3; call++) {
         arazzoSessionService.recordCall(session.getId(), new ArazzoSessionCall());
      }

      assertEquals(List.of(0L, 1L), arazzoSessionCallRepository.findBySessionIdOrderByIndexAsc(session.getId()).stream()
            .map(ArazzoSessionCall::getIndex).toList());
   }

   private List<Date> expiriesOfTheCallsOf(ArazzoSession session) {
      return arazzoSessionCallRepository.findBySessionIdOrderByIndexAsc(session.getId()).stream()
            .map(ArazzoSessionCall::getExpireAt).toList();
   }
}
