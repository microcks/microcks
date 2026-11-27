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

import java.util.ArrayList;
import java.util.List;

/**
 * Domain object representing a Microservice operation / action invocation request.
 * @author laurent
 */
@MongoEntity(collection = "request")
public class Request extends Message {

   @BsonId
   @BsonRepresentation(BsonType.OBJECT_ID)
   private String id;
   private String responseId;
   private String callbackName;

   private List<Parameter> queryParameters;

   public String getId() {
      return id;
   }

   public void setId(String id) {
      this.id = id;
   }

   public String getResponseId() {
      return responseId;
   }

   public void setResponseId(String responseId) {
      this.responseId = responseId;
   }

   public List<Parameter> getQueryParameters() {
      return queryParameters;
   }

   public void setQueryParameters(List<Parameter> queryParameters) {
      this.queryParameters = queryParameters;
   }

   public void addQueryParameter(Parameter parameter) {
      if (this.queryParameters == null) {
         this.queryParameters = new ArrayList<>();
      }
      queryParameters.add(parameter);
   }

   public String getCallbackName() {
      return callbackName;
   }

   public void setCallbackName(String callbackName) {
      this.callbackName = callbackName;
   }
}
