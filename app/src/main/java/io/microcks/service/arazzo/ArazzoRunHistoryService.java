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

import io.microcks.domain.arazzo.ArazzoLastResult;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.domain.arazzo.WorkflowRunCounts;
import io.microcks.domain.arazzo.WorkflowRunStatus;
import io.microcks.domain.arazzo.WorkflowRunSummary;
import io.microcks.domain.arazzo.WorkflowStepResult;
import io.microcks.domain.arazzo.WorkflowStepStatus;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import io.microcks.repository.arazzo.WorkflowRunFilter;


import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service bean reading the runs history of the workflows of an Arazzo document: the history page, the recent runs of a
 * workflow and the last result of each workflow all read this same query.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoRunHistoryService {

   private final WorkflowResultRepository workflowResultRepository;

   /**
    * Build a new ArazzoRunHistoryService with its required dependencies.
    * @param workflowResultRepository The repository of the runs
    */
   @Inject
   public ArazzoRunHistoryService(WorkflowResultRepository workflowResultRepository) {
      this.workflowResultRepository = workflowResultRepository;
   }

   /**
    * List a page of the runs of the workflows of an Arazzo document, newest first.
    * @param documentId The id of the Arazzo document
    * @param filter     The filters the listed runs match
    * @param page       The page to list, from 0
    * @param size       The number of runs in a page
    * @return The runs of that page, newest first
    */
   public List<WorkflowRunSummary> getRuns(String documentId, WorkflowRunFilter filter, int page, int size) {
      return workflowResultRepository
            .findByDocumentIdAndFilter(documentId, filter, page, size).stream()
            .map(ArazzoRunHistoryService::summary).toList();
   }

   /**
    * Count the runs of the workflows of an Arazzo document that match filters.
    * @param documentId The id of the Arazzo document
    * @param filter     The filters the counted runs match
    * @return How many runs match, and how many of them passed and failed
    */
   public WorkflowRunCounts countRuns(String documentId, WorkflowRunFilter filter) {
      return new WorkflowRunCounts(workflowResultRepository.countByDocumentIdAndFilter(documentId, filter),
            countEnded(documentId, filter, WorkflowRunStatus.SUCCESS),
            countEnded(documentId, filter, WorkflowRunStatus.FAILURE));
   }

   /** The runs that ended so among those the filters keep: none when the filters keep another result. */
   private long countEnded(String documentId, WorkflowRunFilter filter, WorkflowRunStatus result) {
      if (filter.result() != null && filter.result() != result) {
         return 0;
      }
      return workflowResultRepository.countByDocumentIdAndFilter(documentId,
            new WorkflowRunFilter(filter.workflowId(), filter.target(), result));
   }

   /**
    * @param documentId The id of an Arazzo document
    * @param workflowId The id of a workflow of that document
    * @return The last run of that workflow; null when it never ran
    */
   public WorkflowRunSummary getLastRun(String documentId, String workflowId) {
      return getRuns(documentId, new WorkflowRunFilter(workflowId, null, null), 0, 1).stream().findFirst().orElse(null);
   }

   /**
    * @param lastRun The last run of a workflow; null when it never ran
    * @return How that run went
    */
   public static ArazzoLastResult lastResult(WorkflowRunSummary lastRun) {
      if (lastRun == null) {
         return ArazzoLastResult.NEVER_RUN;
      }
      return switch (lastRun.status()) {
         case SUCCESS -> ArazzoLastResult.PASSED;
         case FAILURE -> ArazzoLastResult.FAILED;
         case IN_PROGRESS -> ArazzoLastResult.IN_PROGRESS;
      };
   }

   /**
    * The history shows a failed run with the step it failed at: its last failed step, as the attempts of a retried step
    * before it fail too. A run that passed failed at no step, even when some attempts of a retried step failed.
    */
   private static WorkflowRunSummary summary(WorkflowResult run) {
      String failedStepId = run.getStatus() != WorkflowRunStatus.FAILURE ? null
            : run.getSteps().stream().filter(step -> step.status() == WorkflowStepStatus.FAILED)
                  .map(WorkflowStepResult::stepId).reduce((first, last) -> last).orElse(null);
      return new WorkflowRunSummary(run.getId(), run.getNumber(), run.getWorkflowId(), run.getTarget(), run.getStatus(),
            failedStepId, run.getStartedDate(), run.getElapsedTime(),
            run.getExample() == null ? null : run.getExample().name(),
            run.getExample() == null ? null : run.getExample().verdict());
   }
}
