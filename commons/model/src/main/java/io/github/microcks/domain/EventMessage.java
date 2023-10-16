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

/**
 * A simple event message published or sent in an asynchronous exchange.
 * @author laurent
 */
public class EventMessage extends Message {

   @Id
   private String id;
   private String mediaType;
   private String dispatchCriteria;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getMediaType() {
      return mediaType;
   }

   public void setMediaType(String mediaType) {
      this.mediaType = mediaType;
   }

   public String getDispatchCriteria() {
      return dispatchCriteria;
   }

   public void setDispatchCriteria(String dispatchCriteria) {
      this.dispatchCriteria = dispatchCriteria;
   }
}
