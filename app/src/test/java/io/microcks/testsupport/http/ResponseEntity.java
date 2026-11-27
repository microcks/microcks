/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.testsupport.http;

/**
 * Response status, headers and body returned by the integration test HTTP client.
 * @param <T> The body type
 * @author laurent
 */
public class ResponseEntity<T> {

   private final HttpStatus status;
   private final HttpHeaders headers;
   private final T body;

   public ResponseEntity(HttpStatus status, HttpHeaders headers, T body) {
      this.status = status;
      this.headers = headers;
      this.body = body;
   }

   public HttpStatus getStatusCode() {
      return status;
   }

   public HttpHeaders getHeaders() {
      return headers;
   }

   public T getBody() {
      return body;
   }
}
