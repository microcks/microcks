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
package io.microcks.web;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import jakarta.servlet.http.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A read-only {@code HttpServletRequest} view over an incoming Jakarta REST request. There is no servlet container in
 * Microcks 2.0: this class only exists so that the mock processors, the scripting bindings and the parameter
 * constraints can keep reading a request through a well-known API. Only request line, headers, query parameters and
 * cookies are supported; body and session related methods are inherited no-op implementations.
 * @author laurent
 */
public class ContainerHttpServletRequest extends BasicHttpServletRequest {

   private final URI requestUri;
   private final Map<String, List<String>> headerMap;
   private final Map<String, String[]> parameterMap;
   private final String remoteAddr;
   private final int remotePort;
   private final List<Locale> locales;
   private final List<Cookie> cookies;

   private ContainerHttpServletRequest(String method, URI requestUri, Map<String, List<String>> headerMap,
         Map<String, String[]> parameterMap, List<Cookie> cookies, List<Locale> locales, String remoteAddr,
         int remotePort) {
      super(prefixOf(requestUri), method, requestUri.getRawPath(), requestUri.getRawQuery(), firstValues(parameterMap),
            headerMap);
      this.requestUri = requestUri;
      this.headerMap = headerMap;
      this.parameterMap = parameterMap;
      this.cookies = cookies;
      this.locales = locales;
      this.remoteAddr = remoteAddr;
      this.remotePort = remotePort;
   }

   /**
    * Build a request view from Jakarta REST context objects and the underlying Vert.x request.
    * @param method  The HTTP method name
    * @param uriInfo The URI information of the request
    * @param headers The headers of the request
    * @param request The Vert.x request, used to get the remote address (may be null)
    * @return A new request view
    */
   public static ContainerHttpServletRequest from(String method, UriInfo uriInfo, HttpHeaders headers,
         HttpServerRequest request) {
      SocketAddress remote = request != null ? request.remoteAddress() : null;
      return from(method, uriInfo, headers, remote != null ? remote.hostAddress() : null,
            remote != null ? remote.port() : 0);
   }

   /**
    * Build a request view from Jakarta REST context objects.
    * @param method     The HTTP method name
    * @param uriInfo    The URI information of the request
    * @param headers    The headers of the request
    * @param remoteAddr The address of the remote client (may be null)
    * @param remotePort The port of the remote client (or 0)
    * @return A new request view
    */
   public static ContainerHttpServletRequest from(String method, UriInfo uriInfo, HttpHeaders headers,
         String remoteAddr, int remotePort) {
      Map<String, List<String>> headerMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      for (Map.Entry<String, List<String>> header : headers.getRequestHeaders().entrySet()) {
         headerMap.put(header.getKey(), new ArrayList<>(header.getValue()));
      }
      Map<String, String[]> parameterMap = new LinkedHashMap<>();
      MultivaluedMap<String, String> query = uriInfo.getQueryParameters();
      for (Map.Entry<String, List<String>> param : query.entrySet()) {
         parameterMap.put(param.getKey(), param.getValue().toArray(new String[0]));
      }
      List<Cookie> cookies = new ArrayList<>();
      headers.getCookies().values().forEach(c -> cookies.add(new Cookie(c.getName(), c.getValue())));
      return new ContainerHttpServletRequest(method, uriInfo.getRequestUri(), headerMap, parameterMap, cookies,
            headers.getAcceptableLanguages(), remoteAddr, remotePort);
   }

   private static String prefixOf(URI uri) {
      StringBuilder prefix = new StringBuilder(uri.getScheme()).append("://").append(uri.getRawAuthority());
      return prefix.toString();
   }

   private static Map<String, String> firstValues(Map<String, String[]> parameterMap) {
      Map<String, String> result = new LinkedHashMap<>();
      parameterMap.forEach((k, v) -> result.put(k, v.length > 0 ? v[0] : ""));
      return result;
   }

   @Override
   public Enumeration<String> getHeaders(String name) {
      List<String> values = headerMap.get(name);
      return values != null ? Collections.enumeration(values) : Collections.emptyEnumeration();
   }

   @Override
   public Cookie[] getCookies() {
      return cookies.isEmpty() ? null : cookies.toArray(new Cookie[0]);
   }

   @Override
   public StringBuffer getRequestURL() {
      // Like servlet containers: no query string in the request URL.
      return new StringBuffer(prefixOf(requestUri)).append(requestUri.getRawPath());
   }

   @Override
   public String getContentType() {
      return getHeader("Content-Type");
   }

   @Override
   public String getParameter(String name) {
      String[] values = parameterMap.get(name);
      return values != null && values.length > 0 ? values[0] : null;
   }

   @Override
   public String[] getParameterValues(String name) {
      return parameterMap.get(name);
   }

   @Override
   public Map<String, String[]> getParameterMap() {
      return Collections.unmodifiableMap(parameterMap);
   }

   @Override
   public Enumeration<String> getParameterNames() {
      return Collections.enumeration(parameterMap.keySet());
   }

   @Override
   public String getScheme() {
      return requestUri.getScheme();
   }

   @Override
   public String getServerName() {
      return requestUri.getHost();
   }

   @Override
   public int getServerPort() {
      if (requestUri.getPort() != -1) {
         return requestUri.getPort();
      }
      return "https".equals(requestUri.getScheme()) ? 443 : 80;
   }

   @Override
   public boolean isSecure() {
      return "https".equals(requestUri.getScheme());
   }

   @Override
   public String getRemoteAddr() {
      return remoteAddr;
   }

   @Override
   public String getRemoteHost() {
      return remoteAddr;
   }

   @Override
   public int getRemotePort() {
      return remotePort;
   }

   @Override
   public String getProtocol() {
      return "HTTP/1.1";
   }

   @Override
   public Locale getLocale() {
      return locales.isEmpty() ? Locale.getDefault() : locales.get(0);
   }

   @Override
   public Enumeration<Locale> getLocales() {
      return Collections.enumeration(locales);
   }

   @Override
   public Enumeration<String> getAttributeNames() {
      return Collections.emptyEnumeration();
   }
}
