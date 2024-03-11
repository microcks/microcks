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

/**
 * Simple bean representing a request/response pair.
 * @author laurent
 */
public class RequestResponsePair extends Exchange {

   private Request request;
   private Response response;

   @JsonCreator
   public RequestResponsePair(@JsonProperty("request") Request request, @JsonProperty("response") Response response) {
      this.request = request;
      this.response = response;
   }

   public Request getRequest() {
      return request;
   }

   public void setRequest(Request request) {
      this.request = request;
   }

   public Response getResponse() {
      return response;
   }

   public void setResponse(Response response) {
      this.response = response;
   }
}
