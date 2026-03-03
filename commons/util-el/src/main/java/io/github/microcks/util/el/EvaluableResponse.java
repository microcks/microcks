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
package io.github.microcks.util.el;

import java.util.Map;

/**
 * This is a simple bean wrapping response most common elements and adapted for evaluation within EL expressions.
 * @author laurent
 */
public class EvaluableResponse {

   private String body;
   private Map<String, String> headers;

   public EvaluableResponse(String body) {
      this.body = body;
   }

   public EvaluableResponse(String body, Map<String, String> headers) {
      this.body = body;
      this.headers = headers;
   }

   public String getBody() {
      return body;
   }

   public void setBody(String body) {
      this.body = body;
   }

   public Map<String, String> getHeaders() {
      return headers;
   }

   public void setHeaders(Map<String, String> headers) {
      this.headers = headers;
   }
}
