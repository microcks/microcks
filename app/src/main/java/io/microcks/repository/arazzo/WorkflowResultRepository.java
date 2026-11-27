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
package io.microcks.repository.arazzo;

import io.microcks.repository.ObjectIdRepository;
import io.microcks.domain.arazzo.WorkflowResult;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;

import java.util.List;

/**
 * Repository for the runs of Arazzo workflows.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class WorkflowResultRepository implements ObjectIdRepository<WorkflowResult> {

   /**
    * @param documentId The id of an Arazzo document
    * @return The run of the workflows of that document with the highest number; null when none ran
    */
   public WorkflowResult findTopByDocumentIdOrderByNumberDesc(String documentId) {
      return find("documentId", Sort.descending("number"), documentId).firstResult();
   }

   /**
    * @param documentId The id of an Arazzo document
    * @param filter     The filters the runs match
    * @param page       The index of the page of runs to find, newest run first
    * @param size       The size of the page
    * @return The runs of the workflows of that document matching the filters, in that page
    */
   public List<WorkflowResult> findByDocumentIdAndFilter(String documentId, WorkflowRunFilter filter, int page,
         int size) {
      return find(query(documentId, filter), new Document("number", -1)).page(Page.of(page, size)).list();
   }

   /**
    * @param documentId The id of an Arazzo document
    * @param filter     The filters the runs match
    * @return How many runs of the workflows of that document match the filters
    */
   public long countByDocumentIdAndFilter(String documentId, WorkflowRunFilter filter) {
      return count(query(documentId, filter));
   }

   /** The runs of a document, each filter adding its criterion only when it is given. */
   private static Document query(String documentId, WorkflowRunFilter filter) {
      Document query = new Document("documentId", documentId);
      if (filter.workflowId() != null) {
         query.append("workflowId", filter.workflowId());
      }
      if (filter.target() != null) {
         query.append("target", filter.target().name());
      }
      if (filter.result() != null) {
         query.append("status", filter.result().name());
      }
      return query;
   }
}
