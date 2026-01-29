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
 * Simple bean representing an event raised so that a callback can be triggered.
 * @author laurent
 */
public class CallbackTriggerEvent extends ApplicationEvent {

   private final String serviceId;
   private final Operation operation;
   private final String responseName;
   private final HttpServletRequestSnapshot request;

   private int step = 0;

   /**
    * Create a new callback trigger event
    * @param source       Source object for event
    * @param serviceId    Id of invoked service
    * @param operation    Operation invoked
    * @param responseName Name of response returned
    * @param request      Snapshot of incoming request
    */
   public CallbackTriggerEvent(Object source, String serviceId, Operation operation, String responseName,
         HttpServletRequestSnapshot request) {
      super(source);
      this.serviceId = serviceId;
      this.operation = operation;
      this.responseName = responseName;
      this.request = request;
   }

   /**
    * Create a new callback trigger event with an order
    * @param source       Source object for event
    * @param serviceId    Id of invoked service
    * @param operation    Operation invoked
    * @param responseName Name of response returned
    * @param request      Snapshot of incoming request
    * @param order        The order of this event in a callback events chain.
    */
   public CallbackTriggerEvent(Object source, String serviceId, Operation operation, String responseName,
         HttpServletRequestSnapshot request, int order) {
      super(source);
      this.serviceId = serviceId;
      this.operation = operation;
      this.responseName = responseName;
      this.request = request;
      this.step = order;
   }

   public String getServiceId() {
      return serviceId;
   }

   public Operation getOperation() {
      return operation;
   }

   public String getResponseName() {
      return responseName;
   }

   public HttpServletRequestSnapshot getRequest() {
      return request;
   }

   public int getStep() {
      return step;
   }

   public void setStep(int step) {
      this.step = step;
   }
}
