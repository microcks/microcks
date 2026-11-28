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
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.domain.workflow.WorkflowRunStatus;
import io.microcks.repository.workflow.WorkflowDocumentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests of how a call to the Mock URL of a workflow waits for the run the minion reports later.
 * @author SebastienDegodez
 */
class ArazzoMockUrlServiceTest {

   private final WorkflowDocumentRepository workflowDocumentRepository = mock(WorkflowDocumentRepository.class);
   private final ArazzoRunService arazzoRunService = mock(ArazzoRunService.class);
   private final ArazzoMockUrlService service = new ArazzoMockUrlService(workflowDocumentRepository, arazzoRunService);
   private final WorkflowResult inProgress = run(WorkflowRunStatus.IN_PROGRESS);

   @BeforeEach
   void readThenRepriceIsLaunchedInProgress() throws Exception {
      WorkflowDocument document = new WorkflowDocument();
      document.setId("doc-1");
      document.setWorkflowIds(List.of("readThenReprice"));
      when(workflowDocumentRepository.findByTitleAndVersion("API Pastry scenarios", "1.0.0")).thenReturn(document);
      when(arazzoRunService.launchMockUrlRun("doc-1", "readThenReprice", Map.of())).thenReturn(Optional.of(inProgress));
      service.timeout = 2000L;
   }

   /** The call waits for an outcome the minion reports later, reading the run at a modest pace. */
   @Test
   void theCallWaitsForTheOutcomeReadingTheRunAtAModestPace() {
      AtomicInteger reads = new AtomicInteger();
      long reportedAt = System.currentTimeMillis() + 300;
      when(arazzoRunService.getRun("run-1")).thenAnswer(invocation -> {
         reads.incrementAndGet();
         return Optional.of(System.currentTimeMillis() < reportedAt ? inProgress : run(WorkflowRunStatus.SUCCESS));
      });

      ArazzoMockUrlAnswer answer = service.play("API Pastry scenarios", "1.0.0", "readThenReprice", null);

      assertEquals(200, answer.status());
      assertTrue(reads.get() > 1 && reads.get() <= 10, () -> "the run was read " + reads.get() + " times");
   }

   /** A run that does not end is read every 50 ms until the call stops waiting, then answered 504. */
   @Test
   void aRunThatDoesNotEndIsReadEvery50MsUntilTheCallStopsWaiting() {
      service.timeout = 200L;
      AtomicInteger reads = new AtomicInteger();
      when(arazzoRunService.getRun("run-1")).thenAnswer(invocation -> {
         reads.incrementAndGet();
         return Optional.of(inProgress);
      });

      ArazzoMockUrlAnswer answer = service.play("API Pastry scenarios", "1.0.0", "readThenReprice", null);

      assertEquals("504 Run #1 did not end within 200 ms after 5 reads",
            answer.status() + " " + answer.reason() + " after " + reads.get() + " reads");
   }

   /** An interrupted call stops waiting, answers that the run did not end, and keeps the thread interrupted. */
   @Test
   void anInterruptedCallStopsWaiting() {
      when(arazzoRunService.getRun("run-1")).thenReturn(Optional.of(inProgress));
      Thread.currentThread().interrupt();

      ArazzoMockUrlAnswer answer = service.play("API Pastry scenarios", "1.0.0", "readThenReprice", null);

      assertTrue(Thread.interrupted());
      assertEquals("504 Run #1 did not end within 2000 ms", answer.status() + " " + answer.reason());
   }

   private static WorkflowResult run(WorkflowRunStatus status) {
      WorkflowResult run = new WorkflowResult();
      run.setId("run-1");
      run.setNumber(1L);
      run.setStatus(status);
      run.setOutputs(Map.of());
      return run;
   }
}
