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
package io.microcks.domain.arazzo;

import io.microcks.domain.SecretRef;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * A run of a workflow of an Arazzo document: what was run, against what, and how each step ended, in the order the
 * steps were executed.
 * @author SebastienDegodez
 */
@MongoEntity(collection = "workflowResult")
public class WorkflowResult {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   private String id;
   private String documentId;
   private String documentTitle;
   private String documentVersion;
   private String workflowId;
   private WorkflowRunTarget target;
   private List<WorkflowSourceTarget> sources = new ArrayList<>();
   private Map<String, Object> inputs;
   private Map<String, Object> outputs;
   private WorkflowRunStatus status;
   private Date startedDate;
   private long elapsedTime;
   private Long timeout;
   private SecretRef secretRef;
   private List<WorkflowStepResult> steps = new ArrayList<>();
   private String message;
   private Long number;
   private WorkflowMissingExample missingExample;
   private boolean validateSchema;
   private WorkflowExampleCheck example;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getDocumentId() {
      return documentId;
   }

   public void setDocumentId(String documentId) {
      this.documentId = documentId;
   }

   public String getDocumentTitle() {
      return documentTitle;
   }

   public void setDocumentTitle(String documentTitle) {
      this.documentTitle = documentTitle;
   }

   public String getDocumentVersion() {
      return documentVersion;
   }

   public void setDocumentVersion(String documentVersion) {
      this.documentVersion = documentVersion;
   }

   public String getWorkflowId() {
      return workflowId;
   }

   public void setWorkflowId(String workflowId) {
      this.workflowId = workflowId;
   }

   public WorkflowRunTarget getTarget() {
      return target;
   }

   public void setTarget(WorkflowRunTarget target) {
      this.target = target;
   }

   public List<WorkflowSourceTarget> getSources() {
      return sources;
   }

   public void setSources(List<WorkflowSourceTarget> sources) {
      this.sources = sources;
   }

   public Map<String, Object> getInputs() {
      return inputs;
   }

   public void setInputs(Map<String, Object> inputs) {
      this.inputs = inputs;
   }

   public Map<String, Object> getOutputs() {
      return outputs;
   }

   public void setOutputs(Map<String, Object> outputs) {
      this.outputs = outputs;
   }

   public WorkflowRunStatus getStatus() {
      return status;
   }

   public void setStatus(WorkflowRunStatus status) {
      this.status = status;
   }

   public Date getStartedDate() {
      return startedDate;
   }

   public void setStartedDate(Date startedDate) {
      this.startedDate = startedDate;
   }

   public long getElapsedTime() {
      return elapsedTime;
   }

   public void setElapsedTime(long elapsedTime) {
      this.elapsedTime = elapsedTime;
   }

   public Long getTimeout() {
      return timeout;
   }

   public void setTimeout(Long timeout) {
      this.timeout = timeout;
   }

   public SecretRef getSecretRef() {
      return secretRef;
   }

   public void setSecretRef(SecretRef secretRef) {
      this.secretRef = secretRef;
   }

   public List<WorkflowStepResult> getSteps() {
      return steps;
   }

   public void setSteps(List<WorkflowStepResult> steps) {
      this.steps = steps;
   }

   public String getMessage() {
      return message;
   }

   public void setMessage(String message) {
      this.message = message;
   }

   public Long getNumber() {
      return number;
   }

   public void setNumber(Long number) {
      this.number = number;
   }

   /** @return The example the mocks lack for the run to go on; null when the run did not fail for a missing example */
   public WorkflowMissingExample getMissingExample() {
      return missingExample;
   }

   public void setMissingExample(WorkflowMissingExample missingExample) {
      this.missingExample = missingExample;
   }

   /** Whether each step response is validated against the OpenAPI schema of its operation. */
   public boolean isValidateSchema() {
      return validateSchema;
   }

   public void setValidateSchema(boolean validateSchema) {
      this.validateSchema = validateSchema;
   }

   /** The recorded example the run plays, with its verdict once the run ended; null when it plays none. */
   public WorkflowExampleCheck getExample() {
      return example;
   }

   public void setExample(WorkflowExampleCheck example) {
      this.example = example;
   }
}
