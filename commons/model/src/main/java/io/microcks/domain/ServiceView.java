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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Aggregate bean for grouping a Service and its messages pairs.
 * @author laurent
 */
public class ServiceView {

   private Service service;
   private Map<String, List<? extends Exchange>> messagesMap;

   @JsonCreator
   public ServiceView(@JsonProperty("service") Service service,
         @JsonProperty("messagesMap") Map<String, List<? extends Exchange>> messagesMap) {
      this.service = service;
      this.messagesMap = messagesMap;
   }

   public Service getService() {
      return service;
   }

   public Map<String, List<? extends Exchange>> getMessagesMap() {
      return messagesMap;
   }
}
