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

package io.microcks.domain;

import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.Document;
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

/**
 * Domain class representing a GenericResource created for simple CRUD mocking.
 * @author laurent
 */
@MongoEntity(collection = "genericResource")
public class GenericResource {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
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
