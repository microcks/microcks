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
package io.microcks.testsupport;

import io.microcks.web.BasicHttpServletRequest;

import jakarta.servlet.http.Cookie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal mutable replacement for Spring's {@code MockHttpServletRequest} used by the script engine tests.
 * @author laurent
 */
public class MockServletRequest extends BasicHttpServletRequest {

   private final Map<String, List<String>> requestHeaders;
   private String requestQueryString;
   private Cookie[] requestCookies = new Cookie[0];

   public MockServletRequest() {
      this(new HashMap<>());
   }

   private MockServletRequest(Map<String, List<String>> headers) {
      super("", "GET", "/", null, new HashMap<>(), headers);
      this.requestHeaders = headers;
   }

   public void addHeader(String name, String value) {
      requestHeaders.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
   }

   public void setQueryString(String queryString) {
      this.requestQueryString = queryString;
   }

   public void setCookies(Cookie... cookies) {
      this.requestCookies = cookies;
   }

   @Override
   public String getQueryString() {
      return requestQueryString;
   }

   @Override
   public Cookie[] getCookies() {
      return requestCookies;
   }
}
