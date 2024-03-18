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

import java.util.ArrayList;
import java.util.List;

/**
 * Domain object representing a Microservice operation / action invocation request.
 * @author laurent
 */
public class Request extends Message {

   @Id
   private String id;
   private String responseId;

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
}
