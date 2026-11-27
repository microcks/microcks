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
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * An ephemeral Service state that can be used to create stateful mocks.
 * @author laurent
 */
@Document
public class ServiceState {

   @Id
   private String id;
   private String serviceId;
   private String key;
   private String value;
   @Indexed(expireAfterSeconds = 0)
   private Date expireAt;


   /** Build a ServiceState. */
   public ServiceState() {
   }

   /**
    * Build a ServiceState with required information.
    * @param serviceId The unique identifier of Service this state relates to
    * @param key       The key a value will be stored for
    */
   public ServiceState(String serviceId, String key) {
      this.serviceId = serviceId;
      this.key = key;
   }

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

   public String getKey() {
      return key;
   }

   public void setKey(String key) {
      this.key = key;
   }

   public String getValue() {
      return value;
   }

   public void setValue(String value) {
      this.value = value;
   }

   public Date getExpireAt() {
      return expireAt;
   }

   public void setExpireAt(Date expireAt) {
      this.expireAt = expireAt;
   }
}
