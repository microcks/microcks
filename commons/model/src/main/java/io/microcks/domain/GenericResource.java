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

import org.bson.Document;
import org.springframework.data.annotation.Id;

/**
 * Domain class representing a GenericResource created for simple CRUD mocking.
 * @author laurent
 */
public class GenericResource {

   @Id
   private String id;
   private String serviceId;
   private Document payload;
   private boolean reference = false;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getServiceId() {
      return serviceId;
   }

   public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
   }

   public Document getPayload() {
      return payload;
   }

   public void setPayload(Document payload) {
      this.payload = payload;
   }

   public boolean isReference() {
      return reference;
   }

   public void setReference(boolean reference) {
      this.reference = reference;
   }
}
