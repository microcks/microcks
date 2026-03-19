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
package io.github.microcks.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.microcks.domain.Operation;

/**
 * This represented a domain event around AsyncAPI trigger command.
 * @author laurent
 */
public class AsyncAPITriggerCommand {

   private String serviceId;
   private Operation operation;
   private RequestSnapshot request;
   private ResponseSnapshot response;
   private long timestamp;

   @JsonCreator
   public AsyncAPITriggerCommand(@JsonProperty("serviceId") String serviceId,
         @JsonProperty("operation") Operation operation, @JsonProperty("request") RequestSnapshot request,
         @JsonProperty("resposne") ResponseSnapshot response, @JsonProperty("timestamp") long timestamp) {
      this.serviceId = serviceId;
      this.operation = operation;
      this.request = request;
      this.response = response;
      this.timestamp = System.currentTimeMillis();
   }

   public String getServiceId() {
      return serviceId;
   }

   public Operation getOperation() {
      return operation;
   }

   public RequestSnapshot getRequest() {
      return request;
   }

   public ResponseSnapshot getResponse() {
      return response;
   }

   public long getTimestamp() {
      return timestamp;
   }
}
