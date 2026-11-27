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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Case-insensitive multi-valued HTTP headers, framework-neutral.
 * @author laurent
 */
public class HttpHeaders {

   public static final String AUTHORIZATION = "Authorization";
   public static final String CONTENT_TYPE = "Content-Type";
   public static final String TRANSFER_ENCODING = "Transfer-Encoding";
   public static final String CONTENT_LENGTH = "Content-Length";
   public static final String HOST = "Host";

   private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

   /** Append a value to the named header. */
   public void add(String name, String value) {
      headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
   }

   /** Replace all values of the named header by a single one. */
   public void set(String name, String value) {
      List<String> values = new ArrayList<>();
      values.add(value);
      headers.put(name, values);
   }

   /** Replace all values of the named header by the given ones. */
   public void put(String name, List<String> values) {
      headers.put(name, new ArrayList<>(values));
   }

   /** Replace the headers by all those of the given map. */
   public void putAll(Map<String, List<String>> other) {
      other.forEach(this::put);
   }

   /** @return True if the named header is present */
   public boolean containsHeader(String name) {
      return headers.containsKey(name);
   }

   /** @return A case-insensitive view of the headers as a multi-valued map */
   public Map<String, List<String>> asMultiValueMap() {
      return headers;
   }

   /** Set the Content-Type header. */
   public void setContentType(String contentType) {
      set(CONTENT_TYPE, contentType);
   }

   /** @return The first value of the named header or null */
   public String getFirst(String name) {
      List<String> values = headers.get(name);
      return values == null || values.isEmpty() ? null : values.get(0);
   }

   /** @return All values of the named header or null */
   public List<String> get(String name) {
      return headers.get(name);
   }

   /** @return The Content-Type header value or null */
   public String getContentType() {
      return getFirst(CONTENT_TYPE);
   }

   /** @return The Connection header values or null */
   public List<String> getConnection() {
      return get("Connection");
   }

   /** @return True if there are no headers */
   public boolean isEmpty() {
      return headers.isEmpty();
   }

   /** @return The header entries */
   public Set<Map.Entry<String, List<String>>> headerSet() {
      return headers.entrySet();
   }

   @Override
   public boolean equals(Object o) {
      return o instanceof HttpHeaders other && headers.equals(other.headers);
   }

   @Override
   public int hashCode() {
      return headers.hashCode();
   }

   @Override
   public String toString() {
      return headers.toString();
   }
}
