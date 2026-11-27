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

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Case-insensitive multi-valued headers map used by the integration test HTTP client.
 * @author laurent
 */
public class HttpHeaders extends TreeMap<String, List<String>> {

   public HttpHeaders() {
      super(String.CASE_INSENSITIVE_ORDER);
   }

   public void set(String name, String value) {
      List<String> values = new ArrayList<>();
      values.add(value);
      put(name, values);
   }

   public void setContentType(MediaType mediaType) {
      set("Content-Type", mediaType.toString());
   }

   public void setAccept(List<MediaType> mediaTypes) {
      set("Accept", String.join(", ", mediaTypes.stream().map(MediaType::toString).toList()));
   }

   public String getFirst(String name) {
      List<String> values = get(name);
      return values == null || values.isEmpty() ? null : values.get(0);
   }

   public MediaType getContentType() {
      String value = getFirst("Content-Type");
      return value == null ? null : new MediaType(value);
   }
}
