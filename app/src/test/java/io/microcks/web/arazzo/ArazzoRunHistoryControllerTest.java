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
package io.microcks.web;

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.microcks.domain.WorkflowResult;
import io.microcks.domain.WorkflowRunCounts;
import io.microcks.domain.WorkflowRunStatus;
import io.microcks.domain.WorkflowRunSummary;
import io.microcks.domain.WorkflowRunTarget;
import io.microcks.repository.WorkflowResultRepository;
import io.microcks.service.ArazzoRunHistoryService;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the runs history endpoints as the UI calls them, on the history use case.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunHistoryControllerTest {

   @Inject
   private ArazzoRunHistoryService arazzoRunHistoryService;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   @Test
   void theRunsOfADocumentAreListedAndCountedWithTheFiltersAndPageAsked() {
      ArazzoRunHistoryController controller = new ArazzoRunHistoryController(arazzoRunHistoryService);
      runIs(1, "readThenReprice", WorkflowRunTarget.MOCKS, WorkflowRunStatus.SUCCESS);
      runIs(2, "readThenReprice", WorkflowRunTarget.IMPLEMENTATIONS, WorkflowRunStatus.FAILURE);
      runIs(3, "listThenReadFirst", WorkflowRunTarget.MOCKS, WorkflowRunStatus.FAILURE);

      assertEquals(List.of(3L, 2L, 1L), numbers(controller.listRuns("pastry", null, null, null, 0, 20)));
      assertEquals(List.of(2L), numbers(controller.listRuns("pastry", "readThenReprice", null, null, 0, 1)));
      assertEquals(List.of(1L), numbers(controller.listRuns("pastry", "readThenReprice", null, null, 1, 1)));
      assertEquals(List.of(3L),
            numbers(controller.listRuns("pastry", null, WorkflowRunTarget.MOCKS.name(), WorkflowRunStatus.FAILURE.name(), 0, 20)));
      assertEquals(new WorkflowRunCounts(3, 1, 2), controller.countRuns("pastry", null, null, null));
      assertEquals(new WorkflowRunCounts(1, 0, 1),
            controller.countRuns("pastry", "readThenReprice", WorkflowRunTarget.IMPLEMENTATIONS.name(), null));
      assertEquals(new WorkflowRunCounts(2, 0, 2),
            controller.countRuns("pastry", null, null, WorkflowRunStatus.FAILURE.name()));
   }

   private void runIs(long number, String workflowId, WorkflowRunTarget target, WorkflowRunStatus status) {
      WorkflowResult run = new WorkflowResult();
      run.setDocumentId("pastry");
      run.setNumber(number);
      run.setWorkflowId(workflowId);
      run.setTarget(target);
      run.setStatus(status);
      workflowResultRepository.save(run);
   }

   private static List<Long> numbers(List<WorkflowRunSummary> runs) {
      return runs.stream().map(WorkflowRunSummary::number).toList();
   }
}
