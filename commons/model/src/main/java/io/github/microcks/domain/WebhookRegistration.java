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
package io.github.microcks.domain;

import org.springframework.data.annotation.Id;

import java.util.Date;

/**
 * A domain object representing a Webhook registration.
 * @author laurent
 */
public class WebhookRegistration {

   @Id
   private String id;
   private Date createdOn = new Date();
   private Date expiresAt;
   private String operationId;
   private String operationMethod;
   private String targetUrl;
   private Long frequency;
   private Integer errorCount = 0;
   private Integer errorCountThreshold;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public Date getCreatedOn() {
      return createdOn;
   }

   public void setCreatedOn(Date createdOn) {
      this.createdOn = createdOn;
   }

   public Date getExpiresAt() {
      return expiresAt;
   }

   public void setExpiresAt(Date expiresAt) {
      this.expiresAt = expiresAt;
   }

   public String getOperationId() {
      return operationId;
   }

   public void setOperationId(String operationId) {
      this.operationId = operationId;
   }

   public String getOperationMethod() {
      return operationMethod;
   }

   public void setOperationMethod(String operationMethod) {
      this.operationMethod = operationMethod;
   }

   public String getTargetUrl() {
      return targetUrl;
   }

   public void setTargetUrl(String targetUrl) {
      this.targetUrl = targetUrl;
   }

   public Long getFrequency() {
      return frequency;
   }

   public void setFrequency(Long frequency) {
      this.frequency = frequency;
   }

   public Integer getErrorCount() {
      return errorCount;
   }

   public void setErrorCount(Integer errorCount) {
      this.errorCount = errorCount;
   }

   public Integer getErrorCountThreshold() {
      return errorCountThreshold;
   }

   public void setErrorCountThreshold(Integer errorCountThreshold) {
      this.errorCountThreshold = errorCountThreshold;
   }
}
