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
package io.microcks.util.http;

/**
 * An HTTP status code.
 * @param value The numeric code
 * @author laurent
 */
public record HttpStatusCode(int value) {

   /**
    * Tell if this status is a client (4xx) or server (5xx) error.
    * @return True if error status
    */
   public boolean isError() {
      return value >= 400 && value < 600;
   }

   @Override
   public String toString() {
      return String.valueOf(value);
   }
}
