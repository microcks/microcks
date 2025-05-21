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
package io.github.microcks.web;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Basic implementation of HttpServletRequest that does nothing and just carries basic values.
 * @author laurent
 */
public class BasicHttpServletRequest implements HttpServletRequest {

   private final String urlPrefix;
   private final String method;
   private final String pathInfo;
   private final String queryString;
   private final Map<String, String> queryParameters;
   private final Map<String, List<String>> headers;

   public BasicHttpServletRequest(String urlPrefix, String method, String pathInfo, String queryString,
         Map<String, String> queryParameters, Map<String, List<String>> headers) {
      this.urlPrefix = urlPrefix;
      this.method = method;
      this.pathInfo = pathInfo;
      this.queryString = queryString;
      this.queryParameters = queryParameters;
      this.headers = headers;
   }

   @Override
   public String getAuthType() {
      return "";
   }

   @Override
   public Cookie[] getCookies() {
      return new Cookie[0];
   }

   @Override
   public long getDateHeader(String s) {
      return 0;
   }

   @Override
   public String getHeader(String s) {
      if (headers.containsKey(s)) {
         return headers.get(s).getFirst();
      }
      return null;
   }

   @Override
   public Enumeration<String> getHeaders(String s) {
      if (headers.containsKey(s)) {
         return Collections.enumeration(headers.get(s));
      }
      return null;
   }

   @Override
   public Enumeration<String> getHeaderNames() {
      return Collections.enumeration(headers.keySet());
   }

   @Override
   public int getIntHeader(String s) {
      return 0;
   }

   @Override
   public String getMethod() {
      return method;
   }

   @Override
   public String getPathInfo() {
      return pathInfo;
   }

   @Override
   public String getPathTranslated() {
      return pathInfo;
   }

   @Override
   public String getContextPath() {
      return "";
   }

   @Override
   public String getQueryString() {
      return queryString;
   }

   @Override
   public String getRemoteUser() {
      return "";
   }

   @Override
   public boolean isUserInRole(String s) {
      return false;
   }

   @Override
   public Principal getUserPrincipal() {
      return null;
   }

   @Override
   public String getRequestedSessionId() {
      return "";
   }

   @Override
   public String getRequestURI() {
      return pathInfo;
   }

   @Override
   public StringBuffer getRequestURL() {
      StringBuffer url = new StringBuffer(urlPrefix);
      url.append(pathInfo);
      if (queryString != null) {
         url.append("?").append(queryString);
      }
      return url;
   }

   @Override
   public String getServletPath() {
      return "";
   }

   @Override
   public HttpSession getSession(boolean b) {
      return null;
   }

   @Override
   public HttpSession getSession() {
      return null;
   }

   @Override
   public String changeSessionId() {
      return "";
   }

   @Override
   public boolean isRequestedSessionIdValid() {
      return false;
   }

   @Override
   public boolean isRequestedSessionIdFromCookie() {
      return false;
   }

   @Override
   public boolean isRequestedSessionIdFromURL() {
      return false;
   }

   @Override
   public boolean authenticate(HttpServletResponse httpServletResponse) throws IOException, ServletException {
      return false;
   }

   @Override
   public void login(String s, String s1) throws ServletException {
      // Our basic implementation does not support this method.
   }

   @Override
   public void logout() throws ServletException {
      // Our basic implementation does not support this method.
   }

   @Override
   public Collection<Part> getParts() throws IOException, ServletException {
      return List.of();
   }

   @Override
   public Part getPart(String s) throws IOException, ServletException {
      return null;
   }

   @Override
   public <T extends HttpUpgradeHandler> T upgrade(Class<T> aClass) throws IOException, ServletException {
      return null;
   }

   @Override
   public Object getAttribute(String s) {
      return null;
   }

   @Override
   public Enumeration<String> getAttributeNames() {
      return null;
   }

   @Override
   public String getCharacterEncoding() {
      return "";
   }

   @Override
   public void setCharacterEncoding(String s) throws UnsupportedEncodingException {
      // Our basic implementation does not support this method.
   }

   @Override
   public int getContentLength() {
      return 0;
   }

   @Override
   public long getContentLengthLong() {
      return 0;
   }

   @Override
   public String getContentType() {
      return "";
   }

   @Override
   public ServletInputStream getInputStream() throws IOException {
      return null;
   }

   @Override
   public String getParameter(String s) {
      return queryParameters.getOrDefault(s, null);
   }

   @Override
   public Enumeration<String> getParameterNames() {
      return Collections.enumeration(queryParameters.keySet());
   }

   @Override
   public String[] getParameterValues(String s) {
      return new String[0];
   }

   @Override
   public Map<String, String[]> getParameterMap() {
      return Map.of();
   }

   @Override
   public String getProtocol() {
      return "";
   }

   @Override
   public String getScheme() {
      return "";
   }

   @Override
   public String getServerName() {
      return "";
   }

   @Override
   public int getServerPort() {
      return 0;
   }

   @Override
   public BufferedReader getReader() throws IOException {
      return null;
   }

   @Override
   public String getRemoteAddr() {
      return "";
   }

   @Override
   public String getRemoteHost() {
      return "";
   }

   @Override
   public void setAttribute(String s, Object o) {
      // Our basic implementation does not support this method.
   }

   @Override
   public void removeAttribute(String s) {
      // Our basic implementation does not support this method.
   }

   @Override
   public Locale getLocale() {
      return null;
   }

   @Override
   public Enumeration<Locale> getLocales() {
      return null;
   }

   @Override
   public boolean isSecure() {
      return false;
   }

   @Override
   public RequestDispatcher getRequestDispatcher(String s) {
      return null;
   }

   @Override
   public int getRemotePort() {
      return 0;
   }

   @Override
   public String getLocalName() {
      return "";
   }

   @Override
   public String getLocalAddr() {
      return "";
   }

   @Override
   public int getLocalPort() {
      return 0;
   }

   @Override
   public ServletContext getServletContext() {
      return null;
   }

   @Override
   public AsyncContext startAsync() throws IllegalStateException {
      return null;
   }

   @Override
   public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse)
         throws IllegalStateException {
      return null;
   }

   @Override
   public boolean isAsyncStarted() {
      return false;
   }

   @Override
   public boolean isAsyncSupported() {
      return false;
   }

   @Override
   public AsyncContext getAsyncContext() {
      return null;
   }

   @Override
   public DispatcherType getDispatcherType() {
      return null;
   }

   @Override
   public String getRequestId() {
      return "";
   }

   @Override
   public String getProtocolRequestId() {
      return "";
   }

   @Override
   public ServletConnection getServletConnection() {
      return null;
   }
}
