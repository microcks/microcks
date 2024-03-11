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
package io.github.microcks.minion.async;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;

import java.util.List;
import java.util.Objects;

/**
 * This is a wrapper bean that holds needed elements for async API operation mocking.
 * @author laurent
 */
public class AsyncMockDefinition {

   private Service ownerService;
   private Operation operation;
   private List<EventMessage> eventMessages;

   public AsyncMockDefinition(Service ownerService, Operation operation, List<EventMessage> eventMessages) {
      this.ownerService = ownerService;
      this.operation = operation;
      this.eventMessages = eventMessages;
   }

   public Service getOwnerService() {
      return ownerService;
   }

   public void setOwnerService(Service ownerService) {
      this.ownerService = ownerService;
   }

   public Operation getOperation() {
      return operation;
   }

   public void setOperation(Operation operation) {
      this.operation = operation;
   }

   public List<EventMessage> getEventMessages() {
      return eventMessages;
   }

   public void setEventMessages(List<EventMessage> eventMessages) {
      this.eventMessages = eventMessages;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o)
         return true;
      if (o == null || getClass() != o.getClass())
         return false;
      AsyncMockDefinition that = (AsyncMockDefinition) o;
      return ownerService.getId().equals(that.ownerService.getId())
            && operation.getName().equals(that.operation.getName());
   }

   @Override
   public int hashCode() {
      return Objects.hash(ownerService.getId(), operation.getName());
   }
}
