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
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document("invocations")
public class InvocationLogEntry {
   @Id
   private Long timestampEpoch;
   private String serviceName;
   /**
   *
   */
   private String serviceVersion;
   /**
   *
   */
   private String mockResponse;
   /**
   *
   */
   private Date invocationTimestamp;
   /**
   *
   */
   private long duration;
   private String source;
   private String requestId;

   public InvocationLogEntry(Long timestampEpoch, String serviceName, String serviceVersion, String mockResponse,
         Date invocationTimestamp, long duration, String source, String requestId) {
      this.timestampEpoch = timestampEpoch;
      this.serviceName = serviceName;
      this.serviceVersion = serviceVersion;
      this.mockResponse = mockResponse;
      this.invocationTimestamp = invocationTimestamp;
      this.duration = duration;
      this.source = source;
      this.requestId = requestId;
   }

   public InvocationLogEntry() {
   }

   public Long getTimestampEpoch() {
      return timestampEpoch;
   }

   public void setTimestampEpoch(Long timestampEpoch) {
      this.timestampEpoch = timestampEpoch;
   }

   public String getServiceName() {
      return serviceName;
   }

   public void setServiceName(String serviceName) {
      this.serviceName = serviceName;
   }

   public String getServiceVersion() {
      return serviceVersion;
   }

   public void setServiceVersion(String serviceVersion) {
      this.serviceVersion = serviceVersion;
   }

   public String getMockResponse() {
      return mockResponse;
   }

   public void setMockResponse(String mockResponse) {
      this.mockResponse = mockResponse;
   }

   public Date getInvocationTimestamp() {
      return invocationTimestamp;
   }

   public void setInvocationTimestamp(Date invocationTimestamp) {
      this.invocationTimestamp = invocationTimestamp;
   }

   public long getDuration() {
      return duration;
   }

   public void setDuration(long duration) {
      this.duration = duration;
   }

   public String getSource() {
      return source;
   }

   public void setSource(String source) {
      this.source = source;
   }

   public String getRequestId() {
      return requestId;
   }

   public void setRequestId(String requestId) {
      this.requestId = requestId;
   }
}
