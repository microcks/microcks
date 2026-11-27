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
 * Minimal media type holder used by the integration test HTTP client.
 * @author laurent
 */
public record MediaType(String value) {

   public static final String APPLICATION_JSON_VALUE = "application/json";
   public static final String TEXT_PLAIN_VALUE = "text/plain";
   public static final String TEXT_EVENT_STREAM_VALUE = "text/event-stream";
   public static final String MULTIPART_FORM_DATA_VALUE = "multipart/form-data";

   public static final MediaType APPLICATION_JSON = new MediaType(APPLICATION_JSON_VALUE);
   public static final MediaType TEXT_PLAIN = new MediaType(TEXT_PLAIN_VALUE);
   public static final MediaType TEXT_EVENT_STREAM = new MediaType(TEXT_EVENT_STREAM_VALUE);
   public static final MediaType MULTIPART_FORM_DATA = new MediaType(MULTIPART_FORM_DATA_VALUE);

   @Override
   public String toString() {
      return value;
   }
}
