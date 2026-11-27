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
import org.bson.BsonType;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonRepresentation;

/**
 * Domain object representing a microservice operation / rest action invocation response. Holds information wether it's
 * a fault or not and on status code and mediaType to use for return. Responses are typically retrieved using
 * dispatchCriteria extracted from a corresponding request message.
 * @author laurent
 */
@MongoEntity(collection = "response")
public class Response extends Message {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   private String id;
   private String status;
   private String mediaType;
   private String dispatchCriteria;
   private String callbackName;
   private boolean isFault = false;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getStatus() {
      return status;
   }

   public void setStatus(String status) {
      this.status = status;
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

   public String getCallbackName() {
      return callbackName;
   }

   public void setCallbackName(String callbackName) {
      this.callbackName = callbackName;
   }

   public boolean isFault() {
      return isFault;
   }

   public void setFault(boolean isFault) {
      this.isFault = isFault;
   }
}
