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
 * This is a simple bean wrapping request most common elements and adapted for evaluation within EL expressions.
 * @author laurent
 */
public class EvaluableRequest {

   private String body;
   private String[] path;
   private Map<String, String> params;
   private Map<String, String> headers;

   public EvaluableRequest(String body, String[] path) {
      this.body = body;
      this.path = path;
   }

   public String getBody() {
      return body;
   }

   public void setBody(String body) {
      this.body = body;
   }

   public String[] getPath() {
      return path;
   }

   public void setPath(String[] path) {
      this.path = path;
   }

   public Map<String, String> getParams() {
      return params;
   }

   public void setParams(Map<String, String> params) {
      this.params = params;
   }

   public Map<String, String> getHeaders() {
      return headers;
   }

   public void setHeaders(Map<String, String> headers) {
      this.headers = headers;
   }
}
