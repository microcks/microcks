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

import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.domain.workflow.WorkflowSession;
import io.microcks.domain.workflow.WorkflowSessionRecordedCall;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.domain.workflow.WorkflowRunStatus;
import io.microcks.repository.workflow.WorkflowDocumentRepository;
import io.microcks.repository.workflow.WorkflowSessionRecordedCallRepository;
import io.microcks.repository.workflow.WorkflowSessionRepository;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
   private WorkflowDocumentRepository workflowDocumentRepository;

   @Inject
   private WorkflowSessionRepository workflowSessionRepository;

   @Inject
   private WorkflowSessionRecordedCallRepository workflowSessionRecordedCallRepository;

   private WorkflowDocument saga;

   @BeforeEach
   void anArazzoDocumentIsImported() {
      saga = new WorkflowDocument();
      saga.setTitle("Order saga");
      saga.setVersion("1.0.0");
      saga.setWorkflowIds(List.of("placeOrderSaga"));
      saga.setSourceNames(List.of());
      saga.setWorkflowCalls(Map.of("placeOrderSaga", List.of()));
      saga.setContent("""
            arazzo: 1.0.1
            info:
              title: Order saga
              version: 1.0.0
            sourceDescriptions: []
            workflows:
              - workflowId: placeOrderSaga
                steps: []
            """);
      saga = workflowDocumentRepository.save(saga);
   }

   @Test
   void anOpenSessionAndItsCallsExpireAfterTheMaximumDuration() throws Exception {
      WorkflowSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();

      Date before = new Date();
      arazzoSessionService.recordCall(session.getId(), new WorkflowSessionRecordedCall());
      Date after = new Date();

      Date received = workflowSessionRecordedCallRepository.findBySessionIdOrderByIndexAsc(session.getId()).get(0)
            .getReceivedDate();
      assertTrue(!received.before(before) && !received.after(after), () -> received + " is not when it was received");
      WorkflowSession stored = workflowSessionRepository.findByIdOptional(session.getId()).orElseThrow();
      assertNull(stored.getClosedDate());
      assertEquals(120_000L, stored.getExpireAt().getTime() - stored.getOpenedDate().getTime());
      assertEquals(List.of(stored.getExpireAt()), expiriesOfTheCallsOf(session));
   }

   @Test
   void aClosedSessionAndItsCallsExpireAfterTheRetentionDelay() throws Exception {
      WorkflowSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();
      arazzoSessionService.recordCall(session.getId(), new WorkflowSessionRecordedCall());
      arazzoSessionService.recordCall(session.getId(), new WorkflowSessionRecordedCall());

      arazzoSessionService.closeSession(session.getId());

      WorkflowSession closed = workflowSessionRepository.findByIdOptional(session.getId()).orElseThrow();
      assertNotNull(closed.getClosedDate());
      assertEquals(60_000L, closed.getExpireAt().getTime() - closed.getClosedDate().getTime());
      assertEquals(List.of(closed.getExpireAt(), closed.getExpireAt()), expiriesOfTheCallsOf(session));
   }

   @Test
   void closingASessionAgainKeepsWhenItWasClosed() throws Exception {
      WorkflowSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();
      arazzoSessionService.closeSession(session.getId());
      Date closedDate = workflowSessionRepository.findByIdOptional(session.getId()).orElseThrow().getClosedDate();

      assertTrue(arazzoSessionService.closeSession(session.getId()));

      assertEquals(closedDate,
            workflowSessionRepository.findByIdOptional(session.getId()).orElseThrow().getClosedDate());
   }

   @Test
   void aSessionRecordsItsCallsUpToItsMaximum() throws Exception {
      WorkflowSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();

      for (int call = 0; call < 3; call++) {
         arazzoSessionService.recordCall(session.getId(), new WorkflowSessionRecordedCall());
      }

      assertEquals(List.of(0L, 1L), workflowSessionRecordedCallRepository
            .findBySessionIdOrderByIndexAsc(session.getId()).stream().map(WorkflowSessionRecordedCall::getIndex).toList());
   }

   /** A session that reached its maximum of calls fails its verification, telling how many calls it did not record. */
   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = { "2 | 0 | SUCCESS | null",
         "3 | 1 | FAILURE | session reached 2 calls, 1 call was not recorded",
         "5 | 3 | FAILURE | session reached 2 calls, 3 calls were not recorded" })
   void aSessionThatReachedItsMaximumFailsItsVerification(int calls, long dropped, WorkflowRunStatus status,
         String message) throws Exception {
      WorkflowSession session = arazzoSessionService.openSession(saga.getId(), "placeOrderSaga").orElseThrow();

      for (int call = 0; call < calls; call++) {
         arazzoSessionService.recordCall(session.getId(), new WorkflowSessionRecordedCall());
      }

      assertEquals(dropped,
            workflowSessionRepository.findByIdOptional(session.getId()).orElseThrow().getDroppedCallCount());
      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(status + " " + message, verification.getStatus() + " " + verification.getMessage());
   }

   private List<Date> expiriesOfTheCallsOf(WorkflowSession session) {
      return workflowSessionRecordedCallRepository.findBySessionIdOrderByIndexAsc(session.getId()).stream()
            .map(WorkflowSessionRecordedCall::getExpireAt).toList();
   }
}
