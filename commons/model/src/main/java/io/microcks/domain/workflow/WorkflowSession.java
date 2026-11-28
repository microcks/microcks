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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A verification session: a test opens it on a workflow of an Arazzo document, the application it tests calls the
 * Microcks mocks naming the session, and the mocks record these calls in their order of receipt, so that Microcks can
 * verify that the application followed the workflow. It expires at its expiry date: after a maximum duration while it
 * is open, after a retention delay once it is closed.
 * @author SebastienDegodez
 */
@MongoEntity(collection = "arazzoSession")
public class ArazzoSession {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   private String id;
   private String documentId;
   private String documentTitle;
   private String documentVersion;
   private String workflowId;
   private Date openedDate;
   private Date closedDate;
   private long droppedCallCount;
   private Map<String, Object> inputs;
   private long callCount;
   private Date expireAt;
   private String verificationRunId;
   private Map<String, String> failures = new LinkedHashMap<>();
   private List<ArazzoInjectedFailure> injectedFailures = new ArrayList<>();

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

   public Date getOpenedDate() {
      return openedDate;
   }

   public void setOpenedDate(Date openedDate) {
      this.openedDate = openedDate;
   }

   /** When the session was closed; null while it is open and records the calls naming it. */
   public Date getClosedDate() {
      return closedDate;
   }

   public void setClosedDate(Date closedDate) {
      this.closedDate = closedDate;
   }

   /** How many calls the session received: the order index of the next call it records. */
   public long getCallCount() {
      return callCount;
   }

   public void setCallCount(long callCount) {
      this.callCount = callCount;
   }

   /** Whether the session still records the calls naming it ({@code open}) or not ({@code closed}). */
   @BsonIgnore
   @JsonProperty(value = "state", access = JsonProperty.Access.READ_ONLY)
   public String getState() {
      return closedDate == null ? "open" : "closed";
   }

   /** The id of the run its latest verification created; null while the session was not verified. */
   public String getVerificationRunId() {
      return verificationRunId;
   }

   public void setVerificationRunId(String verificationRunId) {
      this.verificationRunId = verificationRunId;
   }

   public Date getExpireAt() {
      return expireAt;
   }

   public void setExpireAt(Date expireAt) {
      this.expireAt = expireAt;
   }

   /** How many calls the session did not record, as it had recorded its maximum of calls. */
   public long getDroppedCallCount() {
      return droppedCallCount;
   }

   public void setDroppedCallCount(long droppedCallCount) {
      this.droppedCallCount = droppedCallCount;
   }

   /** The inputs of the workflow the session was opened with, by name; null when it was opened without. */
   public Map<String, Object> getInputs() {
      return inputs;
   }

   public void setInputs(Map<String, Object> inputs) {
      this.inputs = inputs;
   }

   /** The steps set to fail in this session, by step id, with the name of the example each one answers with. */
   public Map<String, String> getFailures() {
      return failures;
   }

   public void setFailures(Map<String, String> failures) {
      this.failures = failures;
   }

   /** The failures of this session, each with the operation of the mocks that answers it; not shown to clients. */
   @JsonIgnore
   public List<ArazzoInjectedFailure> getInjectedFailures() {
      return injectedFailures;
   }

   public void setInjectedFailures(List<ArazzoInjectedFailure> injectedFailures) {
      this.injectedFailures = injectedFailures;
   }
}
