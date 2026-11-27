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
 * HTTP status holder used by the integration test HTTP client.
 * @author laurent
 */
public record HttpStatus(int value) {

   public static final HttpStatus OK = new HttpStatus(200);
   public static final HttpStatus NO_CONTENT = new HttpStatus(204);
   public static final HttpStatus NOT_FOUND = new HttpStatus(404);
}
