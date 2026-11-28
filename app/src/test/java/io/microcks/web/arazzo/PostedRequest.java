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
package io.microcks.web.arazzo;

import io.microcks.web.BasicHttpServletRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A POST request the mocks receive, as a servlet container sees it: its URL holds no query string, and its header names
 * are case insensitive.
 * @author SebastienDegodez
 */
final class PostedRequest extends BasicHttpServletRequest {

   private static final String URL = "http://localhost";

   private final String path;
   private final Map<String, List<String>> requestHeaders;

   /**
    * @param path        The path of the request
    * @param queryString The query string of the request, without its ?; null when it has none
    */
   PostedRequest(String path, String queryString) {
      this(path, queryString, new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
   }

   private PostedRequest(String path, String queryString, Map<String, List<String>> headers) {
      super(URL, "POST", path, queryString, Map.of(), headers);
      this.path = path;
      this.requestHeaders = headers;
   }

   PostedRequest withHeader(String name, String value) {
      requestHeaders.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
      return this;
   }

   @Override
   public StringBuffer getRequestURL() {
      return new StringBuffer(URL + path);
   }
}
