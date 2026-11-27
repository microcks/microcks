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

import io.github.microcks.domain.ServiceView;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * This represents a domain event around ServiceView change.
 * @author laurent
 */
public class ServiceViewChangeEvent {

   private String serviceId;
   private ServiceView serviceView;
   private ChangeType changeType;
   private long timestamp;

   @JsonCreator
   public ServiceViewChangeEvent(@JsonProperty("serviceId") String serviceId,
         @JsonProperty("serviceView") ServiceView serviceView, @JsonProperty("changeType") ChangeType changeType,
         @JsonProperty("timestamp") long timestamp) {
      this.serviceId = serviceId;
      this.serviceView = serviceView;
      this.changeType = changeType;
      this.timestamp = timestamp;
   }

   public String getServiceId() {
      return serviceId;
   }

   public ServiceView getServiceView() {
      return serviceView;
   }

   public ChangeType getChangeType() {
      return changeType;
   }

   public long getTimestamp() {
      return timestamp;
   }
}
