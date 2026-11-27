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

import io.github.microcks.domain.Operation;
import org.springframework.context.ApplicationEvent;

/**
 * @author laurent
 */
public class AsyncAPITriggerEvent extends ApplicationEvent {

   private final String serviceId;
   private final Operation operation;
   private final RequestSnapshot request;
   private final ResponseSnapshot response;

   /**
    * Create a new AsyncAPITriggerEvent.
    * @param source    Source object for event
    * @param serviceId Id of invoked service
    * @param operation Operation invoked
    * @param request   Snapshot of incoming request
    * @param response  Snapshot of response returned
    */
   public AsyncAPITriggerEvent(Object source, String serviceId, Operation operation, RequestSnapshot request,
         ResponseSnapshot response) {
      super(source);
      this.serviceId = serviceId;
      this.operation = operation;
      this.request = request;
      this.response = response;
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
}
