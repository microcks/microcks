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

import org.springframework.context.ApplicationEvent;

/**
 * Event raised when a Service has been changed (CREATED, UPDATED or DELETED).
 * @author laurent
 */
public class ServiceChangeEvent extends ApplicationEvent {

   /** */
   private final String serviceId;
   /** */
   private final ChangeType changeType;

   /**
    * Creates a new {@code ServiceChangeEvent} with change type.
    * @param source     Source object for event
    * @param serviceId  Identifier of the updated Service
    * @param changeType Type of changes this event if representing
    */
   public ServiceChangeEvent(Object source, String serviceId, ChangeType changeType) {
      super(source);
      this.serviceId = serviceId;
      this.changeType = changeType;
   }

   public String getServiceId() {
      return serviceId;
   }

   public ChangeType getChangeType() {
      return changeType;
   }
}
